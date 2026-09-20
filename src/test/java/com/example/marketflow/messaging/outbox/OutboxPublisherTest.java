package com.example.marketflow.messaging.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import com.example.marketflow.messaging.command.MarketFlowCommand;
import com.example.marketflow.service.WalletService;

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
                eq(RabbitMqNames.BUSINESS_EVENTS_EXCHANGE), eq(RabbitMqNames.ORDER_CREATED_EVENT),
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
                eq(RabbitMqNames.BUSINESS_EVENTS_EXCHANGE), eq(RabbitMqNames.ORDER_CREATED_EVENT),
                any(MarketFlowEvent.class), any(CorrelationData.class));

        fixture.publisher.publishPendingEvents();

        assertEquals(OutboxStatus.NEW, fixture.entity.getStatus());
        assertEquals(1, fixture.entity.getAttempts());
        assertEquals("RabbitMQ отклонил сообщение: broker error", fixture.entity.getLastError());
    }

    @Test
    void commandIsPublishedToCommandsExchange() {
        Fixture fixture = commandFixture();
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(fixture.template).convertAndSend(
                eq(RabbitMqNames.MONEY_COMMANDS_EXCHANGE), eq(RabbitMqNames.REQUEST_SELLER_WITHDRAWAL_COMMAND),
                any(MarketFlowCommand.class), any(CorrelationData.class));

        fixture.publisher.publishPendingEvents();

        assertEquals(OutboxStatus.PUBLISHED, fixture.entity.getStatus());
    }

    @Test
    void permanentlyFailedWithdrawalReturnsReservedMoney() {
        Fixture fixture = commandFixture();
        ReflectionTestUtils.setField(fixture.publisher, "maxAttempts", 1);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "broker error"));
            return null;
        }).when(fixture.template).convertAndSend(
                eq(RabbitMqNames.MONEY_COMMANDS_EXCHANGE),
                eq(RabbitMqNames.REQUEST_SELLER_WITHDRAWAL_COMMAND),
                any(MarketFlowCommand.class),
                any(CorrelationData.class)
        );

        fixture.publisher.publishPendingEvents();

        assertEquals(OutboxStatus.FAILED, fixture.entity.getStatus());
        verify(fixture.walletService).cancelUnpublishedWithdrawal(any(MarketFlowCommand.class));
    }

    private Fixture fixture() {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        MarketFlowEvent event = MarketFlowEvent.create(
                MarketFlowEventType.ORDER_CREATED,
                42L, null, 7L, null, null, null, "CREATED", NOW
        );
        OutboxEventEntity entity = new OutboxEventEntity(
                event.eventId(), event.eventType(), RabbitMqNames.ORDER_CREATED_EVENT,
                event.orderId(), mapper.writeValueAsString(event), NOW
        );
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        RabbitTemplate template = mock(RabbitTemplate.class);
        WalletService walletService = mock(WalletService.class);
        when(repository.findByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAscIdAsc(
                eq(OutboxStatus.NEW), eq(NOW), any())).thenReturn(List.of(entity));
        OutboxPublisher publisher = new OutboxPublisher(
                repository, template, mapper, Clock.fixed(NOW, ZoneOffset.UTC), walletService
        );
        ReflectionTestUtils.setField(publisher, "batchSize", 10);
        ReflectionTestUtils.setField(publisher, "maxAttempts", 3);
        ReflectionTestUtils.setField(publisher, "confirmTimeout", Duration.ofSeconds(1));
        return new Fixture(publisher, template, entity, walletService);
    }

    private Fixture commandFixture() {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        MarketFlowCommand command = MarketFlowCommand.withdrawal(
                81L, 7L, 15L, new java.math.BigDecimal("60.00"), NOW
        );
        OutboxEventEntity entity = new OutboxEventEntity(
                command.commandId(), command.commandType().name(), RabbitMqNames.REQUEST_SELLER_WITHDRAWAL_COMMAND,
                command.sellerId(), mapper.writeValueAsString(command), NOW
        );
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        RabbitTemplate template = mock(RabbitTemplate.class);
        WalletService walletService = mock(WalletService.class);
        when(repository.findByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAscIdAsc(
                eq(OutboxStatus.NEW), eq(NOW), any())).thenReturn(List.of(entity));
        OutboxPublisher publisher = new OutboxPublisher(
                repository, template, mapper, Clock.fixed(NOW, ZoneOffset.UTC), walletService
        );
        ReflectionTestUtils.setField(publisher, "batchSize", 10);
        ReflectionTestUtils.setField(publisher, "maxAttempts", 3);
        ReflectionTestUtils.setField(publisher, "confirmTimeout", Duration.ofSeconds(1));
        return new Fixture(publisher, template, entity, walletService);
    }

    private record Fixture(
            OutboxPublisher publisher,
            RabbitTemplate template,
            OutboxEventEntity entity,
            WalletService walletService
    ) {
    }
}
