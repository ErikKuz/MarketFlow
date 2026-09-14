package com.example.marketflow.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MarketFlowEvent(
        UUID eventId,
        MarketFlowEventType eventType,
        Long orderId,
        Long sellerOrderId,
        Long buyerId,
        Long sellerId,
        BigDecimal amount,
        String previousStatus,
        String currentStatus,
        Instant occurredAt
) {
    public MarketFlowEvent {
        if (eventId == null || eventType == null || occurredAt == null) {
            throw new IllegalArgumentException("Идентификатор, тип и время события обязательны");
        }
    }

    public static MarketFlowEvent create(
            MarketFlowEventType eventType,
            Long orderId,
            Long sellerOrderId,
            Long buyerId,
            Long sellerId,
            BigDecimal amount,
            String previousStatus,
            String currentStatus,
            Instant occurredAt
    ) {
        return new MarketFlowEvent(
                UUID.randomUUID(), eventType, orderId, sellerOrderId, buyerId, sellerId,
                amount, previousStatus, currentStatus, occurredAt
        );
    }
}
