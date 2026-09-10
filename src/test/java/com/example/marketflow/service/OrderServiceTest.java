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
import com.example.marketflow.exception.NotEnoughProductQuantityException;
import com.example.marketflow.exception.InvalidOrderStateException;
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
    private com.example.marketflow.marketplace.OrderWorkflowService workflow;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private OrderService orderService;

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
}
