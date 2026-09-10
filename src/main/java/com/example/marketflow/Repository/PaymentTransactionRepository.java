package com.example.marketflow.Repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.marketflow.payment.PaymentTransactionEntity;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransactionEntity, Long> {
    List<PaymentTransactionEntity> findAllByOrderId(Long orderId);

    List<PaymentTransactionEntity> findAllByWalletAccountIdOrderByCreatedAtDesc(Long walletAccountId);

    List<PaymentTransactionEntity> findAllBySellerOrderIdOrderByCreatedAt(Long sellerOrderId);

    Optional<PaymentTransactionEntity> findByIdempotencyKey(String idempotencyKey);

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
