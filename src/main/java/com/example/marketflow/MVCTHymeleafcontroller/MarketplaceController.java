package com.example.marketflow.MVCTHymeleafcontroller;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;
import com.example.marketflow.marketplace.*;
import com.example.marketflow.service.OrderService;
import com.example.marketflow.service.PaymentCardService;
import lombok.RequiredArgsConstructor;

@Controller @RequiredArgsConstructor
public class MarketplaceController {
    private final OrderWorkflowService workflow;
    private final ReturnService returns;
    private final FinanceService finance;
    private final AdministrationService administration;
    private final AnalyticsService analytics;
    private final CatalogService catalog;
    private final OrderService orders;
    private final PaymentCardService cards;
    private final MarketplaceAccess access;
    private final java.time.Clock clock;

    private Long actor(HttpSession session) { return (Long) session.getAttribute("userId"); }
    private boolean role(Authentication auth, String role) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    @GetMapping("/workspace")
    public String home(HttpSession session, Authentication auth, Model model) {
        access.require(actor(session));
        model.addAttribute("buyer", role(auth, "BUYER"));
        model.addAttribute("seller", role(auth, "SELLER"));
        model.addAttribute("moderator", role(auth, "SELLER_MODERATOR") || role(auth, "OWNER"));
        model.addAttribute("analyst", role(auth, "ANALYST") || role(auth, "OWNER"));
        model.addAttribute("owner", role(auth, "OWNER"));
        return "workspace/home";
    }
    @GetMapping("/account/orders")
    public String orders(HttpSession session, Model model, @RequestParam(defaultValue="0") int page) {
        model.addAttribute("orders", workflow.buyerOrders(actor(session), page, 20));
        return "workspace/orders";
    }
    @GetMapping("/account/orders/{id}/workflow")
    public String tracking(@PathVariable Long id, HttpSession session, Model model) {
        var summary = workflow.buyerSummary(actor(session), id);
        model.addAttribute("summary", summary);
        model.addAttribute("canReturn", summary.returnDeadline() != null && clock.instant().isBefore(summary.returnDeadline())
                && summary.paymentStatus() == com.example.marketflow.payment.PaymentStatus.PAID);
        model.addAttribute("order", orders.getOrderDetails(id, actor(session)));
        model.addAttribute("parts", workflow.buyerParts(actor(session), id));
        model.addAttribute("history", workflow.buyerHistory(actor(session), id));
        model.addAttribute("returnRequest", returns.buyerRequest(actor(session), id));
        return "workspace/tracking";
    }
    @PostMapping("/account/orders/{id}/fulfillments/{partId}/receive")
    public String receive(@PathVariable Long id, @PathVariable Long partId, HttpSession session) {
        workflow.confirmDelivery(actor(session), id, partId);
        return "redirect:/account/orders/" + id + "/workflow";
    }
    @PostMapping("/account/orders/{id}/returns")
    public String requestReturn(@PathVariable Long id, @Valid @ModelAttribute MarketplaceRequests.Reason r, HttpSession session) {
        returns.request(actor(session), id, r.reason());
        return "redirect:/account/orders/" + id + "/workflow";
    }
    @GetMapping("/workspace/seller/orders")
    public String sellerOrders(HttpSession session, Model model, @RequestParam(defaultValue="0") int page) {
        model.addAttribute("orders", workflow.sellerOrders(actor(session), page, 20));
        return "workspace/seller-orders";
    }
    @GetMapping("/workspace/seller/orders/{id}")
    public String sellerOrder(@PathVariable Long id, HttpSession session, Model model) {
        model.addAttribute("details", workflow.sellerDetails(actor(session), id));
        return "workspace/seller-order";
    }
    @PostMapping("/workspace/seller/orders/{id}/{action}")
    public String sellerAction(@PathVariable Long id, @PathVariable String action, HttpSession session) {
        var next = switch (action) {
            case "accept" -> FulfillmentStatus.ACCEPTED;
            case "packing" -> FulfillmentStatus.PACKING;
            case "ship" -> FulfillmentStatus.SHIPPED;
            default -> throw MarketplaceException.missing("Action not found");
        };
        workflow.sellerTransition(actor(session), id, next);
        return "redirect:/workspace/seller/orders/" + id;
    }
    @GetMapping("/workspace/finance")
    public String finance(HttpSession session, Authentication auth, Model model, @RequestParam(defaultValue="0") int page) {
        model.addAttribute("transactions", finance.history(actor(session), page, 20));
        boolean seller = role(auth, "SELLER");
        model.addAttribute("seller", seller);
        if (seller || role(auth, "OWNER")) model.addAttribute("wallet", finance.wallet(actor(session)));
        if (seller) {
            model.addAttribute("withdrawals", finance.sellerWithdrawals(actor(session), page, 20));
            model.addAttribute("cards", cards.getUserPaymentCards(actor(session)));
            model.addAttribute("requestKey", UUID.randomUUID().toString());
        }
        return "workspace/finance";
    }
    @PostMapping("/workspace/seller/withdrawals")
    public String withdraw(@Valid @ModelAttribute MarketplaceRequests.Withdrawal r, HttpSession session) {
        finance.requestWithdrawal(actor(session), r.cardId(), r.amount(), r.idempotencyKey());
        return "redirect:/workspace/finance";
    }
    @GetMapping("/workspace/seller-application")
    public String application(HttpSession session, Model model) {
        model.addAttribute("sellerApplication", administration.myApplication(actor(session)));
        return "workspace/application";
    }
    @PostMapping("/workspace/seller-application")
    public String apply(HttpSession session) {
        administration.apply(actor(session));
        return "redirect:/workspace/seller-application";
    }
    @GetMapping("/workspace/moderation")
    public String moderation(HttpSession session, Model model, @RequestParam(defaultValue="0") int page) {
        model.addAttribute("applications", administration.applications(actor(session), page, 20));
        model.addAttribute("products", administration.moderationProducts(actor(session), page, 20));
        model.addAttribute("audit", administration.audit(actor(session), page, 20));
        return "workspace/moderation";
    }
    @PostMapping("/workspace/moderation/applications/{id}/decision")
    public String sellerDecision(@PathVariable Long id, @Valid @ModelAttribute MarketplaceRequests.Decision r, HttpSession session) {
        administration.decideSeller(actor(session), id, r.approved(), r.reason());
        return "redirect:/workspace/moderation";
    }
    @PostMapping("/workspace/moderation/applications/{id}/block")
    public String sellerBlock(@PathVariable Long id, @Valid @ModelAttribute MarketplaceRequests.Block r, HttpSession session) {
        administration.blockSeller(actor(session), id, r.blocked(), r.reason());
        return "redirect:/workspace/moderation";
    }
    @PostMapping("/workspace/moderation/products/{id}/visibility")
    public String visibility(@PathVariable Long id, @Valid @ModelAttribute MarketplaceRequests.Visibility r, HttpSession session) {
        administration.hideProduct(actor(session), id, r.hidden(), r.reason());
        return "redirect:/workspace/moderation";
    }
    @GetMapping("/workspace/owner")
    public String owner(HttpSession session, Model model, @RequestParam(defaultValue="0") int page) {
        Long id = actor(session);
        model.addAttribute("commission", administration.commission(id));
        model.addAttribute("wallet", finance.wallet(id));
        model.addAttribute("withdrawals", finance.allWithdrawals(id, page, 20));
        model.addAttribute("returns", returns.pending(id, page, 20));
        model.addAttribute("users", administration.users(id, page, 20));
        model.addAttribute("audit", administration.audit(id, page, 20));
        return "workspace/owner";
    }
    @PostMapping("/workspace/owner/commission")
    public String commission(@Valid @ModelAttribute MarketplaceRequests.Commission r, HttpSession session) {
        administration.changeCommission(actor(session), r.rate()); return "redirect:/workspace/owner";
    }
    @PostMapping("/workspace/owner/withdrawals/{id}/decision")
    public String payoutDecision(@PathVariable Long id, @Valid @ModelAttribute MarketplaceRequests.Decision r, HttpSession session) {
        finance.decideWithdrawal(actor(session), id, r.approved(), r.reason()); return "redirect:/workspace/owner";
    }
    @PostMapping("/workspace/owner/returns/{id}/decision")
    public String returnDecision(@PathVariable Long id, @Valid @ModelAttribute MarketplaceRequests.ReturnDecision r, HttpSession session) {
        returns.decide(actor(session), id, r.approved(), r.restock(), r.reason()); return "redirect:/workspace/owner";
    }
    @PostMapping("/workspace/owner/staff")
    public String staff(@Valid @ModelAttribute MarketplaceRequests.Staff r, HttpSession session) {
        administration.createStaff(actor(session), r); return "redirect:/workspace/owner";
    }
    @PostMapping("/workspace/owner/users/{id}/roles")
    public String roles(@PathVariable Long id, @Valid @ModelAttribute MarketplaceRequests.RoleChange r, HttpSession session) {
        administration.changeRole(actor(session), id, r.role(), r.granted()); return "redirect:/workspace/owner";
    }
    @PostMapping("/workspace/owner/users/{id}/block")
    public String userBlock(@PathVariable Long id, @Valid @ModelAttribute MarketplaceRequests.Block r, HttpSession session) {
        administration.blockUser(actor(session), id, r.blocked(), r.reason()); return "redirect:/workspace/owner";
    }
    @GetMapping("/workspace/analytics")
    public String analytics(HttpSession session, Model model,
            @RequestParam(required=false) LocalDate from, @RequestParam(required=false) LocalDate to) {
        var start = from == null ? LocalDate.now(clock).minusDays(30) : from;
        var end = to == null ? LocalDate.now(clock).plusDays(1) : to;
        model.addAttribute("from", start); model.addAttribute("to", end);
        model.addAttribute("report", analytics.report(actor(session), start.atStartOfDay().toInstant(ZoneOffset.UTC),
                end.atStartOfDay().toInstant(ZoneOffset.UTC)));
        return "workspace/analytics";
    }
    @GetMapping("/catalog")
    public String catalog(Model model, @RequestParam(required=false) String q,
            @RequestParam(required=false) BigDecimal minPrice, @RequestParam(required=false) BigDecimal maxPrice,
            @RequestParam(required=false) Long sellerId, @RequestParam(defaultValue="newest") String sort,
            @RequestParam(defaultValue="0") int page) {
        model.addAttribute("products", catalog.search(q, minPrice, maxPrice, sellerId, sort, page, 20));
        model.addAttribute("q", q); model.addAttribute("minPrice", minPrice);
        model.addAttribute("maxPrice", maxPrice); model.addAttribute("sellerId", sellerId); model.addAttribute("sort", sort);
        return "workspace/catalog";
    }
}
