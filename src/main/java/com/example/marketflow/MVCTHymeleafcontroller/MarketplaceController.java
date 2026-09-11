package com.example.marketflow.MVCTHymeleafcontroller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.example.marketflow.marketplace.MarketplaceException;
import com.example.marketflow.marketplace.OBSERFFORSENDBYSELLERPRODUCTSTATUS;
import com.example.marketflow.marketplace.OrderWorkflowService;
import com.example.marketflow.service.OrderService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller @RequiredArgsConstructor
public class MarketplaceController {
    private final OrderWorkflowService workflow;
    private final OrderService OrderService;
    private Long actor(HttpSession session) { return (Long) session.getAttribute("userId"); }
    @GetMapping("/account/orders")
    public String orders(HttpSession session, Model model, @RequestParam(defaultValue="0") int page) {
        model.addAttribute("orders", workflow.buyerOrders(actor(session), page, 20));
        return "workspace/orders";
    }
    @GetMapping("/account/orders/{id}/workflow")
    public String tracking(@PathVariable Long id, HttpSession session, Model model) {
        var summary = workflow.buyerSummary(actor(session), id);
        model.addAttribute("summary", summary);
        model.addAttribute("order", OrderService.getOrderDetails(id, actor(session)));
        model.addAttribute("parts", workflow.buyerParts(actor(session), id));
        return "workspace/tracking";
    }
    @PostMapping("/account/orders/{id}/fulfillments/{partId}/receive")
    public String receive(@PathVariable Long id, @PathVariable Long partId, HttpSession session) {
        workflow.ConfirmThatUSERGETPRODUCTBySellerID(actor(session), id, partId);
        return "redirect:/account/orders/" + id + "/workflow";
    }
    @GetMapping("/workspace/seller/orders")
    public String sellerOrders(HttpSession session, Model model, @RequestParam(defaultValue="0") int page) {
        model.addAttribute("orders", workflow.SOR(actor(session), page, 20));
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
            case "process" -> OBSERFFORSENDBYSELLERPRODUCTSTATUS.PROCESSING;
            case "ship" -> OBSERFFORSENDBYSELLERPRODUCTSTATUS.SELLERSENDPRODUCT;
            default -> throw MarketplaceException.missing("Action not found");
        };
        workflow.sellerTransition(actor(session), id, next);
        return "redirect:/workspace/seller/orders/" + id;
    }
}
