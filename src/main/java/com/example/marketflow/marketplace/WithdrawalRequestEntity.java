package com.example.marketflow.marketplace;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "withdrawal_requests", uniqueConstraints = @UniqueConstraint(columnNames = {"seller_id", "request_key"}))
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WithdrawalRequestEntity {
    public enum Status { PENDING, APPROVED, REJECTED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "seller_id", nullable = false) private Long sellerId;
    @Column(name = "card_id", nullable = false) private Long cardId;
    @Column(name = "request_key", nullable = false, length = 100) private String requestKey;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Status status = Status.PENDING;
    @Column(name = "decision_reason", length = 1000) private String decisionReason;
    @Column(name = "requested_at", nullable = false) private Instant requestedAt;
    @Column(name = "decided_at") private Instant decidedAt;
    public WithdrawalRequestEntity(Long sellerId, Long cardId, BigDecimal amount, String key, Instant now) {
        this.sellerId = sellerId; this.cardId = cardId; this.amount = amount; this.requestedAt = now;
        this.requestKey = key;
    }
    public void decide(boolean approved, String reason, Instant now) {
        if (status != Status.PENDING) throw MarketplaceException.conflict("Withdrawal already decided");
        status = approved ? Status.APPROVED : Status.REJECTED;
        decisionReason = reason; decidedAt = now;
    }
}
