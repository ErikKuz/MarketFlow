package com.example.marketflow.marketplace;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "seller_applications")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerApplicationEntity {
    public enum Status { PENDING, ACTIVE, REJECTED, BLOCKED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false, unique = true) private Long userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private Status status = Status.PENDING;
    @Column(length = 1000) private String reason;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "decided_at") private Instant decidedAt;
    public SellerApplicationEntity(Long userId, Instant now) { this.userId = userId; this.createdAt = now; }
    public void decide(Status status, String reason, Instant now) {
        this.status = status; this.reason = reason; this.decidedAt = now;
    }
    public void resubmit(Instant now) {
        if (status != Status.REJECTED) throw MarketplaceException.conflict("Seller application already exists");
        status = Status.PENDING; reason = null; decidedAt = null; createdAt = now;
    }
}
