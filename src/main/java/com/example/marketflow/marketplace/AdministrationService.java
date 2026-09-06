package com.example.marketflow.marketplace;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.Sort;
import com.example.marketflow.Repository.*;
import com.example.marketflow.User.*;
import com.example.marketflow.payment.WalletAccountEntity;
import com.example.marketflow.userRoles.*;
import static com.example.marketflow.marketplace.MarketplaceViews.*;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class AdministrationService {
    private final UserRepository users;
    private final userRoleRepository roles;
    private final SellerApplicationRepository applications;
    private final WalletAccountRepository wallets;
    private final ProductRepository products;
    private final PlatformSettingsRepository settings;
    private final AuditEventRepository audit;
    private final MarketplaceAccess access;
    private final OrderWorkflowService workflow;
    private final PasswordEncoder encoder;
    private final Clock clock;

    @Transactional
    public SellerApplicationView apply(Long userId) {
        access.require(userId, "BUYER");
        users.findLocked(userId).orElseThrow();
        if (roles.existsById(new UserRoleId(userId, (short) 2)))
            throw MarketplaceException.conflict("Account is already a seller");
        var application = applications.findByUserId(userId).orElse(null);
        if (application == null) application = applications.save(new SellerApplicationEntity(userId, clock.instant()));
        else if (application.getStatus() == SellerApplicationEntity.Status.PENDING) return SellerApplicationView.of(application);
        else application.resubmit(clock.instant());
        workflow.record(userId, null, userId, "SELLER_APPLICATION", null, "PENDING", null);
        return SellerApplicationView.of(application);
    }

    @Transactional(readOnly = true)
    public SellerApplicationView myApplication(Long userId) {
        access.require(userId);
        return applications.findByUserId(userId).map(SellerApplicationView::of).orElse(null);
    }

    @Transactional(readOnly = true)
    public PageView<SellerApplicationView> applications(Long actor, int page, int size) {
        access.require(actor, "SELLER_MODERATOR", "OWNER");
        return PageView.of(applications.findAllByOrderByCreatedAtDescIdDesc(MarketplaceAccess.page(page, size)).map(SellerApplicationView::of));
    }

    @Transactional
    public void decideSeller(Long actor, Long id, boolean approved, String reason) {
        access.require(actor, "SELLER_MODERATOR", "OWNER");
        var a = applications.lockById(id).orElseThrow(() -> MarketplaceException.missing("Application not found"));
        if (a.getStatus() != SellerApplicationEntity.Status.PENDING)
            throw MarketplaceException.conflict("Only pending applications can be decided");
        var user = users.findById(a.getUserId()).orElseThrow();
        if (user.getStatus() != UserStatus.ACTIVE) throw MarketplaceException.conflict("Account is not active");
        var status = approved ? SellerApplicationEntity.Status.ACTIVE : SellerApplicationEntity.Status.REJECTED;
        a.decide(status, reason, clock.instant());
        if (approved) {
            roles.save(new UserRolesEntity(a.getUserId(), (short) 2));
            if (!wallets.existsByUserId(a.getUserId())) wallets.save(new WalletAccountEntity(a.getUserId()));
        }
        workflow.record(actor, null, a.getUserId(), "SELLER_DECIDED", "PENDING", status.name(), reason);
    }

    @Transactional
    public void blockSeller(Long actor, Long id, boolean blocked, String reason) {
        access.require(actor, "SELLER_MODERATOR", "OWNER");
        var a = applications.lockById(id).orElseThrow(() -> MarketplaceException.missing("Application not found"));
        if (a.getStatus() != SellerApplicationEntity.Status.ACTIVE && a.getStatus() != SellerApplicationEntity.Status.BLOCKED)
            throw MarketplaceException.conflict("Seller has not been approved");
        var previous = a.getStatus();
        a.decide(blocked ? SellerApplicationEntity.Status.BLOCKED : SellerApplicationEntity.Status.ACTIVE, reason, clock.instant());
        if (blocked) {
            roles.deleteById(new UserRoleId(a.getUserId(), (short) 2));
            products.findAllBySellerId(a.getUserId()).forEach(p -> p.setHidden(true));
        } else roles.save(new UserRolesEntity(a.getUserId(), (short) 2));
        workflow.record(actor, null, a.getUserId(), "SELLER_BLOCK_CHANGED", previous.name(), a.getStatus().name(), reason);
    }

    @Transactional
    public void hideProduct(Long actor, Long productId, boolean hidden, String reason) {
        access.require(actor, "SELLER_MODERATOR", "OWNER");
        var p = products.findById(productId).orElseThrow(() -> MarketplaceException.missing("Product not found"));
        if (!hidden && !roles.existsById(new UserRoleId(p.getSellerId(), (short) 2)))
            throw MarketplaceException.conflict("Seller is not active");
        boolean previous = p.isHidden();
        p.setHidden(hidden);
        workflow.record(actor, null, p.getSellerId(), "PRODUCT_VISIBILITY", Boolean.toString(previous),
                Boolean.toString(hidden), "Product " + productId + ": " + reason);
    }

    @Transactional(readOnly = true)
    public PageView<ModeratedProduct> moderationProducts(Long actor, int page, int size) {
        access.require(actor, "SELLER_MODERATOR", "OWNER");
        MarketplaceAccess.page(page, size);
        return PageView.of(products.findAll(org.springframework.data.domain.PageRequest.of(page, size, Sort.by("id").descending()))
                .map(p -> new ModeratedProduct(p.getId(), p.getSellerId(), p.getName(), p.isHidden(), p.getActive())));
    }

    @Transactional(readOnly = true)
    public BigDecimal commission(Long actor) {
        access.require(actor, "OWNER");
        return settings.findById(1L).map(PlatformSettingsEntity::getCommissionRate).orElse(new BigDecimal("0.10"));
    }

    @Transactional
    public void changeCommission(Long actor, BigDecimal rate) {
        access.require(actor, "OWNER");
        if (rate == null || rate.signum() < 0 || rate.compareTo(new BigDecimal("0.99")) > 0 || rate.scale() > 4)
            throw MarketplaceException.conflict("Commission must be between 0 and 0.99 with at most 4 decimal places");
        var s = settings.lockSettings().orElseThrow(() -> MarketplaceException.conflict("Platform settings not initialized"));
        var previous = s.getCommissionRate();
        s.changeCommission(rate);
        workflow.record(actor, null, null, "COMMISSION_CHANGED", previous.toPlainString(), rate.toPlainString(), "Applies to new orders only");
    }

    @Transactional(readOnly = true)
    public PageView<UserView> users(Long actor, int page, int size) {
        access.require(actor, "OWNER");
        var pageable = MarketplaceAccess.page(page, size).getSort().isUnsorted()
                ? org.springframework.data.domain.PageRequest.of(page, size, Sort.by("id").descending())
                : MarketplaceAccess.page(page, size);
        return PageView.of(users.findAll(pageable).map(u -> UserView.of(u, roles.findRoleNamesByUserId(u.getId()))));
    }

    @Transactional
    public UserView createStaff(Long actor, MarketplaceRequests.Staff request) {
        access.require(actor, "OWNER");
        short role = staffRole(request.role());
        var email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) throw MarketplaceException.conflict("Email already registered");
        var u = users.save(new UserEntity(email, encoder.encode(request.password()), request.displayName().trim()));
        roles.save(new UserRolesEntity(u.getId(), role));
        workflow.record(actor, null, null, "STAFF_CREATED", null, request.role(), "User " + u.getId());
        return UserView.of(u, List.of(request.role()));
    }

    @Transactional
    public void changeRole(Long actor, Long userId, String role, boolean granted) {
        access.require(actor, "OWNER");
        short roleId = staffRole(role);
        users.findLocked(userId).orElseThrow(() -> MarketplaceException.missing("User not found"));
        if (roles.existsById(new UserRoleId(userId, (short) 5)))
            throw MarketplaceException.conflict("Owner roles cannot be changed");
        if (granted) roles.save(new UserRolesEntity(userId, roleId));
        else roles.deleteById(new UserRoleId(userId, roleId));
        workflow.record(actor, null, null, "ROLE_CHANGED", null, role, "User " + userId + ", granted=" + granted);
    }

    @Transactional
    public void blockUser(Long actor, Long userId, boolean blocked, String reason) {
        access.require(actor, "OWNER");
        var u = users.findLocked(userId).orElseThrow(() -> MarketplaceException.missing("User not found"));
        if (roles.existsById(new UserRoleId(userId, (short) 5))) throw MarketplaceException.conflict("Owner cannot be blocked");
        var previous = u.getStatus();
        u.changeStatus(blocked ? UserStatus.BLOCKED : UserStatus.ACTIVE);
        if (blocked) products.findAllBySellerId(userId).forEach(p -> p.setHidden(true));
        workflow.record(actor, null, null, "USER_STATUS_CHANGED", previous.name(), u.getStatus().name(), "User " + userId + ": " + reason);
    }

    @Transactional(readOnly = true)
    public PageView<AuditView> audit(Long actor, int page, int size) {
        access.require(actor, "OWNER", "SELLER_MODERATOR");
        var pageable = MarketplaceAccess.page(page, size);
        boolean owner = roles.existsById(new UserRoleId(actor, (short) 5));
        return PageView.of((owner ? audit.findAllByOrderByCreatedAtDescIdDesc(pageable)
                : audit.findAllByActorIdOrderByCreatedAtDescIdDesc(actor, pageable)).map(AuditView::of));
    }

    private short staffRole(String role) {
        return switch (role) {
            case "SELLER_MODERATOR" -> 3;
            case "ANALYST" -> 4;
            default -> throw MarketplaceException.conflict("Only moderator and analyst roles can be managed here");
        };
    }

    @Transactional
    public void initializePlatform(String email, String password) {
        if (!settings.existsById(1L)) settings.saveAndFlush(new PlatformSettingsEntity(new BigDecimal("0.10")));
        settings.lockSettings().orElseThrow();
        if (email == null || email.isBlank()) return;
        if (roles.existsByRoleId((short) 5)) return;
        if (password == null || password.length() < 12) throw new IllegalStateException("MARKETFLOW_OWNER_PASSWORD must contain at least 12 characters");
        var normalized = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(normalized))
            throw new IllegalStateException("Bootstrap owner email is already registered; use an unused email");
        var owner = users.save(new UserEntity(normalized, encoder.encode(password), "MarketFlow Owner"));
        roles.save(new UserRolesEntity(owner.getId(), (short) 5));
        wallets.save(new WalletAccountEntity(owner.getId()));
        workflow.record(owner.getId(), null, null, "OWNER_INITIALIZED", null, "OWNER", null);
    }
}
