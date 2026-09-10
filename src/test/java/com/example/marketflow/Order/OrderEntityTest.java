package com.example.marketflow.Order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.example.marketflow.exception.InvalidOrderStateException;
import com.example.marketflow.payment.PaymentStatus;

class OrderEntityTest {

    @Test
    void shouldAllowNormalPaymentAndOrderStatusFlow() {
        OrderEntity order = order();

        order.changePaymentStatus(PaymentStatus.PROCESSING);
        order.changePaymentStatus(PaymentStatus.PAID);
        order.changeStatus(OrderStatus.CONFIRMED);

        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
    }

    @Test
    void shouldRejectPaidToProcessingTransition() {
        OrderEntity order = order();
        order.changePaymentStatus(PaymentStatus.PROCESSING);
        order.changePaymentStatus(PaymentStatus.PAID);

        assertThrows(
                InvalidOrderStateException.class,
                () -> order.changePaymentStatus(PaymentStatus.PROCESSING)
        );
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
    }

    @Test
    void shouldRejectTransitionFromCompletedBackToCreated() {
        OrderEntity order = order();
        order.changeStatus(OrderStatus.CONFIRMED);
        order.changeStatus(OrderStatus.SELLERSSTARTWORK);
        order.changeStatus(OrderStatus.SELLERSENDWORKANDSEND);
        order.changeStatus(OrderStatus.COMPLETED);

        assertThrows(
                InvalidOrderStateException.class,
                () -> order.changeStatus(OrderStatus.CREATED)
        );
        assertEquals(OrderStatus.COMPLETED, order.getStatus());
    }

    private OrderEntity order() {
        return new OrderEntity(
                7L,
                OrderStatus.CREATED,
                new BigDecimal("100.00")
        );
    }
}
