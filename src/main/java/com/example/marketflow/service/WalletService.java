package com.example.marketflow.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.marketflow.Repository.PaymentCardRepository;
import com.example.marketflow.Repository.PaymentTransactionRepository;
import com.example.marketflow.Repository.WalletAccountRepository;
import com.example.marketflow.exception.InvalidOrderStateException;
import com.example.marketflow.exception.PaymentAlreadyProcessedException;
import com.example.marketflow.exception.PaymentCardNotFoundException;
import com.example.marketflow.marketplace.MarketplaceAccess;
import com.example.marketflow.marketplace.MarketplaceException;
import com.example.marketflow.payment.PaymentTransactionEntity;
import com.example.marketflow.payment.TransactionStatus;
import com.example.marketflow.payment.TransactionType;
import com.example.marketflow.payment.WalletAccountEntity;
import com.example.marketflow.payment.WalletType;
import com.example.marketflow.messaging.MarketFlowEvent;
import com.example.marketflow.messaging.MarketFlowEventType;
import com.example.marketflow.messaging.RabbitMqNames;
import com.example.marketflow.messaging.command.MarketFlowCommand;
import com.example.marketflow.messaging.command.MarketFlowCommand.CommandType;
import com.example.marketflow.messaging.outbox.OutboxService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletAccountRepository WAR;
    private final PaymentCardRepository PCR;
    private final PaymentTransactionRepository PTR;
    private final OutboxService outboxService;
    private final MarketplaceAccess access;

    @Transactional(readOnly = true)
    public WalletView sellerWallet(Long sellerId) {
        access.checkonRights(sellerId, "SELLER");
        return WalletView.of(findSellerWallet(sellerId));
    }

    @Transactional(readOnly = true)
    public Page<WalletTransactionView> sellerTransactions(Long sellerId, int page, int size) {
        access.checkonRights(sellerId, "SELLER");
        WalletAccountEntity wallet = findSellerWallet(sellerId);
        return transactions(wallet, page, size);
    }

    @Transactional(readOnly = true)
    public WalletView platformWallet(Long ownerId) {
        access.checkonRights(ownerId, "OWNER");
        return WalletView.of(findPlatformWallet());
    }

    @Transactional(readOnly = true)
    public Page<WalletTransactionView> platformTransactions(Long ownerId, int page, int size) {
        access.checkonRights(ownerId, "OWNER");
        return transactions(findPlatformWallet(), page, size);
    }

    @Transactional
    public WithdrawalView transerMoneyfromWallerforSeller(//выполняет условный вывод денег продавца
            Long sellerId,
            Long cardId,
            BigDecimal amount,
            String idempotencyKey
    ) {
        validateRequest(sellerId, cardId, amount, idempotencyKey);
        access.checkonRights(sellerId, "SELLER");

        var previous = PTR.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) {
            PaymentTransactionEntity transaction = previous.get();
            if (transaction.getType() == TransactionType.SELLER_TRANSFERMONEYFROMMAINWALLET
                    && Objects.equals(transaction.getUserId(), sellerId)
                    && Objects.equals(transaction.getPaymentCardId(), cardId)
                    && transaction.getAmount().compareTo(amount) == 0) {
                log.debug(
                        "Existing withdrawal request returned: transactionId={}, sellerId={}, status={}",
                        transaction.getId(), sellerId, transaction.getStatus()
                );
                return WithdrawalView.of(transaction);
            }
            throw new PaymentAlreadyProcessedException();
        }

        PCR.findForPayment(cardId, sellerId)
                .orElseThrow(PaymentCardNotFoundException::new);
        var wallet = WAR.findLockedByUserId(sellerId)
                .orElseThrow(() -> new InvalidOrderStateException(
                        "Seller wallet not found for seller " + sellerId
                ));

        previous = PTR.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) {
            PaymentTransactionEntity transaction = previous.get();
            if (transaction.getType() == TransactionType.SELLER_TRANSFERMONEYFROMMAINWALLET
                    && Objects.equals(transaction.getUserId(), sellerId)
                    && Objects.equals(transaction.getPaymentCardId(), cardId)
                    && transaction.getAmount().compareTo(amount) == 0) {
                log.debug(
                        "Existing withdrawal request returned after wallet lock: transactionId={}, sellerId={}, status={}",
                        transaction.getId(), sellerId, transaction.getStatus()
                );
                return WithdrawalView.of(transaction);
            }
            throw new PaymentAlreadyProcessedException();
        }

        if (wallet.getAvailableBalance().compareTo(amount) < 0) {
            throw new InvalidOrderStateException("Недостаточно доступных средств");
        }

        wallet.reserveWithdrawal(amount);

        PaymentTransactionEntity transaction = PTR.saveAndFlush(new PaymentTransactionEntity(
                null,
                sellerId,
                null,
                wallet.getId(),
                null,
                TransactionType.SELLER_TRANSFERMONEYFROMMAINWALLET,
                amount,
                TransactionStatus.PENDING,
                idempotencyKey,
                cardId
        ));
        outboxService.saveCommand(MarketFlowCommand.withdrawal(
                transaction.getId(), sellerId, cardId, amount, Instant.now()
        ), RabbitMqNames.REQUEST_SELLER_WITHDRAWAL_COMMAND);
        log.info(
                "Withdrawal request queued: transactionId={}, sellerId={}, amount={}, status={}",
                transaction.getId(), sellerId, amount, transaction.getStatus()
        );
        return WithdrawalView.of(transaction);
    }

    @Transactional(readOnly = true)
    public WithdrawalView withdrawal(Long sellerId, Long transactionId) {
        access.checkonRights(sellerId, "SELLER");
        PaymentTransactionEntity transaction = PTR.findByIdAndUserIdAndType(
                        transactionId,
                        sellerId,
                        TransactionType.SELLER_TRANSFERMONEYFROMMAINWALLET
                )
                .orElseThrow(() -> MarketplaceException.missing("Заявка на вывод не найдена"));
        return WithdrawalView.of(transaction);
    }

    @Transactional
    public void processWithdrawal(MarketFlowCommand command) {
        if (command.commandType() != CommandType.WITHDRAWAL_REQUESTED) {
            throw new IllegalArgumentException("Получена команда другого типа");
        }

        PaymentTransactionEntity transaction = PTR.findLockedById(command.transactionId())
                .orElseThrow(() -> MarketplaceException.missing("Транзакция вывода не найдена"));
        if (transaction.getStatus() == TransactionStatus.COMPLETED
                || transaction.getStatus() == TransactionStatus.FAILED) {
            log.debug(
                    "Withdrawal command ignored because transaction is final: commandId={}, transactionId={}, status={}",
                    command.commandId(), transaction.getId(), transaction.getStatus()
            );
            return;
        }
        if (transaction.getType() != TransactionType.SELLER_TRANSFERMONEYFROMMAINWALLET
                || !Objects.equals(transaction.getUserId(), command.sellerId())
                || !Objects.equals(transaction.getPaymentCardId(), command.cardId())
                || transaction.getAmount().compareTo(command.amount()) != 0) {
            throw new PaymentAlreadyProcessedException();
        }

        var card = PCR.findForPayment(command.cardId(), command.sellerId());
        WalletAccountEntity wallet = WAR.findLockedByUserId(command.sellerId())
                .orElseThrow(() -> MarketplaceException.missing("Кошелёк продавца не найден"));
        if (!Objects.equals(transaction.getWalletAccountId(), wallet.getId())) {
            throw new PaymentAlreadyProcessedException();
        }
        if (card.isEmpty()) {
            wallet.cancelReservedWithdrawal(command.amount());
            transaction.markFailed();
            log.warn(
                    "Withdrawal failed because seller card is unavailable: commandId={}, transactionId={}, sellerId={}",
                    command.commandId(), transaction.getId(), command.sellerId()
            );
            return;
        }

        card.get().credit(command.amount());
        wallet.completeReservedWithdrawal(command.amount());
        transaction.markCompleted();
        outboxService.save(MarketFlowEvent.create(
                MarketFlowEventType.SELLER_WITHDRAWAL_COMPLETED,
                null,
                null,
                null,
                command.sellerId(),
                command.amount(),
                TransactionStatus.PENDING.name(),
                TransactionStatus.COMPLETED.name(),
                Instant.now()
        ), RabbitMqNames.SELLER_WITHDRAWAL_COMPLETED_EVENT);
        log.info(
                "Withdrawal completed: commandId={}, transactionId={}, sellerId={}, amount={}",
                command.commandId(), transaction.getId(), command.sellerId(), command.amount()
        );
    }

    @Transactional
    public void cancelUnpublishedWithdrawal(MarketFlowCommand command) {
        if (command.commandType() != CommandType.WITHDRAWAL_REQUESTED) {
            throw new IllegalArgumentException("Получена команда другого типа");
        }
        PaymentTransactionEntity transaction = PTR.findLockedById(command.transactionId())
                .orElseThrow(() -> MarketplaceException.missing("Транзакция вывода не найдена"));
        if (transaction.getStatus() == TransactionStatus.COMPLETED
                || transaction.getStatus() == TransactionStatus.FAILED) {
            log.debug(
                    "Withdrawal cancellation ignored because transaction is final: commandId={}, transactionId={}, status={}",
                    command.commandId(), transaction.getId(), transaction.getStatus()
            );
            return;
        }
        if (transaction.getType() != TransactionType.SELLER_TRANSFERMONEYFROMMAINWALLET
                || !Objects.equals(transaction.getUserId(), command.sellerId())
                || !Objects.equals(transaction.getPaymentCardId(), command.cardId())
                || transaction.getAmount().compareTo(command.amount()) != 0) {
            throw new PaymentAlreadyProcessedException();
        }
        WalletAccountEntity wallet = WAR.findLockedByUserId(command.sellerId())
                .orElseThrow(() -> MarketplaceException.missing("Кошелёк продавца не найден"));
        if (!Objects.equals(transaction.getWalletAccountId(), wallet.getId())) {
            throw new PaymentAlreadyProcessedException();
        }
        wallet.cancelReservedWithdrawal(command.amount());
        transaction.markFailed();
        log.warn(
                "Withdrawal cancelled after permanent outbox failure: commandId={}, transactionId={}, sellerId={}",
                command.commandId(), transaction.getId(), command.sellerId()
        );
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

    private WalletAccountEntity findSellerWallet(Long sellerId) {
        return WAR.findByUserId(sellerId)
                .orElseThrow(() -> MarketplaceException.missing("Кошелёк продавца не найден"));
    }

    private WalletAccountEntity findPlatformWallet() {
        return WAR.findByType(WalletType.PLATFORM)
                .orElseThrow(() -> MarketplaceException.missing("Кошелёк платформы не найден"));
    }

    private Page<WalletTransactionView> transactions(WalletAccountEntity wallet, int page, int size) {
        return PTR.findAllByWalletAccountIdOrderByCreatedAtDescIdDesc(
                wallet.getId(),
                MarketplaceAccess.page(page, size)
        ).map(WalletTransactionView::of);
    }

    public record WalletView(
            Long id,
            WalletType type,
            BigDecimal pendingBalance,
            BigDecimal availableBalance,
            BigDecimal reservedBalance,
            Instant updatedAt
    ) {
        static WalletView of(WalletAccountEntity wallet) {
            return new WalletView(
                    wallet.getId(),
                    wallet.getType(),
                    wallet.getPendingBalance(),
                    wallet.getAvailableBalance(),
                    wallet.getReservedBalance(),
                    wallet.getUpdatedAt()
            );
        }
    }

    public record WalletTransactionView(
            Long id,
            Long orderId,
            Long sellerOrderId,
            TransactionType type,
            BigDecimal amount,
            TransactionStatus status,
            Instant createdAt
    ) {
        static WalletTransactionView of(PaymentTransactionEntity transaction) {
            return new WalletTransactionView(
                    transaction.getId(),
                    transaction.getOrderId(),
                    transaction.getSellerOrderId(),
                    transaction.getType(),
                    transaction.getAmount(),
                    transaction.getStatus(),
                    transaction.getCreatedAt()
            );
        }
    }

    public record WithdrawalView(
            Long transactionId,
            Long cardId,
            BigDecimal amount,
            TransactionStatus status,
            String idempotencyKey,
            Instant createdAt
    ) {
        static WithdrawalView of(PaymentTransactionEntity transaction) {
            return new WithdrawalView(
                    transaction.getId(),
                    transaction.getPaymentCardId(),
                    transaction.getAmount(),
                    transaction.getStatus(),
                    transaction.getIdempotencyKey(),
                    transaction.getCreatedAt()
            );
        }
    }
}
