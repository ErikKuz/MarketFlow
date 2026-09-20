package com.example.marketflow.messaging.outbox;

import java.time.Instant;
import java.util.UUID;

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
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 60)
    private String eventType;

    @Column(name = "routing_key", nullable = false, length = 100)
    private String routingKey;

    @Column(name = "aggregate_id")
    private Long aggregateId;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    public OutboxEventEntity(
            UUID eventId,
            MarketFlowEventType eventType,
            String routingKey,
            Long aggregateId,
            String payload,
            Instant now
    ) {
        this(eventId, eventType.name(), routingKey, aggregateId, payload, now);
    }

    public OutboxEventEntity(
            UUID eventId,
            String eventType,
            String routingKey,
            Long aggregateId,
            String payload,
            Instant now
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.routingKey = routingKey;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.status = OutboxStatus.NEW;
        this.availableAt = now;
        this.createdAt = now;
    }

    public void markPublished(Instant now) {
        status = OutboxStatus.PUBLISHED;
        publishedAt = now;
        lastError = null;
    }

    public void registerFailure(String error, Instant nextAttemptAt, int maxAttempts) {
        attempts++;
        lastError = error == null ? "Неизвестная ошибка публикации" : error.substring(0, Math.min(error.length(), 1000));
        availableAt = nextAttemptAt;
        if (attempts >= maxAttempts) {
            status = OutboxStatus.FAILED;
        }
    }
}
