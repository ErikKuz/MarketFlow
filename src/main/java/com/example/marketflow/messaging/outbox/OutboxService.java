package com.example.marketflow.messaging.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.marketflow.Repository.OutboxEventRepository;
import com.example.marketflow.messaging.MarketFlowEvent;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OutboxService {
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OutboxEventEntity save(MarketFlowEvent event, String routingKey) {
        if (routingKey == null || routingKey.isBlank()) {
            throw new IllegalArgumentException("Routing key не должен быть пустым");
        }
        String payload = objectMapper.writeValueAsString(event);
        Long aggregateId = event.orderId() != null ? event.orderId() : event.sellerId();
        return outboxRepository.save(new OutboxEventEntity(
                event.eventId(),
                event.eventType(),
                routingKey,
                aggregateId,
                payload,
                event.occurredAt()
        ));
    }
}
