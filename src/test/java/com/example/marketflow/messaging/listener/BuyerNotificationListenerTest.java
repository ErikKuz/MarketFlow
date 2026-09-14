package com.example.marketflow.messaging.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.marketflow.Repository.NotificationRepository;
import com.example.marketflow.messaging.MarketFlowEvent;
import com.example.marketflow.messaging.MarketFlowEventType;
import com.example.marketflow.messaging.notification.NotificationEntity;

class BuyerNotificationListenerTest {
    @Test
    void createsBuyerNotification() {
        NotificationRepository repository = org.mockito.Mockito.mock(NotificationRepository.class);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        var listener = new BuyerNotificationListener(repository, Clock.fixed(now, ZoneOffset.UTC));
        MarketFlowEvent event = MarketFlowEvent.create(
                MarketFlowEventType.ORDER_PAID,
                42L, null, 7L, null, null, "PROCESSING", "PAID", now
        );

        listener.handle(event);

        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(repository).save(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals("BUYER", captor.getValue().getRecipientType());
        assertEquals("Заказ оплачен", captor.getValue().getTitle());
    }
}
