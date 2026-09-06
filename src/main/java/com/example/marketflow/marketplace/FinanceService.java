package com.example.marketflow.marketplace;

import java.time.Clock;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.marketflow.Repository.*;
import com.example.marketflow.payment.*;
import static com.example.marketflow.marketplace.MarketplaceViews.*;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class FinanceService {
    private final WalletAccountRepository wallets;
    private final PaymentTransactionRepository transactions;
    private final PaymentCardRepository cards;
    private final WithdrawalRequestRepository withdrawals;
    private final UserRepository users;
    private final MarketplaceAccess access;
    private final OrderWorkflowService workflow;
    private final Clock clock;

    @Transactional(readOnly = true)
    public WalletView wallet(Long userId) {
        access.require(userId, "SELLER", "OWNER");
        var w = wallets.findByUserId(userId).orElseThrow(() -> MarketplaceException.missing("Wallet not found"));
        return new WalletView(w.getBalance(), w.getPendingBalance(), w.getWithdrawalReserved());
    }

    @Transactional(readOnly = true)
    public PageView<PaymentEntry> history(Long userId, int page, int size) {
        access.require(userId);
        return PageView.of(transactions.findAllByUserIdOrderByCreatedAtDescIdDesc(userId, MarketplaceAccess.page(page, size)).map(PaymentEntry::of));
    }

    @Transactional
    public WithdrawalView requestWithdrawal(Long sellerId, Long cardId, BigDecimal amount, String key) {
        access.require(sellerId, "SELLER");
        users.findLocked(sellerId).orElseThrow();
        var previous = withdrawals.findBySellerIdAndRequestKey(sellerId, key);
        if (previous.isPresent()) {
            if (!previous.get().getCardId().equals(cardId) || previous.get().getAmount().compareTo(amount) != 0)
                throw MarketplaceException.conflict("Withdrawal key was already used for another request");
            return WithdrawalView.of(previous.get());
        }
        cards.findByIdAndUseridAndActiveTrue(cardId, sellerId).orElseThrow(() -> MarketplaceException.missing("Active card not found"));
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2)
            throw MarketplaceException.conflict("Withdrawal amount must be positive with at most two decimal places");
        if (wallets.reserveWithdrawal(sellerId, amount) != 1)
            throw MarketplaceException.conflict("Insufficient available balance");
        var request = withdrawals.save(new WithdrawalRequestEntity(sellerId, cardId, amount, key, clock.instant()));
        workflow.record(sellerId, null, sellerId, "WITHDRAWAL_REQUESTED", null, "PENDING", "Request " + request.getId());
        return WithdrawalView.of(request);
    }

    @Transactional(readOnly = true)
    public PageView<WithdrawalView> sellerWithdrawals(Long sellerId, int page, int size) {
        access.require(sellerId, "SELLER");
        return PageView.of(withdrawals.findAllBySellerIdOrderByRequestedAtDescIdDesc(sellerId, MarketplaceAccess.page(page, size)).map(WithdrawalView::of));
    }

    @Transactional(readOnly = true)
    public PageView<WithdrawalView> allWithdrawals(Long ownerId, int page, int size) {
        access.require(ownerId, "OWNER");
        return PageView.of(withdrawals.findAllByOrderByRequestedAtDescIdDesc(MarketplaceAccess.page(page, size)).map(WithdrawalView::of));
    }

    @Transactional
    public void decideWithdrawal(Long ownerId, Long id, boolean approved, String reason) {
        access.require(ownerId, "OWNER");
        var request = withdrawals.lockById(id).orElseThrow(() -> MarketplaceException.missing("Withdrawal not found"));
        if (request.getStatus() != WithdrawalRequestEntity.Status.PENDING) {
            if ((request.getStatus() == WithdrawalRequestEntity.Status.APPROVED) == approved) return;
            throw MarketplaceException.conflict("Withdrawal already decided");
        }
        if (approved) {
            cards.findByIdAndUseridAndActiveTrue(request.getCardId(), request.getSellerId())
                    .orElseThrow(() -> MarketplaceException.conflict("Withdrawal card is no longer active"));
            if (wallets.completeWithdrawal(request.getSellerId(), request.getAmount()) != 1
                    || cards.increaseBalance(request.getCardId(), request.getSellerId(), request.getAmount()) != 1)
                throw MarketplaceException.conflict("Unable to complete withdrawal");
            transactions.save(new PaymentTransactionEntity(null, request.getSellerId(), TransactionType.WITHDRAWAL,
                    request.getAmount(), TransactionStatus.COMPLETED, "withdrawal:" + id, request.getCardId()));
        } else if (wallets.rejectWithdrawal(request.getSellerId(), request.getAmount()) != 1) {
            throw MarketplaceException.conflict("Unable to release reserved funds");
        }
        request.decide(approved, reason, clock.instant());
        workflow.record(ownerId, null, request.getSellerId(), "WITHDRAWAL_DECIDED", "PENDING", request.getStatus().name(), reason);
    }
}
