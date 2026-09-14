package com.example.marketflow.messaging.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.marketflow.Repository.OutboxEventRepository;
import com.example.marketflow.messaging.MarketFlowEvent;
import com.example.marketflow.messaging.MarketFlowEventType;
import com.example.marketflow.messaging.RabbitMqNames;

import tools.jackson.databind.json.JsonMapper;

class OutboxPublisherTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void confirmedEventBecomesPublished() {
        Fixture fixture = fixture();
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(fixture.template).convertAndSend(
                eq(RabbitMqNames.EVENTS_EXCHANGE), eq(RabbitMqNames.ORDER_CREATED),
                any(MarketFlowEvent.class), any(CorrelationData.class));

        fixture.publisher.publishPendingEvents();

        assertEquals(OutboxStatus.PUBLISHED, fixture.entity.getStatus());
        assertEquals(NOW, fixture.entity.getPublishedAt());
    }

    @Test
    void rejectedEventStaysNewAndSchedulesRetry() {
        Fixture fixture = fixture();
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "broker error"));
            return null;
        }).when(fixture.template).convertAndSend(
                eq(RabbitMqNames.EVENTS_EXCHANGE), eq(RabbitMqNames.ORDER_CREATED),
                any(MarketFlowEvent.class), any(CorrelationData.class));

        fixture.publisher.publishPendingEvents();

        assertEquals(OutboxStatus.NEW, fixture.entity.getStatus());
        assertEquals(1, fixture.entity.getAttempts());
        assertEquals("RabbitMQ отклонил сообщение: broker error", fixture.entity.getLastError());
    }

    private Fixture fixture() {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        MarketFlowEvent event = MarketFlowEvent.create(
                MarketFlowEventType.ORDER_CREATED,
                42L, null, 7L, null, null, null, "CREATED", NOW
        );
        OutboxEventEntity entity = new OutboxEventEntity(
                event.eventId(), event.eventType(), RabbitMqNames.ORDER_CREATED,
                event.orderId(), mapper.writeValueAsString(event), NOW
        );
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        RabbitTemplate template = mock(RabbitTemplate.class);
        when(repository.findByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAscIdAsc(
                eq(OutboxStatus.NEW), eq(NOW), any())).thenReturn(List.of(entity));
        OutboxPublisher publisher = new OutboxPublisher(
                repository, template, mapper, Clock.fixed(NOW, ZoneOffset.UTC)
        );
        ReflectionTestUtils.setField(publisher, "batchSize", 10);
        ReflectionTestUtils.setField(publisher, "maxAttempts", 3);
        ReflectionTestUtils.setField(publisher, "confirmTimeout", Duration.ofSeconds(1));
        return new Fixture(publisher, template, entity);
    }

    private record Fixture(
            OutboxPublisher publisher,
            RabbitTemplate template,
            OutboxEventEntity entity
    ) {
    }
}
