package com.example.marketflow.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.example.marketflow.Order.*;
import com.example.marketflow.Repository.*;
import com.example.marketflow.exception.*;
import com.example.marketflow.marketplace.SellerOrderEntity;
import com.example.marketflow.marketplace.SellerOrderRepository;
import com.example.marketflow.marketplace.OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS;
import com.example.marketflow.payment.*;
import com.example.marketflow.payment_cards.PaymentCardEntity;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock OrderRepository orders;
    @Mock PaymentCardRepository cards;
    @Mock PaymentTransactionRepository transactions;
    @Mock OrderItemRepository items;
    @Mock ProductRepository products;
    @Mock SellerOrderRepository sellerOrders;
    @Mock WalletAccountRepository wallets;
    @InjectMocks PaymentService service;
    private final PayOrderRequest request = new PayOrderRequest(15L, "payment-key");

    private OrderEntity order() {
        var order = new OrderEntity(7L, OrderStatus.CREATED, new BigDecimal("100.00"));
        ReflectionTestUtils.setField(order, "id", 42L);
        when(orders.findForPayment(42L, 7L)).thenReturn(Optional.of(order));
        return order;
    }
    private PaymentCardEntity card(String balance) {
        var card = new PaymentCardEntity(7L, "token", "**** 4242", new BigDecimal(balance));
        card.setId(15L);
        when(cards.findForPayment(15L, 7L)).thenReturn(Optional.of(card));
        return card;
    }
    @Test
    void paymentPageExposesOnlySafeCardDto() {
        var order = new OrderEntity(7L, OrderStatus.CREATED, new BigDecimal("100.00"));
        ReflectionTestUtils.setField(order, "id", 42L);
        when(orders.findByIdAndBuyerId(42L, 7L)).thenReturn(Optional.of(order));
        var card = new PaymentCardEntity(7L, "secret-token", "**** 4242", new BigDecimal("150.00"));
        card.setId(15L);
        when(cards.findAllByUseridAndActiveTrue(7L)).thenReturn(List.of(card));
        var result = service.getPaymentPage(42L, 7L);
        assertEquals(42L, result.orderId());
        assertEquals(15L, result.cards().getFirst().id());
        assertFalse(result.idempotencyKey().isBlank());
    }
    @Test
    void successfulPaymentDebitsStockAndAccruesSellerAndPlatformFunds() {
        var order = order();
        var card = card("150.00");
        when(items.findAllByOrderId(42L)).thenReturn(List.of(
                new OrderItemEntity(42L, 11L, 6L, "Mouse", new BigDecimal("40.00"), 1, "mouse.jpg"),
                new OrderItemEntity(42L, 10L, 5L, "Keyboard", new BigDecimal("60.00"), 1, "key.jpg")));
        when(products.decreaseStock(any(), any())).thenReturn(1);
        var firstPart = sellerPart(501L, 5L, "60.00");
        var secondPart = sellerPart(502L, 6L, "40.00");
        when(sellerOrders.findAllByOrderIdOrderBySellerId(42L))
                .thenReturn(List.of(firstPart, secondPart));
        var platformWallet = wallet(900L, null, WalletType.PLATFORM);
        var firstSellerWallet = wallet(901L, 5L, WalletType.SELLER);
        var secondSellerWallet = wallet(902L, 6L, WalletType.SELLER);
        when(wallets.findLockedByType(WalletType.PLATFORM)).thenReturn(Optional.of(platformWallet));
        when(wallets.findLockedByUserId(5L)).thenReturn(Optional.of(firstSellerWallet));
        when(wallets.findLockedByUserId(6L)).thenReturn(Optional.of(secondSellerWallet));
        when(transactions.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(42L, service.payOrder(42L, 7L, request));
        assertEquals(new BigDecimal("50.00"), card.getBalance());
        assertEquals(PaymentStatus.PAID, order.getPaymentStatus());
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        var sequence = inOrder(products);
        sequence.verify(products).decreaseStock(10L, 1);
        sequence.verify(products).decreaseStock(11L, 1);
        var captor = ArgumentCaptor.forClass(PaymentTransactionEntity.class);
        verify(transactions).saveAndFlush(captor.capture());
        assertEquals(TransactionType.PAYMENT, captor.getValue().getType());
        assertEquals(TransactionStatus.COMPLETED, captor.getValue().getStatus());
        assertEquals(15L, captor.getValue().getPaymentCardId());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals(OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS.PENDINGWALLET,
                firstPart.getSettlementStatus());
        assertEquals(OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS.PENDINGWALLET,
                secondPart.getSettlementStatus());
        assertEquals(0, firstSellerWallet.getPendingBalance().compareTo(new BigDecimal("54.00")));
        assertEquals(0, secondSellerWallet.getPendingBalance().compareTo(new BigDecimal("36.00")));
        assertEquals(0, platformWallet.getPendingBalance().compareTo(new BigDecimal("10.00")));
        verify(transactions, times(4)).save(any(PaymentTransactionEntity.class));
    }
    @Test
    void insufficientBalanceRecordsFailedAttemptWithoutChangingStockOrCard() {
        var order = order();
        var card = card("20.00");
        assertThrows(InsufficientFundsException.class, () -> service.payOrder(42L, 7L, request));
        assertEquals(PaymentStatus.FAILED, order.getPaymentStatus());
        assertEquals(OrderStatus.CREATED, order.getStatus());
        assertEquals(new BigDecimal("20.00"), card.getBalance());
        verifyNoInteractions(items, products);
        var captor = ArgumentCaptor.forClass(PaymentTransactionEntity.class);
        verify(transactions).saveAndFlush(captor.capture());
        assertEquals(TransactionStatus.FAILED, captor.getValue().getStatus());
    }
    @Test
    void completedKeyReturnsSameOrderWithoutSecondDebit() {
        order();
        when(transactions.findByIdempotencyKey("payment-key")).thenReturn(Optional.of(
                new PaymentTransactionEntity(42L, 7L, TransactionType.PAYMENT, new BigDecimal("100.00"),
                        TransactionStatus.COMPLETED, "payment-key", 15L)));
        assertEquals(42L, service.payOrder(42L, 7L, request));
        verifyNoInteractions(cards, items, products);
        verify(transactions, never()).saveAndFlush(any());
    }
    @Test
    void sameKeyCannotBeUsedWithAnotherCard() {
        order();
        when(transactions.findByIdempotencyKey("payment-key")).thenReturn(Optional.of(
                new PaymentTransactionEntity(42L, 7L, TransactionType.PAYMENT, new BigDecimal("100.00"),
                        TransactionStatus.COMPLETED, "payment-key", 99L)));
        assertThrows(PaymentAlreadyProcessedException.class, () -> service.payOrder(42L, 7L, request));
        verifyNoInteractions(cards, items, products);
    }
    @Test
    void alreadyPaidOrderCannotBePaidWithNewKey() {
        var order = order();
        order.changePaymentStatus(PaymentStatus.PROCESSING);
        order.changePaymentStatus(PaymentStatus.PAID);
        order.changeStatus(OrderStatus.CONFIRMED);
        assertThrows(InvalidOrderStateException.class, () -> service.payOrder(42L, 7L, request));
        verifyNoInteractions(cards, items, products);
    }
    @Test
    void cardMustBelongToBuyerAndBeActive() {
        order();
        assertThrows(PaymentCardNotFoundException.class, () -> service.payOrder(42L, 7L, request));
        verifyNoInteractions(items, products);
    }

    private SellerOrderEntity sellerPart(Long id, Long sellerId, String total) {
        var part = new SellerOrderEntity(42L, sellerId, new BigDecimal(total), java.time.Instant.now());
        ReflectionTestUtils.setField(part, "id", id);
        return part;
    }

    private WalletAccountEntity wallet(Long id, Long userId, WalletType type) {
        WalletAccountEntity wallet = type == WalletType.PLATFORM
                ? WalletAccountEntity.platform()
                : WalletAccountEntity.seller(userId);
        ReflectionTestUtils.setField(wallet, "id", id);
        return wallet;
    }
}
