package com.example.marketflow.messaging.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.marketflow.Repository.OutboxEventRepository;
import com.example.marketflow.messaging.MarketFlowEvent;
import com.example.marketflow.messaging.MarketFlowEventType;
import com.example.marketflow.messaging.RabbitMqNames;
import com.example.marketflow.messaging.command.MarketFlowCommand;

import tools.jackson.databind.json.JsonMapper;

class OutboxServiceTest {

    @Test
    void savesJsonEventWithNewStatusAndRoutingKey() {
        OutboxEventRepository repository = org.mockito.Mockito.mock(OutboxEventRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        OutboxService service = new OutboxService(repository, JsonMapper.builder().findAndAddModules().build());
        MarketFlowEvent event = MarketFlowEvent.create(
                MarketFlowEventType.ORDER_CREATED,
                42L, null, 7L, null, null, null, "CREATED", Instant.parse("2026-01-01T00:00:00Z")
        );

        service.save(event, RabbitMqNames.ORDER_CREATED_EVENT);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(repository).save(captor.capture());
        OutboxEventEntity saved = captor.getValue();
        assertEquals(event.eventId(), saved.getEventId());
        assertEquals(OutboxStatus.NEW, saved.getStatus());
        assertEquals(RabbitMqNames.ORDER_CREATED_EVENT, saved.getRoutingKey());
        assertEquals(42L, saved.getAggregateId());
        assertTrue(saved.getPayload().contains("ORDER_CREATED"));
    }

    @Test
    void savesCommandInTheSameTransactionalOutbox() {
        OutboxEventRepository repository = org.mockito.Mockito.mock(OutboxEventRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        OutboxService service = new OutboxService(repository, JsonMapper.builder().findAndAddModules().build());
        MarketFlowCommand command = MarketFlowCommand.withdrawal(
                81L, 7L, 15L, new java.math.BigDecimal("60.00"),
                Instant.parse("2026-01-01T00:00:00Z")
        );

        service.saveCommand(command, RabbitMqNames.REQUEST_SELLER_WITHDRAWAL_COMMAND);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(repository).save(captor.capture());
        OutboxEventEntity saved = captor.getValue();
        assertEquals(command.commandId(), saved.getEventId());
        assertEquals("WITHDRAWAL_REQUESTED", saved.getEventType());
        assertEquals(RabbitMqNames.REQUEST_SELLER_WITHDRAWAL_COMMAND, saved.getRoutingKey());
        assertEquals(7L, saved.getAggregateId());
        assertTrue(saved.getPayload().contains("WITHDRAWAL_REQUESTED"));
        assertEquals(OutboxStatus.NEW, saved.getStatus());
    }
}
