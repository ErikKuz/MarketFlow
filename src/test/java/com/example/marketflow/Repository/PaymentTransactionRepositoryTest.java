package com.example.marketflow.Repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.example.marketflow.payment.PaymentTransactionEntity;
import com.example.marketflow.payment.TransactionStatus;
import com.example.marketflow.payment.TransactionType;

@DataJpaTest
class PaymentTransactionRepositoryTest {

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Test
    void shouldFindTransactionByIdempotencyKey() {
        PaymentTransactionEntity transaction = new PaymentTransactionEntity(
                15L,
                7L,
                TransactionType.PAYMENT,
                new BigDecimal("1000.00"),
                TransactionStatus.COMPLETED,
                "payment-order-15",
                3L
        );
        paymentTransactionRepository.saveAndFlush(transaction);

        assertTrue(paymentTransactionRepository.findByIdempotencyKey("payment-order-15").isPresent());
        PaymentTransactionEntity found = paymentTransactionRepository
                .findByIdempotencyKey("payment-order-15")
                .orElseThrow();
        assertEquals(15L, found.getOrderId());
        assertEquals(3L, found.getPaymentCardId());
        assertEquals(TransactionStatus.COMPLETED, found.getStatus());
    }

    @Test
    void shouldStoreAndFindSellerWalletOperation() {
        PaymentTransactionEntity transaction = new PaymentTransactionEntity(
                15L,
                7L,
                101L,
                201L,
                null,
                TransactionType.SELLER_PENDINGWALLET,
                new BigDecimal("900.00"),
                TransactionStatus.COMPLETED,
                "seller-accrual-order-15",
                null
        );

        paymentTransactionRepository.saveAndFlush(transaction);

        var walletHistory = paymentTransactionRepository
                .findAllByWalletAccountIdOrderByCreatedAtDesc(201L);
        var sellerOrderHistory = paymentTransactionRepository
                .findAllBySellerOrderIdOrderByCreatedAt(101L);

        assertEquals(1, walletHistory.size());
        assertEquals(1, sellerOrderHistory.size());
        assertEquals(TransactionType.SELLER_PENDINGWALLET, walletHistory.getFirst().getType());
        assertTrue(paymentTransactionRepository.existsBySellerOrderIdAndWalletAccountIdAndType(
                101L,
                201L,
                TransactionType.SELLER_PENDINGWALLET
        ));
    }
}
