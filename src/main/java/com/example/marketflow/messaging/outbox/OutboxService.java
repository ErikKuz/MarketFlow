package com.example.marketflow.messaging.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.marketflow.Repository.OutboxEventRepository;
import com.example.marketflow.messaging.MarketFlowEvent;
import com.example.marketflow.messaging.command.MarketFlowCommand;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class OutboxService {
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public OutboxEventEntity save(MarketFlowEvent event, String routingKey) {
        validateRoutingKey(routingKey);
        String payload = objectMapper.writeValueAsString(event);
        Long aggregateId = event.orderId() != null ? event.orderId() : event.sellerId();
        return outboxRepository.save(new OutboxEventEntity(
                event.eventId(),
                event.eventType().name(),
                routingKey,
                aggregateId,
                payload,
                event.occurredAt()
        ));
    }

    @Transactional
    public OutboxEventEntity saveCommand(MarketFlowCommand command, String routingKey) {
        validateRoutingKey(routingKey);
        String payload = objectMapper.writeValueAsString(command);
        Long aggregateId = command.orderId() != null ? command.orderId() : command.sellerId();
        return outboxRepository.save(new OutboxEventEntity(
                command.commandId(),
                command.commandType().name(),
                routingKey,
                aggregateId,
                payload,
                command.createdAt()
        ));
    }

    private void validateRoutingKey(String routingKey) {
        if (routingKey == null || routingKey.isBlank()) {
            throw new IllegalArgumentException("Routing key не должен быть пустым");
        }
    }
}
