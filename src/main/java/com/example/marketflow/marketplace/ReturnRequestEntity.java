package com.example.marketflow.marketplace;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "return_requests")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReturnRequestEntity {
    public enum Status { PENDING, APPROVED, REJECTED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "order_id", nullable = false, unique = true) private Long orderId;
    @Column(name = "buyer_id", nullable = false) private Long buyerId;
    @Column(nullable = false, length = 1000) private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Status status = Status.PENDING;
    @Column(name = "decision_reason", length = 1000) private String decisionReason;
    @Column(name = "requested_at", nullable = false) private Instant requestedAt;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(nullable = false) private boolean restocked;
    public ReturnRequestEntity(Long orderId, Long buyerId, String reason, Instant now) {
        this.orderId = orderId; this.buyerId = buyerId; this.reason = reason; this.requestedAt = now;
    }
    public void decide(boolean approved, String reason, boolean restock, Instant now) {
        if (status != Status.PENDING) throw MarketplaceException.conflict("Return request already decided");
        status = approved ? Status.APPROVED : Status.REJECTED;
        decisionReason = reason; restocked = approved && restock; decidedAt = now;
    }
}
