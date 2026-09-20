package com.example.marketflow.RestController;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.marketflow.Order.CreatedOrderDto;
import com.example.marketflow.Order.OrderDetailsDto;
import com.example.marketflow.exception.AuthenticationRequiredException;
import com.example.marketflow.exception.InvalidOrderIdException;
import com.example.marketflow.service.OrderService;
import com.example.marketflow.service.OrderService.OrderHistoryView;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class RestOrderController {

    private final OrderService orderService;

    private Long requireBuyerId(HttpSession session) {
        Long buyerId = (Long) session.getAttribute("userId");

        if (buyerId == null) {
            throw new AuthenticationRequiredException();
        }

        return buyerId;
    }

    private void validateOrderId(Long orderId) {
        if (orderId == null || orderId < 1) {
            throw new InvalidOrderIdException(orderId);
        }
    }

    @PostMapping
    public ResponseEntity<CreatedOrderDto> createOrder(HttpSession session) {
        Long buyerId = requireBuyerId(session);
        Long orderId = orderService.createOrder(buyerId);

        return ResponseEntity
                .created(URI.create("/api/v1/orders/" + orderId))
                .body(new CreatedOrderDto(orderId));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailsDto> getOrder(
            @PathVariable Long orderId,
            HttpSession session
    ) {
        validateOrderId(orderId);
        Long buyerId = requireBuyerId(session);
        return ResponseEntity.ok(orderService.getOrderDetails(orderId, buyerId));
    }

    @GetMapping("/{orderId}/history")
    public ResponseEntity<List<OrderHistoryView>> getOrderHistory(
            @PathVariable Long orderId,
            HttpSession session
    ) {
        validateOrderId(orderId);
        Long buyerId = requireBuyerId(session);
        return ResponseEntity.ok(orderService.getOrderHistory(orderId, buyerId));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancelOrder(
            @PathVariable Long orderId,
            HttpSession session
    ) {
        validateOrderId(orderId);
        Long buyerId = requireBuyerId(session);
        orderService.cancelOrder(orderId, buyerId);
        return ResponseEntity.noContent().build();
    }
}
