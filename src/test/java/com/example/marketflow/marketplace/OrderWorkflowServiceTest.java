package com.example.marketflow.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.marketflow.Order.OrderEntity;
import com.example.marketflow.Order.OrderStatus;
import com.example.marketflow.Repository.OrderItemRepository;
import com.example.marketflow.Repository.OrderRepository;
import com.example.marketflow.Repository.PaymentTransactionRepository;
import com.example.marketflow.Repository.WalletAccountRepository;
import com.example.marketflow.messaging.MarketFlowEvent;
import com.example.marketflow.messaging.MarketFlowEventType;
import com.example.marketflow.messaging.RabbitMqNames;
import com.example.marketflow.messaging.command.MarketFlowCommand;
import com.example.marketflow.messaging.outbox.OutboxService;
import com.example.marketflow.payment.PaymentTransactionEntity;
import com.example.marketflow.payment.PaymentStatus;
import com.example.marketflow.payment.TransactionStatus;
import com.example.marketflow.payment.TransactionType;
import com.example.marketflow.payment.WalletAccountEntity;
import com.example.marketflow.payment.WalletType;

@ExtendWith(MockitoExtension.class)
class OrderWorkflowServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-15T10:00:00Z");

    @Mock SellerOrderRepository sellerOrders;
    @Mock OrderRepository orders;
    @Mock OrderItemRepository orderItems;
    @Mock WalletAccountRepository wallets;
    @Mock PaymentTransactionRepository transactions;
    @Mock MarketplaceAccess access;
    @Mock Clock clock;
    @Mock OutboxService outboxService;
    @InjectMocks OrderWorkflowService service;

    @Test
    void receivingLastPartQueuesReleaseButDoesNotCompleteOrderBeforeMoneyMoves() {
        OrderEntity order = new OrderEntity(5L, OrderStatus.CREATED, money("100.00"));
        ReflectionTestUtils.setField(order, "id", 42L);
        ReflectionTestUtils.setField(order, "status", OrderStatus.SELLERSENDWORKANDSEND);
        ReflectionTestUtils.setField(order, "paymentStatus", PaymentStatus.PAID);
        SellerOrderEntity part = new SellerOrderEntity(42L, 7L, money("100.00"), NOW);
        ReflectionTestUtils.setField(part, "id", 31L);
        part.recordSettlement(money("0.1000"), money("10.00"), money("90.00"));
        part.transition(OBSERFFORSENDBYSELLERPRODUCTSTATUS.PROCESSING, NOW);
        part.transition(OBSERFFORSENDBYSELLERPRODUCTSTATUS.SELLERSENDPRODUCT, NOW);

        when(orders.findForPayment(42L, 5L)).thenReturn(Optional.of(order));
        when(sellerOrders.findById(31L)).thenReturn(Optional.of(part));
        when(sellerOrders.findAllByOrderIdOrderBySellerId(42L))
                .thenReturn(java.util.List.of(part));
        when(clock.instant()).thenReturn(NOW);

        service.ConfirmThatUSERGETPRODUCTBySellerID(5L, 42L, 31L);

        assertEquals(OBSERFFORSENDBYSELLERPRODUCTSTATUS.USERGETPRODUCT, part.getStatus());
        assertEquals(OrderStatus.SELLERSENDWORKANDSEND, order.getStatus());
        assertEquals(NOW, order.getDeliveredAt());
        verify(outboxService).saveCommand(
                any(MarketFlowCommand.class),
                eq(RabbitMqNames.RELEASE_SELLER_FUNDS_COMMAND)
        );
    }

    @Test
    void repeatedReceiptCanRequeuePendingFundsRelease() {
        OrderEntity order = new OrderEntity(5L, OrderStatus.CREATED, money("100.00"));
        ReflectionTestUtils.setField(order, "id", 42L);
        ReflectionTestUtils.setField(order, "status", OrderStatus.SELLERSENDWORKANDSEND);
        ReflectionTestUtils.setField(order, "paymentStatus", PaymentStatus.PAID);
        SellerOrderEntity part = receivedPart();
        when(orders.findForPayment(42L, 5L)).thenReturn(Optional.of(order));
        when(sellerOrders.findById(31L)).thenReturn(Optional.of(part));
        when(clock.instant()).thenReturn(NOW);

        service.ConfirmThatUSERGETPRODUCTBySellerID(5L, 42L, 31L);

        verify(outboxService).saveCommand(
                any(MarketFlowCommand.class),
                eq(RabbitMqNames.RELEASE_SELLER_FUNDS_COMMAND)
        );
        assertEquals(OrderStatus.SELLERSENDWORKANDSEND, order.getStatus());
    }

    @Test
    void releaseCommandMovesSellerAndPlatformMoneyAndIsIdempotent() {
        SellerOrderEntity part = receivedPart();
        WalletAccountEntity sellerWallet = sellerWallet();
        WalletAccountEntity platformWallet = platformWallet();
        OrderEntity order = new OrderEntity(5L, OrderStatus.CREATED, money("100.00"));
        ReflectionTestUtils.setField(order, "id", 42L);
        ReflectionTestUtils.setField(order, "status", OrderStatus.SELLERSENDWORKANDSEND);

        when(sellerOrders.findLockedById(31L)).thenReturn(Optional.of(part));
        when(orders.findLocked(42L)).thenReturn(Optional.of(order));
        when(sellerOrders.findAllByOrderIdOrderBySellerId(42L))
                .thenReturn(java.util.List.of(part));
        when(wallets.findLockedByType(WalletType.PLATFORM)).thenReturn(Optional.of(platformWallet));
        when(wallets.findLockedByUserId(7L)).thenReturn(Optional.of(sellerWallet));
        when(transactions.findBySellerOrderIdAndWalletAccountIdAndType(
                31L, 22L, TransactionType.SELLER_PENDINGWALLET
        )).thenReturn(Optional.of(ledger(101L, TransactionType.SELLER_PENDINGWALLET, "90.00")));
        when(transactions.findBySellerOrderIdAndWalletAccountIdAndType(
                31L, 90L, TransactionType.PLATFORM_COMMISSION
        )).thenReturn(Optional.of(ledger(102L, TransactionType.PLATFORM_COMMISSION, "10.00")));
        when(clock.instant()).thenReturn(NOW);
        MarketFlowCommand command = MarketFlowCommand.releaseSellerFunds(
                42L, 31L, 7L, money("90.00"), NOW
        );

        service.releaseSellerFunds(command);
        service.releaseSellerFunds(command);

        moneyEquals("0.00", sellerWallet.getPendingBalance());
        moneyEquals("90.00", sellerWallet.getAvailableBalance());
        moneyEquals("0.00", platformWallet.getPendingBalance());
        moneyEquals("10.00", platformWallet.getAvailableBalance());
        assertEquals(
                OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS.MAINWALLET,
                part.getSettlementStatus()
        );
        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        verify(transactions, times(2)).save(any(PaymentTransactionEntity.class));
        verify(wallets, times(1)).findLockedByUserId(7L);

        ArgumentCaptor<MarketFlowEvent> eventCaptor = ArgumentCaptor.forClass(MarketFlowEvent.class);
        verify(outboxService).save(
                eventCaptor.capture(),
                eq(RabbitMqNames.SELLER_FUNDS_RELEASED_EVENT)
        );
        assertEquals(MarketFlowEventType.SELLER_MONEY_AVAILABLE, eventCaptor.getValue().eventType());
        verify(outboxService).save(
                any(MarketFlowEvent.class),
                eq(RabbitMqNames.ORDER_COMPLETED_EVENT)
        );
    }

    private SellerOrderEntity receivedPart() {
        SellerOrderEntity part = new SellerOrderEntity(42L, 7L, money("100.00"), NOW);
        ReflectionTestUtils.setField(part, "id", 31L);
        part.recordSettlement(money("0.1000"), money("10.00"), money("90.00"));
        part.transition(OBSERFFORSENDBYSELLERPRODUCTSTATUS.PROCESSING, NOW);
        part.transition(OBSERFFORSENDBYSELLERPRODUCTSTATUS.SELLERSENDPRODUCT, NOW);
        part.transition(OBSERFFORSENDBYSELLERPRODUCTSTATUS.USERGETPRODUCT, NOW);
        return part;
    }

    private WalletAccountEntity sellerWallet() {
        WalletAccountEntity wallet = WalletAccountEntity.seller(7L);
        ReflectionTestUtils.setField(wallet, "id", 22L);
        wallet.addPending(money("90.00"));
        return wallet;
    }

    private WalletAccountEntity platformWallet() {
        WalletAccountEntity wallet = WalletAccountEntity.platform();
        ReflectionTestUtils.setField(wallet, "id", 90L);
        wallet.addPending(money("10.00"));
        return wallet;
    }

    private PaymentTransactionEntity ledger(Long id, TransactionType type, String amount) {
        PaymentTransactionEntity transaction = new PaymentTransactionEntity(
                42L,
                type == TransactionType.SELLER_PENDINGWALLET ? 7L : null,
                31L,
                type == TransactionType.SELLER_PENDINGWALLET ? 22L : 90L,
                null,
                type,
                money(amount),
                TransactionStatus.COMPLETED,
                "ledger-" + id,
                null
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
