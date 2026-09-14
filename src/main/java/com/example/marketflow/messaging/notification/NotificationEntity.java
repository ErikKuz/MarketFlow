package com.example.marketflow.messaging.notification;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "recipient_type", nullable = false, length = 20)
    private String recipientType;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public NotificationEntity(
            UUID eventId,
            Long userId,
            String recipientType,
            String title,
            String message,
            Instant createdAt
    ) {
        this.eventId = eventId;
        this.userId = userId;
        this.recipientType = recipientType;
        this.title = title;
        this.message = message;
        this.createdAt = createdAt;
    }

    public void markRead() {
        read = true;
    }
}
