package com.example.marketflow.messaging.listener;

import java.time.Clock;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.marketflow.Repository.OrderEventHistoryRepository;
import com.example.marketflow.messaging.MarketFlowEvent;
import com.example.marketflow.messaging.RabbitMqNames;
import com.example.marketflow.messaging.history.OrderEventHistoryEntity;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "spring.rabbitmq.listener.simple.auto-startup",
        havingValue = "true",
        matchIfMissing = true
)
public class OrderHistoryListener {
    private final OrderEventHistoryRepository historyRepository;
    private final Clock clock;

    @RabbitListener(queues = RabbitMqNames.ORDER_EVENT_HISTORY_QUEUE)
    @Transactional
    public void handle(MarketFlowEvent event) {
        if (!historyRepository.existsByEventId(event.eventId())) {
            historyRepository.save(new OrderEventHistoryEntity(event, clock.instant()));
        }
    }
}
