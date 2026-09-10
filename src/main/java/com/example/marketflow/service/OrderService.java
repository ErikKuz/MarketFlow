package com.example.marketflow.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.marketflow.Order.*;
import com.example.marketflow.Repository.*;
import com.example.marketflow.exception.*;
import com.example.marketflow.marketplace.OrderWorkflowService;
import com.example.marketflow.payment.PaymentStatus;
import com.example.marketflow.products.ProductEntity;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderWorkflowService workflow;
    private final PaymentService paymentService;

    @Transactional
    public Long createOrder(Long buyerId) {
        var selected = cartItemRepository.findSelectedForCheckout(buyerId);
        if (selected.isEmpty()) throw new NoSelectedCartItemsException();
        var products = productRepository.findAllById(selected.stream().map(i -> i.getProductId()).distinct().toList())
                .stream().collect(Collectors.toMap(ProductEntity::getId, Function.identity()));
        BigDecimal total = BigDecimal.ZERO;
        for (var item : selected) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new InvalidQuantityException(item.getQuantity());
            }
            var product = products.get(item.getProductId());
            if (product == null) throw new ProductNotFoundException(item.getProductId());
            if (!Boolean.TRUE.equals(product.getActive())) throw new ProductUnavailableException();
            if (product.getQuantity() == null || product.getQuantity() < item.getQuantity()) {
                throw new NotEnoughProductQuantityException();
            }
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        OrderEntity order = orderRepository.save(new OrderEntity(buyerId, OrderStatus.CREATED, total));
        var items = new ArrayList<OrderItemEntity>();
        for (var cartItem : selected) {
            var product = products.get(cartItem.getProductId());
            items.add(new OrderItemEntity(order.getId(), product.getId(), product.getSellerId(),
                    product.getName(), product.getPrice(), cartItem.getQuantity(), product.getUrl()));
        }
        orderItemRepository.saveAll(items);
        workflow.initialize(order, items);
        // Удаляем только этот снимок корзины. Остаток повторно проверяется и уменьшается во время оплаты.
        cartItemRepository.deleteAllInBatch(selected);
        return order.getId();
    }

    @Transactional(readOnly = true)
    public OrderDetailsDto getOrderDetails(Long orderId, Long buyerId) {
        var order = orderRepository.findByIdAndBuyerId(orderId, buyerId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return OrderMapper.toDetailsDto(order, orderItemRepository.findAllByOrderId(orderId));
    }

    @Transactional
    public void cancelOrder(Long orderId, Long buyerId) {
        var order = orderRepository.findForPayment(orderId, buyerId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() == OrderStatus.CANCELLED) return;
        if (order.getStatus() == OrderStatus.CONFIRMED
                && order.getPaymentStatus() == PaymentStatus.PAID) {
            paymentService.refundOrder(orderId, buyerId);
            return;
        }
        if (order.getStatus() != OrderStatus.CREATED
                || (order.getPaymentStatus() != PaymentStatus.NOT_PAID
                    && order.getPaymentStatus() != PaymentStatus.FAILED)) {
            throw new InvalidOrderStateException("Only an unpaid order can be cancelled");
        }
        workflow.cancelled(order);
        order.changeStatus(OrderStatus.CANCELLED);
    }
}
