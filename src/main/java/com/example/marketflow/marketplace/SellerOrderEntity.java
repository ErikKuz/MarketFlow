package com.example.marketflow.marketplace;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "seller_orders", uniqueConstraints = @UniqueConstraint(columnNames = {"order_id", "seller_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SellerOrderEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;
    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "commission_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal commissionRate = new BigDecimal("0.1000");//Хранит процент комиссии MarketFlow

    @Column(name = "commission_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal commissionAmount = BigDecimal.ZERO;//Хранит конкретную сумму, которую получает платформа.

    @Column(name = "seller_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal sellerAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", nullable = false, length = 20)
    private OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS settlementStatus =
            OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS.NOT_DISTRIBUTE;//Показывает состояние
    //  начисления денег продавцу и платформе.

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private OBSERFFORSENDBYSELLERPRODUCTSTATUS status = OBSERFFORSENDBYSELLERPRODUCTSTATUS.NEW;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    public SellerOrderEntity(Long orderId, Long sellerId, BigDecimal amount, Instant now) {
        this.orderId = orderId; this.sellerId = sellerId; this.totalAmount = amount; this.createdAt = now;
    }
    public void transition(OBSERFFORSENDBYSELLERPRODUCTSTATUS next, Instant now) {
        boolean allowed = switch (status) {
            case NEW -> next == OBSERFFORSENDBYSELLERPRODUCTSTATUS.PROCESSING
                    || next == OBSERFFORSENDBYSELLERPRODUCTSTATUS.CANCELLED;
            case PROCESSING -> next == OBSERFFORSENDBYSELLERPRODUCTSTATUS.SELLERSENDPRODUCT;
            case SELLERSENDPRODUCT -> next == OBSERFFORSENDBYSELLERPRODUCTSTATUS.USERGETPRODUCT;
            case USERGETPRODUCT, CANCELLED, RETURNED -> false;
        };
        if (!allowed) throw MarketplaceException.conflict("Fulfillment cannot change from " + status + " to " + next);
        status = next;
        if (next == OBSERFFORSENDBYSELLERPRODUCTSTATUS.SELLERSENDPRODUCT) shippedAt = now;
        if (next == OBSERFFORSENDBYSELLERPRODUCTSTATUS.USERGETPRODUCT) deliveredAt = now;
    }

    public void recordSettlement(
        BigDecimal commissionRate,
        BigDecimal commissionAmount,
        BigDecimal sellerAmount
    ) {
        if (settlementStatus != OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS.NOT_DISTRIBUTE) {
            throw MarketplaceException.conflict(
                    "Settlement cannot be recorded from status "
                            + settlementStatus
            );
        }

        validateSettlementAmounts(
                commissionRate,
                commissionAmount,
                sellerAmount
        );

        this.commissionRate = commissionRate;
        this.commissionAmount = commissionAmount;
        this.sellerAmount = sellerAmount;
        this.settlementStatus = OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS.PENDINGWALLET;
    }

    private void validateSettlementAmounts(
        BigDecimal commissionRate,
        BigDecimal commissionAmount,
        BigDecimal sellerAmount
    ) {
        if (commissionRate == null
                || commissionAmount == null
                || sellerAmount == null) {
            throw new IllegalArgumentException(
                    "Settlement values must not be null"
            );
        }

        if (commissionRate.signum() < 0
                || commissionRate.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException(
                    "Commission rate must be between 0 and 1"
            );
        }

        if (commissionAmount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Commission amount must not be negative"
            );
        }

        if (sellerAmount.signum() < 0) {
            throw new IllegalArgumentException(
                    "Seller amount must not be negative"
            );
        }

        BigDecimal distributedAmount =
                commissionAmount.add(sellerAmount);

        if (distributedAmount.compareTo(totalAmount) != 0) {
            throw new IllegalArgumentException(
                    "Commission amount and seller amount "
                            + "must equal seller order total"
            );
        }
    }

    public void markSettlementAvailable() {
        if (settlementStatus != OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS.PENDINGWALLET) {
            throw MarketplaceException.conflict(
                    "Settlement cannot become available from status "
                            + settlementStatus
            );
        }
        this.settlementStatus = OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS.MAINWALLET;
    }

    public void reverseSettlement() {
        if (settlementStatus != OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS.PENDINGWALLET) {
            throw MarketplaceException.conflict(
                    "Settlement cannot be reversed from status "
                            + settlementStatus
            );
        }
        this.settlementStatus = OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS.RETURNMONEY;
    }


}
