package com.example.marketflow.payment;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import com.example.marketflow.exception.RefundNotAvailableException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTransactionEntity {//он фиксирует, что денежная операция произошла.

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionType type;

    @DecimalMin("0.01")
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionStatus status;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "payment_card_id")
    private Long paymentCardId;

    @Column(nullable = false)
    private boolean pending;

    public PaymentTransactionEntity hold() { pending = true; return this; }
    public void makeAvailable() { pending = false; }

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PaymentTransactionEntity(
            Long orderId,
            Long userId,
            TransactionType type,
            BigDecimal amount,
            TransactionStatus status,
            String idempotencyKey
    ) {
        this(orderId, userId, type, amount, status, idempotencyKey, null);
    }

    public PaymentTransactionEntity(
            Long orderId,
            Long userId,
            TransactionType type,
            BigDecimal amount,
            TransactionStatus status,
            String idempotencyKey,
            Long paymentCardId
    ) {
        this.orderId = orderId;
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.paymentCardId = paymentCardId;
    }

    public void markRefunded() {
        if (status != TransactionStatus.COMPLETED) {
            throw new RefundNotAvailableException(
                    "Only a completed transaction can be refunded"
            );
        }

        status = TransactionStatus.REFUNDED;
        pending = false;
    }
}
