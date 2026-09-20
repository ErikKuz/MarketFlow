package com.example.marketflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.marketflow.Order.OrderEntity;
import com.example.marketflow.Order.OrderItemEntity;
import com.example.marketflow.Order.OrderStatus;
import com.example.marketflow.Repository.CartItemRepository;
import com.example.marketflow.Repository.OrderItemRepository;
import com.example.marketflow.Repository.OrderEventHistoryRepository;
import com.example.marketflow.Repository.OrderRepository;
import com.example.marketflow.Repository.ProductRepository;
import com.example.marketflow.cart.CartItemEntity;
import com.example.marketflow.exception.NotEnoughProductQuantityException;
import com.example.marketflow.exception.InvalidOrderStateException;
import com.example.marketflow.exception.NoSelectedCartItemsException;
import com.example.marketflow.products.ProductEntity;
import com.example.marketflow.payment.PaymentStatus;
import com.example.marketflow.messaging.outbox.OutboxService;
import com.example.marketflow.messaging.MarketFlowEvent;
import com.example.marketflow.messaging.MarketFlowEventType;
import com.example.marketflow.messaging.history.OrderEventHistoryEntity;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private com.example.marketflow.marketplace.OrderWorkflowService workflow;

    @Mock
    private PaymentService paymentService;

    @Mock
    private OutboxService outboxService;

    @Mock
    private OrderEventHistoryRepository orderEventHistoryRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void returnsOwnedOrderHistoryInRepositoryOrder() {
        UUID eventId = UUID.fromString("8b679b54-37f9-4d9a-8f5f-50e3559ed38f");
        Instant occurredAt = Instant.parse("2026-09-14T10:05:00Z");
        OrderEntity order = new OrderEntity(7L, OrderStatus.CREATED, money("100.00"));
        OrderEventHistoryEntity history = new OrderEventHistoryEntity(
                new MarketFlowEvent(
                        eventId,
                        MarketFlowEventType.ORDER_PAID,
                        42L,
                        null,
                        7L,
                        null,
                        money("100.00"),
                        "CREATED",
                        "CONFIRMED",
                        occurredAt
                ),
                Instant.parse("2026-09-14T10:05:01Z")
        );
        when(orderRepository.findByIdAndBuyerId(42L, 7L)).thenReturn(Optional.of(order));
        when(orderEventHistoryRepository.findAllByOrderIdOrderByOccurredAtAscIdAsc(42L))
                .thenReturn(List.of(history));

        var result = orderService.getOrderHistory(42L, 7L);

        assertEquals(1, result.size());
        assertEquals(eventId, result.getFirst().eventId());
        assertEquals(MarketFlowEventType.ORDER_PAID, result.getFirst().eventType());
        assertEquals("Заказ оплачен", result.getFirst().description());
        assertEquals("CREATED", result.getFirst().previousStatus());
        assertEquals("CONFIRMED", result.getFirst().currentStatus());
        assertEquals(occurredAt, result.getFirst().occurredAt());
    }

    @Test
    void doesNotExposeHistoryOfAnotherBuyerOrder() {
        when(orderRepository.findByIdAndBuyerId(42L, 7L)).thenReturn(Optional.empty());

        assertThrows(
                com.example.marketflow.exception.OrderNotFoundException.class,
                () -> orderService.getOrderHistory(42L, 7L)
        );

        verifyNoInteractions(orderEventHistoryRepository);
    }

    @Test
    void createOrderSnapshotsPricesAndDeletesOnlySelectedItemsWithoutReservingStock() {
        Long buyerId = 7L;
        Long productId = 11L;
        Long orderId = 21L;

        CartItemEntity cartItem = mock(CartItemEntity.class);
        ProductEntity product = mock(ProductEntity.class);
        OrderEntity savedOrder = mock(OrderEntity.class);

        when(cartItem.getProductId()).thenReturn(productId);
        when(cartItem.getQuantity()).thenReturn(2);
        when(cartItemRepository.findSelectedForCheckout(buyerId))
                .thenReturn(List.of(cartItem));

        when(product.getId()).thenReturn(productId);
        when(product.getSellerId()).thenReturn(5L);
        when(product.getName()).thenReturn("Тестовый товар");
        when(product.getPrice()).thenReturn(new BigDecimal("25.00"));
        when(product.getQuantity()).thenReturn(10);
        when(product.getActive()).thenReturn(true);
        when(product.getUrl()).thenReturn("/images/product.jpg");
        when(productRepository.findAllById(List.of(productId)))
                .thenReturn(List.of(product));

        when(orderRepository.save(any(OrderEntity.class))).thenReturn(savedOrder);
        when(savedOrder.getId()).thenReturn(orderId);

        Long result = orderService.createOrder(buyerId);

        assertEquals(orderId, result);
        verify(productRepository, never()).decreaseStock(any(), any());
        verify(orderItemRepository).saveAll(anyList());
        verify(workflow).initialize(org.mockito.ArgumentMatchers.eq(savedOrder), anyList());
        verify(cartItemRepository).deleteAllInBatch(List.of(cartItem));
        verify(outboxService).save(any(), org.mockito.ArgumentMatchers.eq("order.created"));
    }

    @Test
    void createOrderDoesNotSaveOrderWhenStockIsInsufficient() {
        Long buyerId = 7L;
        Long productId = 11L;

        CartItemEntity cartItem = mock(CartItemEntity.class);
        ProductEntity product = mock(ProductEntity.class);

        when(cartItem.getProductId()).thenReturn(productId);
        when(cartItem.getQuantity()).thenReturn(2);
        when(cartItemRepository.findSelectedForCheckout(buyerId))
                .thenReturn(List.of(cartItem));

        when(product.getId()).thenReturn(productId);
        when(product.getQuantity()).thenReturn(1);
        when(product.getActive()).thenReturn(true);
        when(productRepository.findAllById(List.of(productId)))
                .thenReturn(List.of(product));

        assertThrows(
                NotEnoughProductQuantityException.class,
                () -> orderService.createOrder(buyerId)
        );

        verifyNoInteractions(orderRepository, orderItemRepository);
        verify(cartItemRepository, never()).deleteAllInBatch(anyList());
    }

    @Test
    void createOrderRejectsEmptySelectionBeforeAnyWrite() {
        when(cartItemRepository.findSelectedForCheckout(7L))
                .thenReturn(List.of());

        assertThrows(
                NoSelectedCartItemsException.class,
                () -> orderService.createOrder(7L)
        );

        verifyNoInteractions(productRepository, orderRepository, orderItemRepository);
        verify(cartItemRepository, never()).deleteAllInBatch(anyList());
    }

    @Test
    void cancelUnpaidOrderDoesNotTouchCardOrStock() {
        OrderEntity order = new OrderEntity(7L, OrderStatus.CREATED, new BigDecimal("100.00"));
        when(orderRepository.findForPayment(42L, 7L)).thenReturn(java.util.Optional.of(order));
        orderService.cancelOrder(42L, 7L);
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        verify(workflow).cancelled(order);
        verify(outboxService).save(any(), org.mockito.ArgumentMatchers.eq("order.cancelled"));
        verifyNoInteractions(productRepository, orderItemRepository);
    }

    @Test
    void cancelPaidConfirmedOrderDelegatesToRefund() {
        OrderEntity order = new OrderEntity(7L, OrderStatus.CREATED, new BigDecimal("100.00"));
        order.changePaymentStatus(PaymentStatus.PROCESSING);
        order.changePaymentStatus(PaymentStatus.PAID);
        order.changeStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findForPayment(42L, 7L)).thenReturn(java.util.Optional.of(order));
        orderService.cancelOrder(42L, 7L);
        verify(paymentService).refundOrder(42L, 7L);
        verifyNoInteractions(productRepository, orderItemRepository, workflow);
    }

    @Test
    void repeatedCancellationDoesNotRestoreStockTwice() {
        OrderEntity order = mock(OrderEntity.class);
        when(order.getStatus()).thenReturn(OrderStatus.CANCELLED);
        when(orderRepository.findForPayment(42L, 7L))
                .thenReturn(java.util.Optional.of(order));

        orderService.cancelOrder(42L, 7L);

        verifyNoInteractions(productRepository, orderItemRepository);
        verify(order, never()).changeStatus(any(OrderStatus.class));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
