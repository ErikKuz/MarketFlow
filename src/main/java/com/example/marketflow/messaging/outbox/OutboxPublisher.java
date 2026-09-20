package com.example.marketflow.messaging.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.marketflow.Repository.OutboxEventRepository;
import com.example.marketflow.messaging.MarketFlowEvent;
import com.example.marketflow.messaging.RabbitMqNames;
import com.example.marketflow.messaging.command.MarketFlowCommand;
import com.example.marketflow.service.WalletService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "marketflow.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {
    private final OutboxEventRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final WalletService walletService;

    @Value("${marketflow.outbox.batch-size:50}")
    private int batchSize;

    @Value("${marketflow.outbox.max-attempts:5}")
    private int maxAttempts;

    @Value("${marketflow.outbox.confirm-timeout:10s}")
    private Duration confirmTimeout;

    @Scheduled(fixedDelayString = "${marketflow.outbox.delay:1000}")
    @Transactional
    public void publishPendingEvents() {
        Instant now = clock.instant();
        var events = outboxRepository
                .findByStatusAndAvailableAtLessThanEqualOrderByCreatedAtAscIdAsc(
                        OutboxStatus.NEW,
                        now,
                        PageRequest.of(0, batchSize)
                );
        for (OutboxEventEntity entity : events) {
            publish(entity, now);
        }
    }

    private void publish(OutboxEventEntity entity, Instant now) {
        try {
            boolean command = entity.getRoutingKey().startsWith("command.");
            Object message = command
                    ? objectMapper.readValue(entity.getPayload(), MarketFlowCommand.class)
                    : objectMapper.readValue(entity.getPayload(), MarketFlowEvent.class);
            CorrelationData correlation = new CorrelationData(entity.getEventId().toString());
            rabbitTemplate.convertAndSend(
                    command ? RabbitMqNames.MONEY_COMMANDS_EXCHANGE : RabbitMqNames.BUSINESS_EVENTS_EXCHANGE,
                    entity.getRoutingKey(),
                    message,
                    correlation
            );
            CorrelationData.Confirm confirm = correlation.getFuture()//получается Future — объект, в котором позже появится ответ RabbitMQ.
                    .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
                    //Если:confirm.ack() == true   RabbitMQ принял сообщение.
            if (!confirm.ack()) {
                throw new IllegalStateException("RabbitMQ отклонил сообщение: " + confirm.reason());
            }
            if (correlation.getReturned() != null) {
                throw new IllegalStateException(
                        "Для routing key не найдена очередь: " + entity.getRoutingKey()
                );
            }
            entity.markPublished(clock.instant());
        } catch (Exception exception) {
            long delaySeconds = Math.min(60, 1L << Math.min(entity.getAttempts(), 6));
            entity.registerFailure(
                    exception.getMessage(),
                    now.plusSeconds(delaySeconds),
                    maxAttempts
            );
            cancelWithdrawalIfPublishingPermanentlyFailed(entity);
        }
    }

    private void cancelWithdrawalIfPublishingPermanentlyFailed(OutboxEventEntity entity) {
        if (entity.getStatus() != OutboxStatus.FAILED
                || !RabbitMqNames.REQUEST_SELLER_WITHDRAWAL_COMMAND.equals(entity.getRoutingKey())) {
            return;
        }
        MarketFlowCommand command = objectMapper.readValue(
                entity.getPayload(),
                MarketFlowCommand.class
        );
        walletService.cancelUnpublishedWithdrawal(command);
    }
}
