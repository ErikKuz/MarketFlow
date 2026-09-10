package com.example.marketflow.payment_cards;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Entity
@Table(name="payment_cards")
@Getter
@Setter
@RequiredArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCardEntity {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @NonNull
    @Column(name="user_id")
    private Long userid;
    @NonNull
    @Column(name="card_token",length=100)
    private String cardtoken;
    @NonNull
    @Column(name = "masked_number", nullable = false, length = 30)
    private String maskedNumber;

    @NonNull
    @Column(precision=12,scale=2)
    private BigDecimal balance;

    
    @Column(nullable = false)
    private boolean active = true;
    
    public void debit(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || balance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Invalid card debit");
        }
        balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Invalid card credit");
        }
        balance = balance.add(amount);
    }

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
