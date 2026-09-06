package com.example.marketflow.marketplace;

import java.math.BigDecimal;
import java.time.Instant;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "seller_orders", uniqueConstraints = @UniqueConstraint(columnNames = {"order_id", "seller_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerOrderEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "order_id", nullable = false) private Long orderId;
    @Column(name = "seller_id", nullable = false) private Long sellerId;
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2) private BigDecimal totalAmount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private FulfillmentStatus status = FulfillmentStatus.NEW;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "shipped_at") private Instant shippedAt;
    @Column(name = "delivered_at") private Instant deliveredAt;

    public SellerOrderEntity(Long orderId, Long sellerId, BigDecimal amount, Instant now) {
        this.orderId = orderId; this.sellerId = sellerId; this.totalAmount = amount; this.createdAt = now;
    }
    public void transition(FulfillmentStatus next, Instant now) {
        boolean allowed = switch (status) {
            case NEW -> next == FulfillmentStatus.ACCEPTED || next == FulfillmentStatus.CANCELLED;
            case ACCEPTED -> next == FulfillmentStatus.PACKING;
            case PACKING -> next == FulfillmentStatus.SHIPPED;
            case SHIPPED -> next == FulfillmentStatus.DELIVERED;
            case DELIVERED -> next == FulfillmentStatus.RETURNED;
            case CANCELLED, RETURNED -> false;
        };
        if (!allowed) throw MarketplaceException.conflict("Fulfillment cannot change from " + status + " to " + next);
        status = next;
        if (next == FulfillmentStatus.SHIPPED) shippedAt = now;
        if (next == FulfillmentStatus.DELIVERED) deliveredAt = now;
    }
}
