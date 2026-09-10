package com.example.marketflow.payment;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "wallet_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WalletType type;

    /*
     * Деньги уже начислены, но заказ ещё не завершён.
     * Вывести или окончательно использовать их пока нельзя.
     */
    @Column(
            name = "pending_balance",
            nullable = false,
            precision = 14,
            scale = 2
    )
    private BigDecimal pendingBalance = BigDecimal.ZERO;

    /*
     * Деньги окончательно принадлежат продавцу или платформе.
     */
    @Column(
            name = "available_balance",
            nullable = false,
            precision = 14,
            scale = 2
    )
    private BigDecimal availableBalance = BigDecimal.ZERO;

    /*
     * Защищает кошелёк от одновременного изменения
     * двумя транзакциями.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private WalletAccountEntity(
            Long userId,
            WalletType type
    ) {
        this.userId = userId;
        this.type = type;
        this.pendingBalance = BigDecimal.ZERO;
        this.availableBalance = BigDecimal.ZERO;
    }

    public static WalletAccountEntity seller(Long sellerId) {
        if (sellerId == null) {
            throw new IllegalArgumentException(
                    "Seller id must not be null"
            );
        }

        return new WalletAccountEntity(
                sellerId,
                WalletType.SELLER
        );
    }

    public static WalletAccountEntity platform() {
        return new WalletAccountEntity(
                null,
                WalletType.PLATFORM
        );
    }

    public void addPending(BigDecimal amount) {
        validatePositiveAmount(amount);

        this.pendingBalance = this.pendingBalance.add(amount);
    }
    public void releasePending(BigDecimal amount) {
        validatePositiveAmount(amount);
        if (this.pendingBalance.compareTo(amount) < 0) {
                throw new IllegalStateException(
                        "Недостаточно ожидающих средств"
                );
        }
        this.pendingBalance = this.pendingBalance.subtract(amount);
        this.availableBalance = this.availableBalance.add(amount);
    }
    public void minusPending(BigDecimal amount){
        validatePositiveAmount(amount);
        if (this.pendingBalance.compareTo(amount) <0)throw new IllegalStateException("Error");
        this.pendingBalance = this.pendingBalance.subtract(amount);
    }

    public void withdraw(BigDecimal amount) {
    validatePositiveAmount(amount);

    if (this.availableBalance.compareTo(amount) < 0) {
        throw new IllegalStateException(
                "Недостаточно доступных средств"
        );
    }

    this.availableBalance = this.availableBalance.subtract(amount);
}


    private void validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException(
                        "Сумма должна быть больше нуля"
                );
        }
    }
}
