package com.example.marketflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.marketflow.Repository.PaymentCardRepository;
import com.example.marketflow.Repository.PaymentTransactionRepository;
import com.example.marketflow.Repository.WalletAccountRepository;
import com.example.marketflow.marketplace.MarketplaceAccess;
import com.example.marketflow.payment.PaymentTransactionEntity;
import com.example.marketflow.payment.TransactionStatus;
import com.example.marketflow.payment.TransactionType;
import com.example.marketflow.payment.WalletAccountEntity;
import com.example.marketflow.payment.WalletType;
import com.example.marketflow.payment_cards.PaymentCardEntity;
import com.example.marketflow.messaging.MarketFlowEvent;
import com.example.marketflow.messaging.RabbitMqNames;
import com.example.marketflow.messaging.command.MarketFlowCommand;
import com.example.marketflow.messaging.outbox.OutboxService;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock WalletAccountRepository wallets;
    @Mock PaymentCardRepository cards;
    @Mock PaymentTransactionRepository transactions;
    @Mock OutboxService outboxService;
    @Mock MarketplaceAccess access;
    @InjectMocks WalletService service;

    @Test
    void returnsSellerWalletBalances() {
        Instant updatedAt = Instant.parse("2026-09-14T10:00:00Z");
        WalletAccountEntity wallet = sellerWallet("80.00");
        wallet.addPending(money("25.00"));
        ReflectionTestUtils.setField(wallet, "updatedAt", updatedAt);
        when(wallets.findByUserId(7L)).thenReturn(Optional.of(wallet));

        var result = service.sellerWallet(7L);

        assertEquals(22L, result.id());
        assertEquals(WalletType.SELLER, result.type());
        moneyEquals("25.00", result.pendingBalance());
        moneyEquals("80.00", result.availableBalance());
        moneyEquals("0.00", result.reservedBalance());
        assertEquals(updatedAt, result.updatedAt());
    }

    @Test
    void returnsSellerWalletTransactionsAsPage() {
        WalletAccountEntity wallet = sellerWallet("80.00");
        PaymentTransactionEntity transaction = transaction(
                41L,
                101L,
                31L,
                22L,
                TransactionType.SELLER_PENDINGWALLET,
                "90.00",
                "2026-09-14T10:05:00Z"
        );
        when(wallets.findByUserId(7L)).thenReturn(Optional.of(wallet));
        when(transactions.findAllByWalletAccountIdOrderByCreatedAtDescIdDesc(
                22L, PageRequest.of(1, 10)
        )).thenReturn(new PageImpl<>(List.of(transaction), PageRequest.of(1, 10), 11));

        var result = service.sellerTransactions(7L, 1, 10);

        assertEquals(1, result.getNumber());
        assertEquals(11, result.getTotalElements());
        assertEquals(41L, result.getContent().getFirst().id());
        assertEquals(101L, result.getContent().getFirst().orderId());
        assertEquals(31L, result.getContent().getFirst().sellerOrderId());
        assertEquals(TransactionType.SELLER_PENDINGWALLET, result.getContent().getFirst().type());
        moneyEquals("90.00", result.getContent().getFirst().amount());
    }

    @Test
    void returnsPlatformWalletBalances() {
        Instant updatedAt = Instant.parse("2026-09-14T11:00:00Z");
        WalletAccountEntity wallet = platformWallet("120.00", "30.00", updatedAt);
        when(wallets.findByType(WalletType.PLATFORM)).thenReturn(Optional.of(wallet));

        var result = service.platformWallet(99L);

        assertEquals(90L, result.id());
        assertEquals(WalletType.PLATFORM, result.type());
        moneyEquals("30.00", result.pendingBalance());
        moneyEquals("120.00", result.availableBalance());
        assertEquals(updatedAt, result.updatedAt());
    }

    @Test
    void returnsPlatformCommissionTransactionsAsPage() {
        WalletAccountEntity wallet = platformWallet(
                "120.00", "30.00", Instant.parse("2026-09-14T11:00:00Z")
        );
        PaymentTransactionEntity transaction = transaction(
                51L,
                101L,
                31L,
                90L,
                TransactionType.PLATFORM_COMMISSION,
                "10.00",
                "2026-09-14T11:05:00Z"
        );
        when(wallets.findByType(WalletType.PLATFORM)).thenReturn(Optional.of(wallet));
        when(transactions.findAllByWalletAccountIdOrderByCreatedAtDescIdDesc(
                90L, PageRequest.of(0, 20)
        )).thenReturn(new PageImpl<>(List.of(transaction), PageRequest.of(0, 20), 1));

        var result = service.platformTransactions(99L, 0, 20);

        assertEquals(1, result.getTotalElements());
        assertEquals(TransactionType.PLATFORM_COMMISSION, result.getContent().getFirst().type());
        assertEquals(TransactionStatus.COMPLETED, result.getContent().getFirst().status());
        assertEquals(Instant.parse("2026-09-14T11:05:00Z"), result.getContent().getFirst().createdAt());
    }

    @Test
    void withdrawalRequestReservesMoneyAndCreatesPendingTransactionAndCommand() {
        WalletAccountEntity wallet = sellerWallet("100.00");
        PaymentCardEntity card = sellerCard("0.00");
        when(cards.findForPayment(15L, 7L)).thenReturn(Optional.of(card));
        when(wallets.findLockedByUserId(7L)).thenReturn(Optional.of(wallet));
        when(transactions.saveAndFlush(any(PaymentTransactionEntity.class))).thenAnswer(invocation -> {
            PaymentTransactionEntity transaction = invocation.getArgument(0);
            ReflectionTestUtils.setField(transaction, "id", 81L);
            return transaction;
        });

        var result = service.transerMoneyfromWallerforSeller(
                7L, 15L, money("60.00"), "withdrawal-key"
        );

        assertEquals(81L, result.transactionId());
        assertEquals(TransactionStatus.PENDING, result.status());
        moneyEquals("40.00", wallet.getAvailableBalance());
        moneyEquals("60.00", wallet.getReservedBalance());
        moneyEquals("0.00", card.getBalance());
        ArgumentCaptor<PaymentTransactionEntity> captor =
                ArgumentCaptor.forClass(PaymentTransactionEntity.class);
        verify(transactions).saveAndFlush(captor.capture());
        assertEquals(TransactionType.SELLER_TRANSFERMONEYFROMMAINWALLET, captor.getValue().getType());
        assertEquals(TransactionStatus.PENDING, captor.getValue().getStatus());

        ArgumentCaptor<MarketFlowCommand> commandCaptor =
                ArgumentCaptor.forClass(MarketFlowCommand.class);
        verify(outboxService).saveCommand(
                commandCaptor.capture(),
                eq(RabbitMqNames.REQUEST_SELLER_WITHDRAWAL_COMMAND)
        );
        assertEquals(81L, commandCaptor.getValue().transactionId());
        assertEquals(7L, commandCaptor.getValue().sellerId());
        assertEquals(15L, commandCaptor.getValue().cardId());
        moneyEquals("60.00", commandCaptor.getValue().amount());
        assertNotNull(commandCaptor.getValue().commandId());
    }

    @Test
    void withdrawalConsumerMovesMoneyAndMarksTransactionCompleted() {
        WalletAccountEntity wallet = sellerWallet("100.00");
        wallet.reserveWithdrawal(money("60.00"));
        PaymentCardEntity card = sellerCard("0.00");
        PaymentTransactionEntity transaction = pendingWithdrawal(81L, "60.00");
        when(transactions.findLockedById(81L)).thenReturn(Optional.of(transaction));
        when(cards.findForPayment(15L, 7L)).thenReturn(Optional.of(card));
        when(wallets.findLockedByUserId(7L)).thenReturn(Optional.of(wallet));

        service.processWithdrawal(MarketFlowCommand.withdrawal(
                81L, 7L, 15L, money("60.00"), Instant.parse("2026-09-15T10:00:00Z")
        ));

        moneyEquals("40.00", wallet.getAvailableBalance());
        moneyEquals("0.00", wallet.getReservedBalance());
        moneyEquals("60.00", card.getBalance());
        assertEquals(TransactionStatus.COMPLETED, transaction.getStatus());
        verify(outboxService).save(
                any(MarketFlowEvent.class),
                eq(RabbitMqNames.SELLER_WITHDRAWAL_COMPLETED_EVENT)
        );
    }

    @Test
    void repeatedWithdrawalCommandDoesNotCreditCardTwice() {
        WalletAccountEntity wallet = sellerWallet("100.00");
        wallet.reserveWithdrawal(money("60.00"));
        PaymentCardEntity card = sellerCard("0.00");
        PaymentTransactionEntity transaction = pendingWithdrawal(81L, "60.00");
        when(transactions.findLockedById(81L)).thenReturn(Optional.of(transaction));
        when(cards.findForPayment(15L, 7L)).thenReturn(Optional.of(card));
        when(wallets.findLockedByUserId(7L)).thenReturn(Optional.of(wallet));
        MarketFlowCommand command = MarketFlowCommand.withdrawal(
                81L, 7L, 15L, money("60.00"), Instant.parse("2026-09-15T10:00:00Z")
        );

        service.processWithdrawal(command);
        service.processWithdrawal(command);

        moneyEquals("60.00", card.getBalance());
        moneyEquals("40.00", wallet.getAvailableBalance());
        moneyEquals("0.00", wallet.getReservedBalance());
        verify(cards, times(1)).findForPayment(15L, 7L);
        verify(wallets, times(1)).findLockedByUserId(7L);
    }

    @Test
    void withdrawalConsumerReturnsReservationWhenCardNoLongerExists() {
        WalletAccountEntity wallet = sellerWallet("100.00");
        wallet.reserveWithdrawal(money("60.00"));
        PaymentTransactionEntity transaction = pendingWithdrawal(81L, "60.00");
        when(transactions.findLockedById(81L)).thenReturn(Optional.of(transaction));
        when(cards.findForPayment(15L, 7L)).thenReturn(Optional.empty());
        when(wallets.findLockedByUserId(7L)).thenReturn(Optional.of(wallet));

        service.processWithdrawal(MarketFlowCommand.withdrawal(
                81L, 7L, 15L, money("60.00"), Instant.parse("2026-09-15T10:00:00Z")
        ));

        moneyEquals("100.00", wallet.getAvailableBalance());
        moneyEquals("0.00", wallet.getReservedBalance());
        assertEquals(TransactionStatus.FAILED, transaction.getStatus());
        verify(outboxService, never()).save(any(MarketFlowEvent.class), any());
    }

    @Test
    void returnsOnlyWithdrawalOwnedByCurrentSeller() {
        PaymentTransactionEntity transaction = pendingWithdrawal(81L, "60.00");
        when(transactions.findByIdAndUserIdAndType(
                81L, 7L, TransactionType.SELLER_TRANSFERMONEYFROMMAINWALLET
        )).thenReturn(Optional.of(transaction));

        var result = service.withdrawal(7L, 81L);

        assertEquals(81L, result.transactionId());
        assertEquals(TransactionStatus.PENDING, result.status());
        moneyEquals("60.00", result.amount());
    }

    @Test
    void permanentlyUnpublishedWithdrawalReturnsReservationAndFailsTransaction() {
        WalletAccountEntity wallet = sellerWallet("100.00");
        wallet.reserveWithdrawal(money("60.00"));
        PaymentTransactionEntity transaction = pendingWithdrawal(81L, "60.00");
        when(transactions.findLockedById(81L)).thenReturn(Optional.of(transaction));
        when(wallets.findLockedByUserId(7L)).thenReturn(Optional.of(wallet));
        MarketFlowCommand command = MarketFlowCommand.withdrawal(
                81L, 7L, 15L, money("60.00"), Instant.parse("2026-09-15T10:00:00Z")
        );

        service.cancelUnpublishedWithdrawal(command);
        service.cancelUnpublishedWithdrawal(command);

        assertEquals(TransactionStatus.FAILED, transaction.getStatus());
        moneyEquals("100.00", wallet.getAvailableBalance());
        moneyEquals("0.00", wallet.getReservedBalance());
        verify(wallets, times(1)).findLockedByUserId(7L);
    }

    @Test
    void repeatedWithdrawalWithSameKeyDoesNotMoveMoneyAgain() {
        when(transactions.findByIdempotencyKey("withdrawal-key")).thenReturn(Optional.of(
                new PaymentTransactionEntity(
                        null,
                        7L,
                        null,
                        22L,
                        null,
                        TransactionType.SELLER_TRANSFERMONEYFROMMAINWALLET,
                        money("60.00"),
                        TransactionStatus.COMPLETED,
                        "withdrawal-key",
                        15L
                )
        ));

        service.transerMoneyfromWallerforSeller(7L, 15L, money("60.00"), "withdrawal-key");

        verifyNoInteractions(cards, wallets);
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test
    void withdrawalCannotExceedAvailableBalance() {
        WalletAccountEntity wallet = sellerWallet("20.00");
        PaymentCardEntity card = sellerCard("0.00");
        when(cards.findForPayment(15L, 7L)).thenReturn(Optional.of(card));
        when(wallets.findLockedByUserId(7L)).thenReturn(Optional.of(wallet));

        assertThrows(
                com.example.marketflow.exception.InvalidOrderStateException.class,
                () -> service.transerMoneyfromWallerforSeller(
                        7L, 15L, money("60.00"), "withdrawal-key"
                )
        );

        moneyEquals("20.00", wallet.getAvailableBalance());
        moneyEquals("0.00", card.getBalance());
        verify(transactions, never()).saveAndFlush(any());
    }

    private WalletAccountEntity sellerWallet(String available) {
        WalletAccountEntity wallet = WalletAccountEntity.seller(7L);
        ReflectionTestUtils.setField(wallet, "id", 22L);
        wallet.addPending(money(available));
        wallet.releasePending(money(available));
        return wallet;
    }

    private PaymentCardEntity sellerCard(String balance) {
        PaymentCardEntity card = new PaymentCardEntity(
                7L,
                "token",
                "**** 4242",
                money(balance)
        );
        card.setId(15L);
        return card;
    }

    private WalletAccountEntity platformWallet(String available, String pending, Instant updatedAt) {
        WalletAccountEntity wallet = WalletAccountEntity.platform();
        ReflectionTestUtils.setField(wallet, "id", 90L);
        wallet.addPending(money(available));
        wallet.releasePending(money(available));
        wallet.addPending(money(pending));
        ReflectionTestUtils.setField(wallet, "updatedAt", updatedAt);
        return wallet;
    }

    private PaymentTransactionEntity transaction(
            Long id,
            Long orderId,
            Long sellerOrderId,
            Long walletId,
            TransactionType type,
            String amount,
            String createdAt
    ) {
        PaymentTransactionEntity transaction = new PaymentTransactionEntity(
                orderId,
                7L,
                sellerOrderId,
                walletId,
                null,
                type,
                money(amount),
                TransactionStatus.COMPLETED,
                "transaction-" + id,
                null
        );
        ReflectionTestUtils.setField(transaction, "id", id);
        ReflectionTestUtils.setField(transaction, "createdAt", Instant.parse(createdAt));
        return transaction;
    }

    private PaymentTransactionEntity pendingWithdrawal(Long id, String amount) {
        PaymentTransactionEntity transaction = new PaymentTransactionEntity(
                null,
                7L,
                null,
                22L,
                null,
                TransactionType.SELLER_TRANSFERMONEYFROMMAINWALLET,
                money(amount),
                TransactionStatus.PENDING,
                "withdrawal-key",
                15L
        );
        ReflectionTestUtils.setField(transaction, "id", id);
        return transaction;
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static void moneyEquals(String expected, BigDecimal actual) {
        assertEquals(0, money(expected).compareTo(actual));
    }
}
