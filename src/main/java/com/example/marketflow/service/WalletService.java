package com.example.marketflow.service;

import java.math.BigDecimal;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.marketflow.Repository.PaymentCardRepository;
import com.example.marketflow.Repository.PaymentTransactionRepository;
import com.example.marketflow.Repository.WalletAccountRepository;
import com.example.marketflow.exception.InvalidOrderStateException;
import com.example.marketflow.exception.PaymentAlreadyProcessedException;
import com.example.marketflow.exception.PaymentCardNotFoundException;
import com.example.marketflow.payment.PaymentTransactionEntity;
import com.example.marketflow.payment.TransactionStatus;
import com.example.marketflow.payment.TransactionType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletAccountRepository WAR;
    private final PaymentCardRepository PCR;
    private final PaymentTransactionRepository PTR;

    @Transactional
    public void transerMoneyfromWallerforSeller(//выполняет условный вывод денег продавца
            Long sellerId,
            Long cardId,
            BigDecimal amount,
            String idempotencyKey
    ) {
        validateRequest(sellerId, cardId, amount, idempotencyKey);

        var previous = PTR.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) {
            PaymentTransactionEntity transaction = previous.get();
            if (transaction.getType() == TransactionType.SELLER_TRANSFERMONEYFROMMAINWALLET
                    && transaction.getStatus() == TransactionStatus.COMPLETED
                    && Objects.equals(transaction.getUserId(), sellerId)
                    && Objects.equals(transaction.getPaymentCardId(), cardId)
                    && transaction.getAmount().compareTo(amount) == 0) {
                return;
            }
            throw new PaymentAlreadyProcessedException();
        }

        // Сохраняем тот же порядок блокировок «карта → кошелёк», что и при оплате и возврате.
        var card = PCR.findForPayment(cardId, sellerId)
                .orElseThrow(PaymentCardNotFoundException::new);
        var wallet = WAR.findLockedByUserId(sellerId)
                .orElseThrow(() -> new InvalidOrderStateException(
                        "Seller wallet not found for seller " + sellerId
                ));

        if (wallet.getAvailableBalance().compareTo(amount) < 0) {
            throw new InvalidOrderStateException("Недостаточно доступных средств");
        }

        wallet.withdraw(amount);
        card.credit(amount);
        PTR.saveAndFlush(new PaymentTransactionEntity(
                null,
                sellerId,
                null,
                wallet.getId(),
                null,
                TransactionType.SELLER_TRANSFERMONEYFROMMAINWALLET,
                amount,
                TransactionStatus.COMPLETED,
                idempotencyKey,
                cardId
        ));
    }

    private void validateRequest(
            Long sellerId,
            Long cardId,
            BigDecimal amount,
            String idempotencyKey
    ) {
        if (sellerId == null || sellerId < 1 || cardId == null || cardId < 1) {
            throw new IllegalArgumentException("Seller and card identifiers must be positive");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 150) {
            throw new IllegalArgumentException("Invalid withdrawal idempotency key");
        }
    }
}
