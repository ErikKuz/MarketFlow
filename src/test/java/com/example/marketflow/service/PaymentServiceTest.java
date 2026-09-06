package com.example.marketflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.marketflow.Order.OrderEntity;
import com.example.marketflow.Order.OrderItemEntity;
import com.example.marketflow.Order.OrderStatus;
import com.example.marketflow.Repository.OrderItemRepository;
import com.example.marketflow.Repository.OrderRepository;
import com.example.marketflow.Repository.PaymentCardRepository;
import com.example.marketflow.Repository.PaymentTransactionRepository;
import com.example.marketflow.Repository.WalletAccountRepository;
import com.example.marketflow.exception.InsufficientFundsException;
import com.example.marketflow.payment.PayOrderRequest;
import com.example.marketflow.payment.PaymentPageDto;
import com.example.marketflow.payment.PaymentStatus;
import com.example.marketflow.payment.PaymentTransactionEntity;
import com.example.marketflow.payment.TransactionStatus;
import com.example.marketflow.payment.TransactionType;
import com.example.marketflow.payment.WalletAccountEntity;
import com.example.marketflow.payment_cards.PaymentCardEntity;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentCardRepository paymentCardRepository;

    @Mock
    private PaymentTransactionRepository transactionRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private WalletAccountRepository walletAccountRepository;

    @Mock
    private com.example.marketflow.marketplace.OrderWorkflowService workflow;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-09-06T10:00:00Z"), ZoneOffset.UTC);

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void getPaymentPageReturnsOrderAndOnlyActiveBuyerCards() {
        OrderEntity order = org.mockito.Mockito.mock(OrderEntity.class);
        when(order.getId()).thenReturn(42L);
        when(order.getTotalPrice()).thenReturn(new BigDecimal("100.00"));
        when(order.getPaymentStatus()).thenReturn(PaymentStatus.NOT_PAID);
        when(orderRepository.findByIdAndBuyerId(42L, 7L))
                .thenReturn(Optional.of(order));

        PaymentCardEntity card = new PaymentCardEntity(
                7L,
                "secret-token",
                "**** **** **** 4242",
                new BigDecimal("150.00")
        );
        card.setId(15L);
        when(paymentCardRepository.findAllByUseridAndActiveTrue(7L))
                .thenReturn(List.of(card));

        PaymentPageDto result = paymentService.getPaymentPage(42L, 7L);

        assertEquals(42L, result.orderId());
        assertEquals(PaymentStatus.NOT_PAID, result.paymentStatus());
        assertEquals(1, result.cards().size());
        assertEquals(15L, result.cards().getFirst().id());
        assertTrue(result.idempotencyKey() != null && !result.idempotencyKey().isBlank());
    }

    @Test
    void payOrderReturnsExistingSuccessfulPaymentWithoutChargingAgain() {
        OrderEntity order = org.mockito.Mockito.mock(OrderEntity.class);
        when(order.getId()).thenReturn(42L);
        when(orderRepository.findForPayment(42L, 7L)).thenReturn(Optional.of(order));

        PaymentTransactionEntity transaction =
                org.mockito.Mockito.mock(PaymentTransactionEntity.class);
        when(transaction.getOrderId()).thenReturn(42L);
        when(transaction.getUserId()).thenReturn(7L);
        when(transaction.getType()).thenReturn(TransactionType.PAYMENT);
        when(transaction.getStatus()).thenReturn(TransactionStatus.COMPLETED);
        when(transactionRepository.findByIdempotencyKey("payment-key"))
                .thenReturn(Optional.of(transaction));

        Long result = paymentService.payOrder(
                42L,
                7L,
                new PayOrderRequest(15L, "payment-key")
        );

        assertEquals(42L, result);
        verify(paymentCardRepository, never()).decreaseBalance(any(), any(), any());
        verify(transactionRepository, never()).save(any(PaymentTransactionEntity.class));
        verifyNoInteractions(orderItemRepository, walletAccountRepository);
    }

    @Test
    void payOrderStopsBeforePayoutsWhenCardBalanceIsInsufficient() {
        OrderEntity order = org.mockito.Mockito.mock(OrderEntity.class);
        when(order.getId()).thenReturn(42L);
        when(order.getTotalPrice()).thenReturn(new BigDecimal("100.00"));
        when(order.getStatus()).thenReturn(OrderStatus.CREATED);
        when(order.getPaymentStatus()).thenReturn(PaymentStatus.NOT_PAID);
        when(orderRepository.findForPayment(42L, 7L)).thenReturn(Optional.of(order));
        when(transactionRepository.findByIdempotencyKey("payment-key"))
                .thenReturn(Optional.empty());
        when(paymentCardRepository.findByIdAndUseridAndActiveTrue(15L, 7L))
                .thenReturn(Optional.of(paymentCard()));
        when(paymentCardRepository.decreaseBalance(
                15L,
                7L,
                new BigDecimal("100.00")
        )).thenReturn(0);

        assertThrows(
                InsufficientFundsException.class,
                () -> paymentService.payOrder(
                        42L,
                        7L,
                        new PayOrderRequest(15L, "payment-key")
                )
        );

        verify(order).changePaymentStatus(PaymentStatus.PROCESSING);
        verify(order).changePaymentStatus(PaymentStatus.FAILED);
        verify(order, never()).changePaymentStatus(PaymentStatus.PAID);
        ArgumentCaptor<PaymentTransactionEntity> failedTransaction =
                ArgumentCaptor.forClass(PaymentTransactionEntity.class);
        verify(transactionRepository).save(failedTransaction.capture());
        assertEquals(TransactionStatus.FAILED, failedTransaction.getValue().getStatus());
        assertEquals(15L, failedTransaction.getValue().getPaymentCardId());
        verifyNoInteractions(orderItemRepository, walletAccountRepository);
    }

    @Test
    void payOrderDebitsCardAndDistributesSellerPayoutsAndCommission() {
        OrderEntity order = payableOrder();
        when(orderRepository.findForPayment(42L, 7L)).thenReturn(Optional.of(order));
        when(transactionRepository.findByIdempotencyKey("payment-key"))
                .thenReturn(Optional.empty());
        when(paymentCardRepository.findByIdAndUseridAndActiveTrue(15L, 7L))
                .thenReturn(Optional.of(paymentCard()));
        when(paymentCardRepository.decreaseBalance(
                15L,
                7L,
                new BigDecimal("100.00")
        )).thenReturn(1);

        List<OrderItemEntity> items = List.of(
                new OrderItemEntity(
                        42L, 10L, 5L, "Keyboard",
                        new BigDecimal("60.00"), 1, "keyboard.jpg"
                ),
                new OrderItemEntity(
                        42L, 11L, 6L, "Mouse",
                        new BigDecimal("40.00"), 1, "mouse.jpg"
                )
        );
        when(orderItemRepository.findAllByOrderId(42L)).thenReturn(items);
        when(walletAccountRepository.increasePendingBalance(5L, new BigDecimal("54.00")))
                .thenReturn(1);
        when(walletAccountRepository.increasePendingBalance(6L, new BigDecimal("36.00")))
                .thenReturn(1);
        when(walletAccountRepository.findOwnerAccount())
                .thenReturn(Optional.of(new WalletAccountEntity(99L)));
        when(walletAccountRepository.increasePendingBalance(99L, new BigDecimal("10.00")))
                .thenReturn(1);

        Long result = paymentService.payOrder(
                42L,
                7L,
                new PayOrderRequest(15L, "payment-key")
        );

        assertEquals(42L, result);
        verify(paymentCardRepository).decreaseBalance(
                15L,
                7L,
                new BigDecimal("100.00")
        );
        verify(walletAccountRepository).increasePendingBalance(5L, new BigDecimal("54.00"));
        verify(walletAccountRepository).increasePendingBalance(6L, new BigDecimal("36.00"));
        verify(walletAccountRepository).increasePendingBalance(99L, new BigDecimal("10.00"));
        verify(walletAccountRepository, never()).increaseBalance(any(), any());
        verify(order).changePaymentStatus(PaymentStatus.PROCESSING);
        verify(order).changePaymentStatus(PaymentStatus.PAID);
        verify(order).changeStatus(OrderStatus.CONFIRMED);

        ArgumentCaptor<PaymentTransactionEntity> captor =
                ArgumentCaptor.forClass(PaymentTransactionEntity.class);
        verify(transactionRepository, times(4)).save(captor.capture());

        List<PaymentTransactionEntity> transactions = captor.getAllValues();
        assertEquals(1, transactions.stream()
                .filter(item -> item.getType() == TransactionType.PAYMENT)
                .count());
        assertEquals(2, transactions.stream()
                .filter(item -> item.getType() == TransactionType.SELLER_PAYOUT)
                .count());
        PaymentTransactionEntity commission = transactions.stream()
                .filter(item -> item.getType() == TransactionType.PLATFORM_COMMISSION)
                .findFirst()
                .orElseThrow();
        assertEquals(99L, commission.getUserId());
        assertTrue(commission.isPending());
        assertTrue(transactions.stream()
                .filter(item -> item.getType() == TransactionType.SELLER_PAYOUT)
                .allMatch(PaymentTransactionEntity::isPending));
        assertEquals(0, new BigDecimal("10.00").compareTo(commission.getAmount()));
        PaymentTransactionEntity payment = transactions.stream()
                .filter(item -> item.getType() == TransactionType.PAYMENT)
                .findFirst()
                .orElseThrow();
        assertEquals(15L, payment.getPaymentCardId());
    }

    @Test
    void refundOrderReversesPayoutsAndReturnsMoneyToBuyerCard() {
        OrderEntity order = new OrderEntity(
                7L,
                OrderStatus.CREATED,
                new BigDecimal("100.00")
        );
        ReflectionTestUtils.setField(order, "id", 42L);
        order.changePaymentStatus(PaymentStatus.PROCESSING);
        order.changePaymentStatus(PaymentStatus.PAID);
        order.changeStatus(OrderStatus.CONFIRMED);

        PaymentTransactionEntity payment = new PaymentTransactionEntity(
                42L, 7L, TransactionType.PAYMENT,
                new BigDecimal("100.00"), TransactionStatus.COMPLETED,
                "payment-key", 15L
        );
        PaymentTransactionEntity sellerPayout = new PaymentTransactionEntity(
                42L, 5L, TransactionType.SELLER_PAYOUT,
                new BigDecimal("90.00"), TransactionStatus.COMPLETED,
                "payment-key:seller:5"
        );
        PaymentTransactionEntity commission = new PaymentTransactionEntity(
                42L, 99L, TransactionType.PLATFORM_COMMISSION,
                new BigDecimal("10.00"), TransactionStatus.COMPLETED,
                "payment-key:owner"
        );

        when(transactionRepository.findAllByOrderId(42L))
                .thenReturn(List.of(payment, sellerPayout, commission));
        when(walletAccountRepository.decreaseBalance(5L, new BigDecimal("90.00")))
                .thenReturn(1);
        when(walletAccountRepository.decreaseBalance(99L, new BigDecimal("10.00")))
                .thenReturn(1);
        when(paymentCardRepository.increaseBalance(15L, 7L, new BigDecimal("100.00")))
                .thenReturn(1);

        paymentService.refundOrder(order);

        assertEquals(PaymentStatus.REFUNDED, order.getPaymentStatus());
        assertEquals(TransactionStatus.REFUNDED, payment.getStatus());
        assertEquals(TransactionStatus.REFUNDED, sellerPayout.getStatus());
        assertEquals(TransactionStatus.REFUNDED, commission.getStatus());
        verify(paymentCardRepository).increaseBalance(
                15L, 7L, new BigDecimal("100.00")
        );

        ArgumentCaptor<PaymentTransactionEntity> reversalCaptor =
                ArgumentCaptor.forClass(PaymentTransactionEntity.class);
        verify(transactionRepository, times(3)).save(reversalCaptor.capture());
        assertEquals(1, reversalCaptor.getAllValues().stream()
                .filter(item -> item.getType() == TransactionType.REFUND)
                .count());
        assertEquals(1, reversalCaptor.getAllValues().stream()
                .filter(item -> item.getType() == TransactionType.SELLER_PAYOUT_REVERSAL)
                .count());
        assertEquals(1, reversalCaptor.getAllValues().stream()
                .filter(item -> item.getType() == TransactionType.PLATFORM_COMMISSION_REVERSAL)
                .count());
    }

    private OrderEntity payableOrder() {
        OrderEntity order = org.mockito.Mockito.mock(OrderEntity.class);
        when(order.getId()).thenReturn(42L);
        when(order.getTotalPrice()).thenReturn(new BigDecimal("100.00"));
        when(order.getStatus()).thenReturn(OrderStatus.CREATED);
        when(order.getPaymentStatus()).thenReturn(PaymentStatus.NOT_PAID);
        when(order.getCommissionRate()).thenReturn(new BigDecimal("0.10"));
        return order;
    }

    private PaymentCardEntity paymentCard() {
        PaymentCardEntity card = new PaymentCardEntity(
                7L,
                "secret-token",
                "**** **** **** 4242",
                new BigDecimal("150.00")
        );
        card.setId(15L);
        return card;
    }
}
