package com.example.marketflow.RestController;

import java.util.List;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import com.example.marketflow.marketplace.*;
import com.example.marketflow.products.ProductDto;
import static com.example.marketflow.marketplace.MarketplaceViews.*;
import lombok.RequiredArgsConstructor;

@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor
public class RestMarketplaceController {
    private final OrderWorkflowService workflow;
    private Long actor(HttpSession session) { return (Long) session.getAttribute("userId"); }

    @GetMapping("/orders")
    public PageView<OrderSummary> orders(HttpSession session, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) {
        return workflow.buyerOrders(actor(session), page, size);
    }
    @GetMapping("/orders/{id}/fulfillments")
    public List<SellerOrderView> parts(@PathVariable Long id, HttpSession session) { return workflow.buyerParts(actor(session), id); }
    @PostMapping("/orders/{id}/fulfillments/{partId}/receive") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void receive(@PathVariable Long id, @PathVariable Long partId, HttpSession session) {
        workflow.confirmDelivery(actor(session), id, partId);
    }
    @GetMapping("/seller/orders")
    public PageView<SellerOrderView> sellerOrders(HttpSession session, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) { return workflow.SOR(actor(session), page, size); }
    @GetMapping("/seller/orders/{id}")
    public SellerOrderDetails sellerOrder(@PathVariable Long id, HttpSession session) { return workflow.sellerDetails(actor(session), id); }
    @PostMapping("/seller/orders/{id}/process") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void process(@PathVariable Long id, HttpSession session) {
        workflow.sellerTransition(actor(session), id, OBSERFFORSENDBYSELLERPRODUCTSTATUS.PROCESSING);
    }
    @PostMapping("/seller/orders/{id}/ship") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ship(@PathVariable Long id, HttpSession session) {
        workflow.sellerTransition(actor(session), id, OBSERFFORSENDBYSELLERPRODUCTSTATUS.SELLERSENDPRODUCT);
    }

}
