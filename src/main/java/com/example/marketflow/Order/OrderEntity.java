package com.example.marketflow.Order;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.example.marketflow.exception.InvalidOrderStateException;
import com.example.marketflow.payment.PaymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus;

    @Column(name = "total_amount", nullable = false)
    @DecimalMin(value = "0.01")
    private BigDecimal totalPrice;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    public void recordDelivery(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public OrderEntity(
            Long buyerId,
            OrderStatus status,
            BigDecimal totalPrice
    ) {
        this.buyerId = buyerId;
        this.status = status;
        this.paymentStatus = PaymentStatus.NOT_PAID;
        this.totalPrice = totalPrice;
    }

    public void changeStatus(OrderStatus status) {
        if (!isAllowedOrderTransition(this.status, status)) {
            throw new InvalidOrderStateException(
                    "Order status cannot change from " + this.status + " to " + status
            );
        }
        this.status = status;
    }

    public void changePaymentStatus(PaymentStatus paymentStatus) {
        if (!isAllowedPaymentTransition(this.paymentStatus, paymentStatus)) {
            throw new InvalidOrderStateException(
                    "Payment status cannot change from "
                            + this.paymentStatus
                            + " to "
                            + paymentStatus
            );
        }
        this.paymentStatus = paymentStatus;
    }

    private boolean isAllowedOrderTransition(//просто проверяем что next допустимый статус
            OrderStatus current,
            OrderStatus next
    ) {
        if (current == null || next == null) {
            return false;
        }

        return switch (current) {
            case CREATED -> next == OrderStatus.CONFIRMED
                    || next == OrderStatus.CANCELLED;
            case CONFIRMED -> next == OrderStatus.SELLERSSTARTWORK
                    || next == OrderStatus.CANCELLED;
            case SELLERSSTARTWORK -> next == OrderStatus.SELLERSENDWORKANDSEND;
            case SELLERSENDWORKANDSEND -> next == OrderStatus.COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    private boolean isAllowedPaymentTransition(//просто проверяем что next допустимый статус
            PaymentStatus current,
            PaymentStatus next
    ) {
        if (current == null || next == null) {
            return false;
        }

        return switch (current) {
            case NOT_PAID, FAILED -> next == PaymentStatus.PROCESSING;
            case PROCESSING -> next == PaymentStatus.PAID
                    || next == PaymentStatus.FAILED;
            case PAID -> next == PaymentStatus.REFUNDED;
            case REFUNDED -> false;
        };
    }
}
