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
import java.util.List;

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
import com.example.marketflow.Repository.OrderRepository;
import com.example.marketflow.Repository.ProductRepository;
import com.example.marketflow.cart.CartItemEntity;
import com.example.marketflow.exception.InsufficientStockException;
import com.example.marketflow.exception.NoSelectedCartItemsException;
import com.example.marketflow.products.ProductEntity;
import com.example.marketflow.payment.PaymentStatus;

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
    private PaymentService paymentService;

    @Mock
    private com.example.marketflow.marketplace.OrderWorkflowService workflow;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrderDecreasesStockAndDeletesSelectedCartItems() {
        Long buyerId = 7L;
        Long productId = 11L;
        Long orderId = 21L;

        CartItemEntity cartItem = mock(CartItemEntity.class);
        ProductEntity product = mock(ProductEntity.class);
        OrderEntity savedOrder = mock(OrderEntity.class);

        when(cartItem.getProductId()).thenReturn(productId);
        when(cartItem.getQuantity()).thenReturn(2);
        when(cartItemRepository.findAllByBuyerIdAndSelectedTrue(buyerId))
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
        when(productRepository.decreaseStock(productId, 2)).thenReturn(1);

        when(orderRepository.save(any(OrderEntity.class))).thenReturn(savedOrder);
        when(savedOrder.getId()).thenReturn(orderId);

        Long result = orderService.createOrder(buyerId);

        assertEquals(orderId, result);
        verify(productRepository).decreaseStock(productId, 2);
        verify(orderItemRepository).saveAll(anyList());
        verify(workflow).initialize(org.mockito.ArgumentMatchers.eq(savedOrder), anyList());
        verify(cartItemRepository).deleteSelectedByBuyerId(buyerId);
    }

    @Test
    void createOrderDoesNotSaveOrderWhenAtomicStockUpdateFails() {
        Long buyerId = 7L;
        Long productId = 11L;

        CartItemEntity cartItem = mock(CartItemEntity.class);
        ProductEntity product = mock(ProductEntity.class);

        when(cartItem.getProductId()).thenReturn(productId);
        when(cartItem.getQuantity()).thenReturn(2);
        when(cartItemRepository.findAllByBuyerIdAndSelectedTrue(buyerId))
                .thenReturn(List.of(cartItem));

        when(product.getId()).thenReturn(productId);
        when(product.getPrice()).thenReturn(new BigDecimal("25.00"));
        when(product.getQuantity()).thenReturn(10);
        when(product.getActive()).thenReturn(true);
        when(productRepository.findAllById(List.of(productId)))
                .thenReturn(List.of(product));
        when(productRepository.decreaseStock(productId, 2)).thenReturn(0);

        assertThrows(
                InsufficientStockException.class,
                () -> orderService.createOrder(buyerId)
        );

        verifyNoInteractions(orderRepository, orderItemRepository);
        verify(cartItemRepository, never()).deleteSelectedByBuyerId(buyerId);
    }

    @Test
    void createOrderRejectsEmptySelectionBeforeAnyWrite() {
        when(cartItemRepository.findAllByBuyerIdAndSelectedTrue(7L))
                .thenReturn(List.of());

        assertThrows(
                NoSelectedCartItemsException.class,
                () -> orderService.createOrder(7L)
        );

        verifyNoInteractions(productRepository, orderRepository, orderItemRepository);
        verify(cartItemRepository, never()).deleteSelectedByBuyerId(7L);
    }

    @Test
    void cancelUnpaidOrderRestoresStockWithoutRefund() {
        OrderEntity order = mock(OrderEntity.class);
        OrderItemEntity orderItem = mock(OrderItemEntity.class);
        when(order.getId()).thenReturn(42L);
        when(order.getStatus()).thenReturn(OrderStatus.CREATED);
        when(order.getPaymentStatus()).thenReturn(PaymentStatus.NOT_PAID);
        when(orderRepository.findForPayment(42L, 7L))
                .thenReturn(java.util.Optional.of(order));
        when(orderItemRepository.findAllByOrderId(42L))
                .thenReturn(List.of(orderItem));
        when(orderItem.getProductId()).thenReturn(11L);
        when(orderItem.getQuantity()).thenReturn(2);
        when(productRepository.increaseStock(11L, 2)).thenReturn(1);

        orderService.cancelOrder(42L, 7L);

        verify(productRepository).increaseStock(11L, 2);
        verify(order).changeStatus(OrderStatus.CANCELLED);
        verifyNoInteractions(paymentService);
        verify(workflow).cancelled(order, 7L, "BUYER_CANCELLED");
    }

    @Test
    void cancelPaidOrderRefundsMoneyAndRestoresStock() {
        OrderEntity order = mock(OrderEntity.class);
        OrderItemEntity orderItem = mock(OrderItemEntity.class);
        when(order.getId()).thenReturn(42L);
        when(order.getStatus()).thenReturn(OrderStatus.CONFIRMED);
        when(order.getPaymentStatus()).thenReturn(PaymentStatus.PAID);
        when(orderRepository.findForPayment(42L, 7L))
                .thenReturn(java.util.Optional.of(order));
        when(orderItemRepository.findAllByOrderId(42L))
                .thenReturn(List.of(orderItem));
        when(orderItem.getProductId()).thenReturn(11L);
        when(orderItem.getQuantity()).thenReturn(2);
        when(productRepository.increaseStock(11L, 2)).thenReturn(1);

        orderService.cancelOrder(42L, 7L);

        verify(paymentService).refundOrder(order);
        verify(productRepository).increaseStock(11L, 2);
        verify(order).changeStatus(OrderStatus.CANCELLED);
    }

    @Test
    void repeatedCancellationDoesNotRestoreStockTwice() {
        OrderEntity order = mock(OrderEntity.class);
        when(order.getStatus()).thenReturn(OrderStatus.CANCELLED);
        when(orderRepository.findForPayment(42L, 7L))
                .thenReturn(java.util.Optional.of(order));

        orderService.cancelOrder(42L, 7L);

        verifyNoInteractions(paymentService, productRepository, orderItemRepository);
        verify(order, never()).changeStatus(any(OrderStatus.class));
    }
}
