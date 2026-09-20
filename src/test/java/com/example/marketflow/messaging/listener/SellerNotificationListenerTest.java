package com.example.marketflow.messaging.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.marketflow.Repository.NotificationRepository;
import com.example.marketflow.marketplace.SellerOrderRepository;
import com.example.marketflow.marketplace.SellerOrderEntity;
import com.example.marketflow.messaging.MarketFlowEvent;
import com.example.marketflow.messaging.MarketFlowEventType;
import com.example.marketflow.messaging.notification.NotificationEntity;

class SellerNotificationListenerTest {
    @Test
    void createsSellerNotification() {
        NotificationRepository repository = org.mockito.Mockito.mock(NotificationRepository.class);
        SellerOrderRepository sellerOrders = org.mockito.Mockito.mock(SellerOrderRepository.class);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        var listener = new SellerNotificationListener(
                repository, sellerOrders, Clock.fixed(now, ZoneOffset.UTC)
        );
        MarketFlowEvent event = MarketFlowEvent.create(
                MarketFlowEventType.SELLER_MONEY_AVAILABLE,
                42L, 51L, 7L, 9L, new BigDecimal("90.00"),
                "PENDINGWALLET", "MAINWALLET", now
        );

        listener.handle(event);

        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(repository).save(captor.capture());
        assertEquals(9L, captor.getValue().getUserId());
        assertEquals("SELLER", captor.getValue().getRecipientType());
        assertEquals("Деньги доступны для вывода", captor.getValue().getTitle());
        assertEquals(
                "Деньги доступны для вывода. Заказ №42, часть заказа №51, сумма 90.00",
                captor.getValue().getMessage()
        );
    }

    @Test
    void orderPaidUsesEachSellersOwnPartAndAmount() {
        NotificationRepository repository = org.mockito.Mockito.mock(NotificationRepository.class);
        SellerOrderRepository sellerOrders = org.mockito.Mockito.mock(SellerOrderRepository.class);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        var firstPart = new SellerOrderEntity(42L, 9L, new BigDecimal("1000.00"), now);
        var secondPart = new SellerOrderEntity(42L, 10L, new BigDecimal("2500.00"), now);
        ReflectionTestUtils.setField(firstPart, "id", 51L);
        ReflectionTestUtils.setField(secondPart, "id", 52L);
        when(sellerOrders.findAllByOrderIdOrderBySellerId(42L))
                .thenReturn(List.of(firstPart, secondPart));
        var listener = new SellerNotificationListener(
                repository, sellerOrders, Clock.fixed(now, ZoneOffset.UTC)
        );
        MarketFlowEvent event = MarketFlowEvent.create(
                MarketFlowEventType.ORDER_PAID,
                42L, null, 7L, null, new BigDecimal("3500.00"),
                "PROCESSING", "PAID", now
        );

        listener.handle(event);

        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(repository, times(2)).save(captor.capture());
        assertEquals(
                "Поступил оплаченный заказ. Заказ №42, ваша часть №51, сумма 1000.00",
                captor.getAllValues().get(0).getMessage()
        );
        assertEquals(
                "Поступил оплаченный заказ. Заказ №42, ваша часть №52, сумма 2500.00",
                captor.getAllValues().get(1).getMessage()
        );
    }
}
