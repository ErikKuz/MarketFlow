package com.example.marketflow.marketplace;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "audit_events")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "actor_id") private Long actorId;
    @Column(name = "order_id") private Long orderId;
    @Column(name = "seller_id") private Long sellerId;
    @Column(nullable = false, length = 60) private String action;
    @Column(name = "previous_status", length = 40) private String previousStatus;
    @Column(name = "next_status", length = 40) private String nextStatus;
    @Column(length = 1000) private String detail;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    public AuditEventEntity(Long actorId, Long orderId, Long sellerId, String action,
            String previous, String next, String detail, Instant now) {
        this.actorId = actorId; this.orderId = orderId; this.sellerId = sellerId;
        this.action = action; this.previousStatus = previous; this.nextStatus = next;
        this.detail = detail; this.createdAt = now;
    }
}
