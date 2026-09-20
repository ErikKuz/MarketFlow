package com.example.marketflow.MVCTHymeleafcontroller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.example.marketflow.Order.OrderDetailsDto;
import com.example.marketflow.service.OrderService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;


@Controller
@RequestMapping("/account/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public String createOrder(HttpSession session) {
        Long buyerId = (Long) session.getAttribute("userId");

        if (buyerId == null) {
            return "redirect:/login";
        }

        Long orderId = orderService.createOrder(buyerId);

        return "redirect:/account/orders/" + orderId;
    }

    @GetMapping("/{orderId}")
    public String showOrder(
            @PathVariable Long orderId,
            HttpSession session,
            Model model
    ) {
        Long buyerId =
                (Long) session.getAttribute("userId");

        if (buyerId == null) {
            return "redirect:/login";
        }

        OrderDetailsDto order =
                orderService.getOrderDetails(orderId, buyerId);

        model.addAttribute("order", order);
        model.addAttribute("history", orderService.getOrderHistory(orderId, buyerId));

        return "orders/showOrder";
    }

    @PostMapping("/{orderId}/cancel")
    public String cancelOrder(
            @PathVariable Long orderId,
            HttpSession session
    ) {
        Long buyerId = (Long) session.getAttribute("userId");

        if (buyerId == null) {
            return "redirect:/login";
        }

        orderService.cancelOrder(orderId, buyerId);
        return "redirect:/account/orders/" + orderId;
    }
}
