package com.example.marketflow.messaging.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.marketflow.Repository.OrderEventHistoryRepository;
import com.example.marketflow.messaging.MarketFlowEvent;
import com.example.marketflow.messaging.MarketFlowEventType;
import com.example.marketflow.messaging.history.OrderEventHistoryEntity;

class OrderHistoryListenerTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void savesEventOnce() {
        OrderEventHistoryRepository repository = org.mockito.Mockito.mock(OrderEventHistoryRepository.class);
        OrderHistoryListener listener = new OrderHistoryListener(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        MarketFlowEvent event = event();

        listener.handle(event);

        ArgumentCaptor<OrderEventHistoryEntity> captor = ArgumentCaptor.forClass(OrderEventHistoryEntity.class);
        verify(repository).save(captor.capture());
        assertEquals(event.eventId(), captor.getValue().getEventId());

        when(repository.existsByEventId(event.eventId())).thenReturn(true);
        listener.handle(event);
        verify(repository).save(any());
    }

    @Test
    void duplicateEventIsIgnored() {
        OrderEventHistoryRepository repository = org.mockito.Mockito.mock(OrderEventHistoryRepository.class);
        MarketFlowEvent event = event();
        when(repository.existsByEventId(event.eventId())).thenReturn(true);
        new OrderHistoryListener(repository, Clock.fixed(NOW, ZoneOffset.UTC)).handle(event);
        verify(repository, never()).save(any());
    }

    private MarketFlowEvent event() {
        return MarketFlowEvent.create(
                MarketFlowEventType.ORDER_PAID,
                42L, null, 7L, null, null, "PROCESSING", "PAID", NOW
        );
    }
}
