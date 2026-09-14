package com.example.marketflow.messaging.history;

import java.time.Instant;
import java.util.UUID;

import com.example.marketflow.messaging.MarketFlowEvent;
import com.example.marketflow.messaging.MarketFlowEventType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_event_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderEventHistoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "seller_order_id")
    private Long sellerOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 60)
    private MarketFlowEventType eventType;

    @Column(name = "previous_status", length = 60)
    private String previousStatus;

    @Column(name = "current_status", length = 60)
    private String currentStatus;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public OrderEventHistoryEntity(MarketFlowEvent event, Instant createdAt) {
        this.eventId = event.eventId();
        this.orderId = event.orderId();
        this.sellerOrderId = event.sellerOrderId();
        this.eventType = event.eventType();
        this.previousStatus = event.previousStatus();
        this.currentStatus = event.currentStatus();
        this.occurredAt = event.occurredAt();
        this.createdAt = createdAt;
    }
}
