package com.example.marketflow.Repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.example.marketflow.payment.PaymentTransactionEntity;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransactionEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select transaction from PaymentTransactionEntity transaction where transaction.id = :id")
    Optional<PaymentTransactionEntity> findLockedById(@Param("id") Long id);

    List<PaymentTransactionEntity> findAllByOrderId(Long orderId);

    List<PaymentTransactionEntity> findAllByWalletAccountIdOrderByCreatedAtDesc(Long walletAccountId);

    Page<PaymentTransactionEntity> findAllByWalletAccountIdOrderByCreatedAtDescIdDesc(
            Long walletAccountId,
            Pageable pageable
    );

    List<PaymentTransactionEntity> findAllBySellerOrderIdOrderByCreatedAt(Long sellerOrderId);

    Optional<PaymentTransactionEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentTransactionEntity> findByIdAndUserIdAndType(
            Long id,
            Long userId,
            com.example.marketflow.payment.TransactionType type
    );

    Optional<PaymentTransactionEntity> findBySellerOrderIdAndWalletAccountIdAndType(
            Long sellerOrderId,
            Long walletAccountId,
            com.example.marketflow.payment.TransactionType type
    );

    boolean existsBySellerOrderIdAndWalletAccountIdAndType(
            Long sellerOrderId,
            Long walletAccountId,
            com.example.marketflow.payment.TransactionType type
    );
}
