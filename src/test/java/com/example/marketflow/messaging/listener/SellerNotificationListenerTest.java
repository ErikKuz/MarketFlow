package com.example.marketflow.messaging.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.marketflow.Repository.NotificationRepository;
import com.example.marketflow.marketplace.SellerOrderRepository;
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
    }
}
