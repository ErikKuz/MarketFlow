package com.example.marketflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.marketflow.Repository.PaymentCardRepository;
import com.example.marketflow.Repository.PaymentTransactionRepository;
import com.example.marketflow.Repository.WalletAccountRepository;
import com.example.marketflow.payment.PaymentTransactionEntity;
import com.example.marketflow.payment.TransactionStatus;
import com.example.marketflow.payment.TransactionType;
import com.example.marketflow.payment.WalletAccountEntity;
import com.example.marketflow.payment_cards.PaymentCardEntity;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock WalletAccountRepository wallets;
    @Mock PaymentCardRepository cards;
    @Mock PaymentTransactionRepository transactions;
    @InjectMocks WalletService service;

    @Test
    void withdrawalMovesAvailableFundsToSellerCardAndWritesLedger() {
        WalletAccountEntity wallet = sellerWallet("100.00");
        PaymentCardEntity card = sellerCard("0.00");
        when(cards.findForPayment(15L, 7L)).thenReturn(Optional.of(card));
        when(wallets.findLockedByUserId(7L)).thenReturn(Optional.of(wallet));

        service.transerMoneyfromWallerforSeller(7L, 15L, money("60.00"), "withdrawal-key");

        moneyEquals("40.00", wallet.getAvailableBalance());
        moneyEquals("60.00", card.getBalance());
        ArgumentCaptor<PaymentTransactionEntity> captor =
                ArgumentCaptor.forClass(PaymentTransactionEntity.class);
        verify(transactions).saveAndFlush(captor.capture());
        assertEquals(TransactionType.SELLER_TRANSFERMONEYFROMMAINWALLET, captor.getValue().getType());
        assertEquals(TransactionStatus.COMPLETED, captor.getValue().getStatus());
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

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static void moneyEquals(String expected, BigDecimal actual) {
        assertEquals(0, money(expected).compareTo(actual));
    }
}
