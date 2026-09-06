package com.example.marketflow.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.marketflow.Order.OrderEntity;
import com.example.marketflow.Order.OrderItemEntity;
import com.example.marketflow.Order.OrderStatus;
import com.example.marketflow.Repository.OrderItemRepository;
import com.example.marketflow.Repository.OrderRepository;
import com.example.marketflow.Repository.PaymentCardRepository;
import com.example.marketflow.Repository.PaymentTransactionRepository;
import com.example.marketflow.Repository.WalletAccountRepository;
import com.example.marketflow.exception.InsufficientFundsException;
import com.example.marketflow.exception.InvalidOrderStateException;
import com.example.marketflow.exception.OrderNotFoundException;
import com.example.marketflow.exception.OwnerWalletAccountNotFoundException;
import com.example.marketflow.exception.PaymentAlreadyProcessedException;
import com.example.marketflow.exception.PaymentCardNotFoundException;
import com.example.marketflow.exception.RefundNotAvailableException;
import com.example.marketflow.exception.WalletAccountNotFoundException;
import com.example.marketflow.payment.PayOrderRequest;
import com.example.marketflow.payment.PaymentMapper;
import com.example.marketflow.payment.PaymentPageDto;
import com.example.marketflow.payment.PaymentStatus;
import com.example.marketflow.payment.PaymentTransactionEntity;
import com.example.marketflow.payment.TransactionStatus;
import com.example.marketflow.payment.TransactionType;
import com.example.marketflow.payment.WalletAccountEntity;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final OrderRepository OR;
    private final PaymentCardRepository PCR;
    private final PaymentTransactionRepository PTR;
    private final OrderItemRepository OIR;
    private final WalletAccountRepository WAR;
    private final com.example.marketflow.marketplace.OrderWorkflowService workflow;
    private final java.time.Clock clock;

    @Transactional(readOnly = true)
    public PaymentPageDto getPaymentPage(Long orderId, Long buyerId) {
        OrderEntity order = OR.findByIdAndBuyerId(orderId, buyerId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        return new PaymentPageDto(
                order.getId(),
                order.getTotalPrice(),
                order.getPaymentStatus(),
                PCR.findAllByUseridAndActiveTrue(buyerId)
                        .stream()
                        .map(PaymentMapper::convert)
                        .toList(),
                UUID.randomUUID().toString()
        );
    }

    @Transactional(noRollbackFor = InsufficientFundsException.class)
    public Long payOrder(Long orderId, Long buyerId, PayOrderRequest request) {
        OrderEntity order = OR.findForPayment(orderId, buyerId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        Optional<PaymentTransactionEntity> existingTransaction =
                PTR.findByIdempotencyKey(
                        request.idempotencyKey()
                );

        if (existingTransaction.isPresent()) {
            PaymentTransactionEntity transaction = existingTransaction.get();

            boolean samePayment =
                    transaction.getOrderId().equals(order.getId())
                    && transaction.getUserId().equals(buyerId)
                    && transaction.getType() == TransactionType.PAYMENT
                    && transaction.getStatus() == TransactionStatus.COMPLETED;

            if (samePayment) {
                return order.getId();
            }

            throw new PaymentAlreadyProcessedException();
        }

        if (order.getStatus() != OrderStatus.CREATED) {
            throw new InvalidOrderStateException(
                    "Only an order in CREATED status can be paid"
            );
        }

        if (order.paymentExpired(clock.instant())) {
            throw new InvalidOrderStateException("The order payment deadline has expired");
        }

        if (order.getPaymentStatus() == PaymentStatus.NOT_PAID
                || order.getPaymentStatus() == PaymentStatus.FAILED) {

            PCR.findByIdAndUseridAndActiveTrue(
                    request.cardId(),
                    buyerId
            ).orElseThrow(PaymentCardNotFoundException::new);

            order.changePaymentStatus(
                    PaymentStatus.PROCESSING
            );

            int updatedRows = PCR.decreaseBalance(
                    request.cardId(),
                    buyerId,
                    order.getTotalPrice()
            );

            if (updatedRows != 1) {
                order.changePaymentStatus(PaymentStatus.FAILED);
                workflow.record(buyerId, orderId, null, "PAYMENT_FAILED", "PROCESSING", "FAILED", "Insufficient funds");

                PTR.save(
                        new PaymentTransactionEntity(
                                order.getId(),
                                buyerId,
                                TransactionType.PAYMENT,
                                order.getTotalPrice(),
                                TransactionStatus.FAILED,
                                request.idempotencyKey(),
                                request.cardId()
                        )
                );

                throw new InsufficientFundsException();
            }

            PTR.save(
                    new PaymentTransactionEntity(
                            order.getId(),
                            buyerId,
                            TransactionType.PAYMENT,
                            order.getTotalPrice(),
                            TransactionStatus.COMPLETED,
                            request.idempotencyKey(),
                            request.cardId()
                    )
            );

            List<OrderItemEntity> orderItems =
                    OIR.findAllByOrderId(order.getId());

            Map<Long, BigDecimal> totalsBySeller = orderItems.stream()
                    .collect(Collectors.toMap(
                            OrderItemEntity::getSellerId,
                            OrderItemEntity::getTotalPrice,
                            BigDecimal::add
                    ));

            BigDecimal totalCommission = BigDecimal.ZERO;

            for (Entry<Long, BigDecimal> entry : totalsBySeller.entrySet()) {

                BigDecimal commission = entry.getValue()
                        .multiply(order.getCommissionRate())
                        .setScale(2, RoundingMode.HALF_UP);

                BigDecimal sellerPayout = entry.getValue().subtract(commission);

                totalCommission = totalCommission.add(commission);

                if (sellerPayout.signum() > 0) {
                int updatedWalletRows = WAR.increasePendingBalance(
                        entry.getKey(),
                        sellerPayout
                );

                if (updatedWalletRows != 1) {
                    throw new WalletAccountNotFoundException(entry.getKey());
                }

                PTR.save(
                        new PaymentTransactionEntity(
                                order.getId(),
                                entry.getKey(),
                                TransactionType.SELLER_PAYOUT,
                                sellerPayout,
                                TransactionStatus.COMPLETED,
                                request.idempotencyKey()
                                        + ":seller:"
                                        + entry.getKey()
                        ).hold()
                );
                }
            }

            WalletAccountEntity ownerAccount = WAR.findOwnerAccount()
                    .orElseThrow(OwnerWalletAccountNotFoundException::new);

            if (totalCommission.signum() > 0) {
                int updatedOwnerRows = WAR.increasePendingBalance(
                        ownerAccount.getUserId(),
                        totalCommission
                );

                if (updatedOwnerRows != 1) {
                    throw new WalletAccountNotFoundException(
                            ownerAccount.getUserId()
                    );
                }

                PTR.save(
                        new PaymentTransactionEntity(
                                order.getId(),
                                ownerAccount.getUserId(),
                                TransactionType.PLATFORM_COMMISSION,
                                totalCommission,
                                TransactionStatus.COMPLETED,
                                request.idempotencyKey() + ":owner"
                        ).hold()
                );
            }

            order.changePaymentStatus(
                    PaymentStatus.PAID
            );
            order.changeStatus(OrderStatus.CONFIRMED);
            workflow.record(buyerId, orderId, null, "ORDER_PAID", "CREATED", "CONFIRMED", "Funds held until return deadline");

            return order.getId();
        }

        throw new PaymentAlreadyProcessedException();
    }

    @Transactional
    public void refundOrder(OrderEntity order) {
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new RefundNotAvailableException(
                    "Only a paid order can be refunded"
            );
        }

        List<PaymentTransactionEntity> transactions =
                PTR.findAllByOrderId(order.getId());

        PaymentTransactionEntity payment = transactions.stream()
                .filter(transaction -> transaction.getType() == TransactionType.PAYMENT)
                .filter(transaction -> transaction.getStatus() == TransactionStatus.COMPLETED)
                .findFirst()
                .orElseThrow(() -> new RefundNotAvailableException(
                        "Completed payment transaction was not found"
                ));

        if (payment.getPaymentCardId() == null) {
            throw new RefundNotAvailableException(
                    "The payment card is not recorded for this order"
            );
        }

        List<PaymentTransactionEntity> sellerPayouts = transactions.stream()
                .filter(transaction -> transaction.getType() == TransactionType.SELLER_PAYOUT)
                .filter(transaction -> transaction.getStatus() == TransactionStatus.COMPLETED)
                .toList();

        List<PaymentTransactionEntity> commissions = transactions.stream()
                .filter(transaction -> transaction.getType() == TransactionType.PLATFORM_COMMISSION)
                .filter(transaction -> transaction.getStatus() == TransactionStatus.COMPLETED)
                .toList();

        for (PaymentTransactionEntity payout : sellerPayouts) {
            reverseWalletTransaction(
                    order.getId(),
                    payout,
                    TransactionType.SELLER_PAYOUT_REVERSAL,
                    "seller"
            );
        }

        for (PaymentTransactionEntity commission : commissions) {
            reverseWalletTransaction(
                    order.getId(),
                    commission,
                    TransactionType.PLATFORM_COMMISSION_REVERSAL,
                    "owner"
            );
        }

        int updatedCardRows = PCR.increaseBalance(
                payment.getPaymentCardId(),
                order.getBuyerId(),
                payment.getAmount()
        );

        if (updatedCardRows != 1) {
            throw new RefundNotAvailableException(
                    "The buyer payment card is not available for a refund"
            );
        }

        PTR.save(
                new PaymentTransactionEntity(
                        order.getId(),
                        order.getBuyerId(),
                        TransactionType.REFUND,
                        payment.getAmount(),
                        TransactionStatus.COMPLETED,
                        "refund:order:" + order.getId() + ":buyer",
                        payment.getPaymentCardId()
                )
        );

        sellerPayouts.forEach(PaymentTransactionEntity::markRefunded);
        commissions.forEach(PaymentTransactionEntity::markRefunded);
        payment.markRefunded();
        order.changePaymentStatus(PaymentStatus.REFUNDED);
    }

    private void reverseWalletTransaction(//с wallet забираются деньги
            Long orderId,
            PaymentTransactionEntity original,
            TransactionType reversalType,
            String keyPart
    ) {
        int updatedRows = original.isPending()
                ? WAR.decreasePendingBalance(original.getUserId(), original.getAmount())
                : WAR.decreaseBalance(original.getUserId(), original.getAmount());

        if (updatedRows != 1) {
            throw new RefundNotAvailableException(
                    "The credited wallet does not have enough funds for a refund"
            );
        }

        PTR.save(
                new PaymentTransactionEntity(
                        orderId,
                        original.getUserId(),
                        reversalType,
                        original.getAmount(),
                        TransactionStatus.COMPLETED,
                        "refund:order:"
                                + orderId
                                + ":"
                                + keyPart
                                + ":"
                                + original.getUserId()
                )
        );
    }
}
