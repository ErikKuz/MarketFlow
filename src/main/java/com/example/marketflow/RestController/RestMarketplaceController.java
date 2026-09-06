package com.example.marketflow.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import com.example.marketflow.marketplace.*;
import com.example.marketflow.products.ProductDto;
import static com.example.marketflow.marketplace.MarketplaceViews.*;
import lombok.RequiredArgsConstructor;

@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
public class RestMarketplaceController {
    private final OrderWorkflowService workflow;
    private final ReturnService returns;
    private final FinanceService finance;
    private final AdministrationService administration;
    private final AnalyticsService analytics;
    private final CatalogService catalog;

    private Long actor(HttpSession session) { return (Long) session.getAttribute("userId"); }

    @GetMapping("/orders")
    public PageView<OrderSummary> orders(HttpSession session, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return workflow.buyerOrders(actor(session), page, size);
    }
    @GetMapping("/orders/{id}/fulfillments")
    public List<SellerOrderView> parts(@PathVariable Long id, HttpSession session) { return workflow.buyerParts(actor(session), id); }
    @GetMapping("/orders/{id}/history")
    public List<AuditView> history(@PathVariable Long id, HttpSession session) { return workflow.buyerHistory(actor(session), id); }
    @PostMapping("/orders/{id}/fulfillments/{partId}/receive") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void receive(@PathVariable Long id, @PathVariable Long partId, HttpSession session) {
        workflow.confirmDelivery(actor(session), id, partId);
    }
    @GetMapping("/seller/orders")
    public PageView<SellerOrderView> sellerOrders(HttpSession session, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) { return workflow.sellerOrders(actor(session), page, size); }
    @GetMapping("/seller/orders/{id}")
    public SellerOrderDetails sellerOrder(@PathVariable Long id, HttpSession session) { return workflow.sellerDetails(actor(session), id); }
    @PostMapping("/seller/orders/{id}/accept") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@PathVariable Long id, HttpSession session) { workflow.sellerTransition(actor(session), id, FulfillmentStatus.ACCEPTED); }
    @PostMapping("/seller/orders/{id}/packing") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void pack(@PathVariable Long id, HttpSession session) { workflow.sellerTransition(actor(session), id, FulfillmentStatus.PACKING); }
    @PostMapping("/seller/orders/{id}/ship") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ship(@PathVariable Long id, HttpSession session) { workflow.sellerTransition(actor(session), id, FulfillmentStatus.SHIPPED); }

    @PostMapping("/orders/{id}/returns") @ResponseStatus(HttpStatus.CREATED)
    public ReturnView requestReturn(@PathVariable Long id, @Valid @RequestBody MarketplaceRequests.Reason request, HttpSession session) {
        return returns.request(actor(session), id, request.reason());
    }
    @GetMapping("/orders/{id}/returns")
    public ReturnView getReturn(@PathVariable Long id, HttpSession session) { return returns.buyerRequest(actor(session), id); }
    @GetMapping("/owner/returns")
    public PageView<ReturnView> ownerReturns(HttpSession session, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) { return returns.pending(actor(session), page, size); }
    @PostMapping("/owner/returns/{id}/decision") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decideReturn(@PathVariable Long id, @Valid @RequestBody MarketplaceRequests.ReturnDecision r, HttpSession session) {
        returns.decide(actor(session), id, r.approved(), r.restock(), r.reason());
    }

    @GetMapping("/finance/wallet")
    public WalletView wallet(HttpSession session) { return finance.wallet(actor(session)); }
    @GetMapping("/finance/transactions")
    public PageView<PaymentEntry> transactions(HttpSession session, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) { return finance.history(actor(session), page, size); }
    @GetMapping("/seller/withdrawals")
    public PageView<WithdrawalView> withdrawals(HttpSession session, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) { return finance.sellerWithdrawals(actor(session), page, size); }
    @PostMapping("/seller/withdrawals") @ResponseStatus(HttpStatus.CREATED)
    public WithdrawalView withdraw(@Valid @RequestBody MarketplaceRequests.Withdrawal r, HttpSession session) {
        return finance.requestWithdrawal(actor(session), r.cardId(), r.amount(), r.idempotencyKey());
    }
    @GetMapping("/owner/withdrawals")
    public PageView<WithdrawalView> ownerWithdrawals(HttpSession session, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) { return finance.allWithdrawals(actor(session), page, size); }
    @PostMapping("/owner/withdrawals/{id}/decision") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decideWithdrawal(@PathVariable Long id, @Valid @RequestBody MarketplaceRequests.Decision r, HttpSession session) {
        finance.decideWithdrawal(actor(session), id, r.approved(), r.reason());
    }

    @GetMapping("/account/seller-application")
    public SellerApplicationView myApplication(HttpSession session) { return administration.myApplication(actor(session)); }
    @PostMapping("/account/seller-application") @ResponseStatus(HttpStatus.CREATED)
    public SellerApplicationView apply(HttpSession session) { return administration.apply(actor(session)); }
    @GetMapping("/moderation/seller-applications")
    public PageView<SellerApplicationView> applications(HttpSession session, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) { return administration.applications(actor(session), page, size); }
    @PostMapping("/moderation/seller-applications/{id}/decision") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decideSeller(@PathVariable Long id, @Valid @RequestBody MarketplaceRequests.Decision r, HttpSession session) {
        administration.decideSeller(actor(session), id, r.approved(), r.reason());
    }
    @PostMapping("/moderation/seller-applications/{id}/block") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void blockSeller(@PathVariable Long id, @Valid @RequestBody MarketplaceRequests.Block r, HttpSession session) {
        administration.blockSeller(actor(session), id, r.blocked(), r.reason());
    }
    @PostMapping("/moderation/products/{id}/visibility") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void visibility(@PathVariable Long id, @Valid @RequestBody MarketplaceRequests.Visibility r, HttpSession session) {
        administration.hideProduct(actor(session), id, r.hidden(), r.reason());
    }
    @GetMapping("/moderation/products")
    public PageView<ModeratedProduct> moderationProducts(HttpSession session, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) { return administration.moderationProducts(actor(session), page, size); }
    @GetMapping("/owner/commission")
    public BigDecimal commission(HttpSession session) { return administration.commission(actor(session)); }
    @PutMapping("/owner/commission") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void commission(@Valid @RequestBody MarketplaceRequests.Commission r, HttpSession session) {
        administration.changeCommission(actor(session), r.rate());
    }
    @GetMapping("/owner/users")
    public PageView<UserView> users(HttpSession session, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) { return administration.users(actor(session), page, size); }
    @PostMapping("/owner/staff") @ResponseStatus(HttpStatus.CREATED)
    public UserView staff(@Valid @RequestBody MarketplaceRequests.Staff r, HttpSession session) {
        return administration.createStaff(actor(session), r);
    }
    @PostMapping("/owner/users/{id}/roles") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void roles(@PathVariable Long id, @Valid @RequestBody MarketplaceRequests.RoleChange r, HttpSession session) {
        administration.changeRole(actor(session), id, r.role(), r.granted());
    }
    @PostMapping("/owner/users/{id}/block") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void blockUser(@PathVariable Long id, @Valid @RequestBody MarketplaceRequests.Block r, HttpSession session) {
        administration.blockUser(actor(session), id, r.blocked(), r.reason());
    }
    @GetMapping("/moderation/audit")
    public PageView<AuditView> audit(HttpSession session, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) { return administration.audit(actor(session), page, size); }

    @GetMapping("/analytics/report")
    public Report report(@RequestParam Instant from, @RequestParam Instant to, HttpSession session) {
        return analytics.report(actor(session), from, to);
    }
    @GetMapping("/products/search")
    public PageView<ProductDto> search(@RequestParam(required=false) String q,
            @RequestParam(required=false) BigDecimal minPrice, @RequestParam(required=false) BigDecimal maxPrice,
            @RequestParam(required=false) Long sellerId, @RequestParam(defaultValue="newest") String sort,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return catalog.search(q, minPrice, maxPrice, sellerId, sort, page, size);
    }
}
