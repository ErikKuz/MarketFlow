package com.example.marketflow.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.marketflow.Order.*;
import com.example.marketflow.Repository.*;
import com.example.marketflow.exception.*;
import com.example.marketflow.marketplace.OrderWorkflowService;
import com.example.marketflow.messaging.MarketFlowEvent;
import com.example.marketflow.messaging.MarketFlowEventType;
import com.example.marketflow.messaging.RabbitMqNames;
import com.example.marketflow.messaging.outbox.OutboxService;
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
    private final OutboxService outboxService;
    private final OrderEventHistoryRepository orderEventHistoryRepository;

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
        outboxService.save(MarketFlowEvent.create(
                MarketFlowEventType.ORDER_CREATED,
                order.getId(),
                null,
                buyerId,
                null,
                total,
                null,
                OrderStatus.CREATED.name(),
                Instant.now()
        ), RabbitMqNames.ORDER_CREATED_EVENT);
        return order.getId();
    }

    @Transactional(readOnly = true)
    public OrderDetailsDto getOrderDetails(Long orderId, Long buyerId) {
        var order = orderRepository.findByIdAndBuyerId(orderId, buyerId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return OrderMapper.toDetailsDto(order, orderItemRepository.findAllByOrderId(orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderHistoryView> getOrderHistory(Long orderId, Long buyerId) {
        orderRepository.findByIdAndBuyerId(orderId, buyerId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        return orderEventHistoryRepository.findAllByOrderIdOrderByOccurredAtAscIdAsc(orderId)
                .stream()
                .map(OrderHistoryView::of)
                .toList();
    }

    @Transactional
    public void cancelOrder(Long orderId, Long buyerId) {
        var order = orderRepository.findForPayment(orderId, buyerId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() == OrderStatus.CANCELLED) return;
        boolean refundablePaidOrder = order.getPaymentStatus() == PaymentStatus.PAID
                && (order.getStatus() == OrderStatus.CONFIRMED
                    || order.getStatus() == OrderStatus.SELLERSSTARTWORK
                    || order.getStatus() == OrderStatus.SELLERSENDWORKANDSEND);
        if (refundablePaidOrder) {
            paymentService.refundOrder(orderId, buyerId);
            return;
        }
        if (order.getStatus() != OrderStatus.CREATED
                || (order.getPaymentStatus() != PaymentStatus.NOT_PAID
                    && order.getPaymentStatus() != PaymentStatus.FAILED)) {
            throw new InvalidOrderStateException("Only an unpaid order can be cancelled");
        }
        workflow.cancelled(order);
        OrderStatus previousStatus = order.getStatus();
        order.changeStatus(OrderStatus.CANCELLED);
        outboxService.save(MarketFlowEvent.create(
                MarketFlowEventType.ORDER_CANCELLED,
                order.getId(),
                null,
                buyerId,
                null,
                order.getTotalPrice(),
                previousStatus.name(),
                OrderStatus.CANCELLED.name(),
                Instant.now()
        ), RabbitMqNames.ORDER_CANCELLED_EVENT);
    }

    public record OrderHistoryView(
            UUID eventId,
            MarketFlowEventType eventType,
            String description,
            Long sellerOrderId,
            String previousStatus,
            String currentStatus,
            Instant occurredAt
    ) {
        static OrderHistoryView of(
                com.example.marketflow.messaging.history.OrderEventHistoryEntity history
        ) {
            return new OrderHistoryView(
                    history.getEventId(),
                    history.getEventType(),
                    description(history.getEventType()),
                    history.getSellerOrderId(),
                    history.getPreviousStatus(),
                    history.getCurrentStatus(),
                    history.getOccurredAt()
            );
        }

        private static String description(MarketFlowEventType type) {
            return switch (type) {
                case ORDER_CREATED -> "Заказ создан";
                case ORDER_PAID -> "Заказ оплачен";
                case ORDER_CANCELLED -> "Заказ отменён";
                case ORDER_COMPLETED -> "Заказ завершён";
                case ORDER_REFUNDED -> "Деньги за заказ возвращены";
                case SELLER_STARTED_WORK -> "Продавец начал обработку товара";
                case SELLER_SENT_PRODUCT -> "Продавец отправил товар";
                case USER_RECEIVED_PRODUCT -> "Покупатель получил товар";
                case SELLER_MONEY_PENDING -> "Деньги начислены продавцу в ожидающий баланс";
                case PLATFORM_COMMISSION_ADDED -> "Платформе начислена комиссия";
                case SELLER_MONEY_AVAILABLE -> "Деньги продавца стали доступны для вывода";
                case SELLER_WITHDRAWAL_COMPLETED -> "Продавец вывел деньги";
                case SELLER_MONEY_RETURNED -> "Начисление продавцу отменено";
                case PLATFORM_COMMISSION_RETURNED -> "Комиссия платформы возвращена";
            };
        }
    }
}
