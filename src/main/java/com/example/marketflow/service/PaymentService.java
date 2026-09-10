package com.example.marketflow.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.marketflow.Order.OrderEntity;
import com.example.marketflow.Order.OrderItemEntity;
import com.example.marketflow.Order.OrderStatus;
import com.example.marketflow.Repository.OrderItemRepository;
import com.example.marketflow.Repository.OrderRepository;
import com.example.marketflow.Repository.PaymentCardRepository;
import com.example.marketflow.Repository.PaymentTransactionRepository;
import com.example.marketflow.Repository.ProductRepository;
import com.example.marketflow.Repository.WalletAccountRepository;
import com.example.marketflow.exception.InsufficientFundsException;
import com.example.marketflow.exception.InsufficientStockException;
import com.example.marketflow.exception.InvalidOrderStateException;
import com.example.marketflow.exception.OrderNotFoundException;
import com.example.marketflow.exception.PaymentAlreadyProcessedException;
import com.example.marketflow.exception.PaymentCardNotFoundException;
import com.example.marketflow.exception.ProductNotFoundException;
import com.example.marketflow.marketplace.OBSERFFORSENDBYSELLERPRODUCTSTATUS;
import com.example.marketflow.marketplace.SellerOrderEntity;
import com.example.marketflow.marketplace.SellerOrderRepository;
import com.example.marketflow.marketplace.OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS;
import com.example.marketflow.payment.PayOrderRequest;
import com.example.marketflow.payment.PaymentMapper;
import com.example.marketflow.payment.PaymentPageDto;
import com.example.marketflow.payment.PaymentStatus;
import com.example.marketflow.payment.PaymentTransactionEntity;
import com.example.marketflow.payment.TransactionStatus;
import com.example.marketflow.payment.TransactionType;
import com.example.marketflow.payment.WalletAccountEntity;
import com.example.marketflow.payment.WalletType;
import com.example.marketflow.payment_cards.PaymentCardEntity;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final OrderRepository OR;
    private final PaymentCardRepository PCR;
    private final PaymentTransactionRepository PTR;
    private final OrderItemRepository OIR;
    private final ProductRepository PR;
    private final SellerOrderRepository SOR;
    private final WalletAccountRepository WAR;

    @Transactional(readOnly = true)
    public PaymentPageDto getPaymentPage(Long orderId, Long buyerId) {
        OrderEntity order = OR.findByIdAndBuyerId(orderId, buyerId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new InvalidOrderStateException("A cancelled order cannot be paid");
        }
        return new PaymentPageDto(order.getId(), order.getTotalPrice(), order.getPaymentStatus(),
                PCR.findAllByUseridAndActiveTrue(buyerId).stream()
                        .map(PaymentMapper::convert).toList(), UUID.randomUUID().toString());
    }

    // Фиксируем отказ из-за недостатка средств; остальные ошибки с товаром, картой и журналом приводят к откату.
    @Transactional(noRollbackFor = InsufficientFundsException.class)
    public Long payOrder(Long orderId, Long buyerId, PayOrderRequest request) {
        OrderEntity order = OR.findForPayment(orderId, buyerId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        var checkonbegin = PTR.findByIdempotencyKey(request.idempotencyKey());
        if (checkonbegin.isPresent()) {
            var entity = checkonbegin.get();
            if (!Objects.equals(entity.getOrderId(), orderId)
                    || !Objects.equals(entity.getUserId(), buyerId)
                    || !Objects.equals(entity.getPaymentCardId(), request.cardId())) {
                throw new PaymentAlreadyProcessedException();
            }
            if (entity.getStatus() == TransactionStatus.COMPLETED) return orderId;
            if (entity.getStatus() == TransactionStatus.FAILED) throw new InsufficientFundsException();
            throw new PaymentAlreadyProcessedException();
        }
        if (order.getStatus() != OrderStatus.CREATED
                || (order.getPaymentStatus() != PaymentStatus.NOT_PAID
                    && order.getPaymentStatus() != PaymentStatus.FAILED)) {
            throw new InvalidOrderStateException("Only an unpaid CREATED order can be paid");
        }
        PaymentCardEntity card = PCR.findForPayment(request.cardId(), buyerId)
                .orElseThrow(PaymentCardNotFoundException::new);
        order.changePaymentStatus(PaymentStatus.PROCESSING);
        if (card.getBalance().compareTo(order.getTotalPrice()) < 0) {
            order.changePaymentStatus(PaymentStatus.FAILED);
            savePayment(order, request, TransactionStatus.FAILED);
            throw new InsufficientFundsException();
        }
        var items = OIR.findAllByOrderId(orderId).stream()
                .sorted(Comparator.comparing(OrderItemEntity::getProductId)).toList();
        if (items.isEmpty()) throw new InvalidOrderStateException("An order must contain products");
        // Условно обновляем остатки в постоянном порядке, чтобы не продать лишнее и снизить вероятность взаимных блокировок.
        for (var item : items) {
            if (PR.decreaseStock(item.getProductId(), item.getQuantity()) != 1) {
                throw new InsufficientStockException();
            }
        }
        // Заблокированная управляемая сущность карты сохраняется в одной транзакции с остатками и оплатой.
        card.debit(order.getTotalPrice());//уменьшали деньги с карты покупателя
        savePayment(order, request, TransactionStatus.COMPLETED);
        divideBetweenSellerAndPlatform(order);//раздали по продавцам
        order.changePaymentStatus(PaymentStatus.PAID);
        order.changeStatus(OrderStatus.CONFIRMED);
        return orderId;
    }

    @Transactional
    public void refundOrder(Long orderId, Long buyerId) {//отменяет оплаченный заказ
        OrderEntity order = OR.findForPayment(orderId, buyerId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() == OrderStatus.CANCELLED
                && order.getPaymentStatus() == PaymentStatus.REFUNDED) {
            return;
        }
        if (order.getStatus() != OrderStatus.CONFIRMED
                || order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new InvalidOrderStateException(
                    "Only a paid order that has not entered fulfillment can be refunded"
            );
        }

        PaymentTransactionEntity payment = PTR.findAllByOrderId(orderId).stream()
                .filter(transaction -> transaction.getType() == TransactionType.PAYMENT)
                .filter(transaction -> transaction.getStatus() == TransactionStatus.COMPLETED)
                .findFirst()
                .orElseThrow(() -> new InvalidOrderStateException("Completed payment transaction not found"));

        PaymentCardEntity card = PCR
                .findForBalanceUpdate(payment.getPaymentCardId(), buyerId)
                .orElseThrow(PaymentCardNotFoundException::new);

        List<SellerOrderEntity> parts = SOR
                .findAllByOrderIdOrderBySellerId(orderId);
        if (parts.isEmpty()) {
            throw new InvalidOrderStateException("Seller order parts not found");
        }
        if (parts.stream().anyMatch(part ->
                part.getSettlementStatus() != OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS.PENDINGWALLET
                        || part.getStatus() != OBSERFFORSENDBYSELLERPRODUCTSTATUS.NEW)) {
            throw new InvalidOrderStateException(
                    "A refund is available only before a seller starts fulfillment"
            );
        }

        WalletAccountEntity platformWallet = BlockPlatformWallet();
        for (SellerOrderEntity part : parts) {
            takeMoneyFromSellerAndPlatform(order, part, platformWallet);
        }

        card.credit(order.getTotalPrice());
        OIR.findAllByOrderId(orderId).stream()
                .sorted(Comparator.comparing(OrderItemEntity::getProductId))
                .forEach(item -> {
                    if (PR.increaseStock(item.getProductId(), item.getQuantity()) != 1) {
                        throw new ProductNotFoundException(item.getProductId());
                    }
                });

        PTR.saveAndFlush(new PaymentTransactionEntity(
                orderId,
                buyerId,
                null,
                null,
                payment.getId(),
                TransactionType.RETURNMONEY,
                order.getTotalPrice(),
                TransactionStatus.COMPLETED,
                "refund:" + orderId,
                payment.getPaymentCardId()
        ));
        order.changePaymentStatus(PaymentStatus.REFUNDED);
        order.changeStatus(OrderStatus.CANCELLED);
    }

    private PaymentTransactionEntity savePayment(//сохраняет в payment_transactions запись об успешной или неуспешной попытке
            OrderEntity order,
            PayOrderRequest request,
            TransactionStatus status
    ) {
        return PTR.saveAndFlush(new PaymentTransactionEntity(
                order.getId(),
                order.getBuyerId(),
                TransactionType.PAYMENT,
                order.getTotalPrice(),
                status,
                request.idempotencyKey(),
                request.cardId()
        ));
    }

    private void divideBetweenSellerAndPlatform(OrderEntity order) {//разделяет стоимость заказа между продавцами и платформой
        List<SellerOrderEntity> parts = SOR
                .findAllByOrderIdOrderBySellerId(order.getId());
        if (parts.isEmpty()) {
            throw new InvalidOrderStateException("Seller order parts not found");
        }

        WalletAccountEntity platformWallet = BlockPlatformWallet();
        for (SellerOrderEntity part : parts) {
            WalletAccountEntity sellerWallet = WAR.findLockedByUserId(part.getSellerId())
                    .orElseThrow(() -> new InvalidOrderStateException(
                            "Seller wallet not found for seller " + part.getSellerId()
                    ));

            BigDecimal commission = part.getTotalAmount()
                    .multiply(part.getCommissionRate())
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal sellerAmount = part.getTotalAmount().subtract(commission);

            part.recordSettlement(part.getCommissionRate(), commission, sellerAmount);
            sellerWallet.addPending(sellerAmount);
            SaveInfoinPTR(
                    order.getId(),
                    part.getSellerId(),
                    part,
                    sellerWallet,
                    null,
                    TransactionType.SELLER_PENDINGWALLET,
                    sellerAmount,
                    "seller-accrual:"
            );

            if (commission.signum() > 0) {
                platformWallet.addPending(commission);
                SaveInfoinPTR(
                        order.getId(),
                        null,
                        part,
                        platformWallet,
                        null,
                        TransactionType.PLATFORM_COMMISSION,
                        commission,
                        "platform-commission:"
                );
            }
        }
    }

    private void takeMoneyFromSellerAndPlatform(//Если возврат ,забираем у продавца и владельца обратно деньги
            OrderEntity order,
            SellerOrderEntity part,
            WalletAccountEntity platformWallet
    ) {
        WalletAccountEntity sellerWallet = WAR.findLockedByUserId(part.getSellerId())
                .orElseThrow(() -> new InvalidOrderStateException(
                        "Seller wallet not found for seller " + part.getSellerId()
                ));

        PaymentTransactionEntity sellerAccrual = ResearchSpecificTransaction(
                part,
                sellerWallet,
                TransactionType.SELLER_PENDINGWALLET
        );
        sellerWallet.minusPending(part.getSellerAmount());
        SaveInfoinPTR(
                order.getId(),
                part.getSellerId(),
                part,
                sellerWallet,
                sellerAccrual.getId(),
                TransactionType.SELLER_RETURNMONEY,
                part.getSellerAmount(),
                "seller-reversal:"
        );

        if (part.getCommissionAmount().signum() > 0) {
            PaymentTransactionEntity platformAccrual = ResearchSpecificTransaction(
                    part,
                    platformWallet,
                    TransactionType.PLATFORM_COMMISSION
            );
            platformWallet.minusPending(part.getCommissionAmount());
            SaveInfoinPTR(
                    order.getId(),
                    null,
                    part,
                    platformWallet,
                    platformAccrual.getId(),
                    TransactionType.PLATFORM_RETURMONEY,
                    part.getCommissionAmount(),
                    "platform-reversal:"
            );
        }

        part.reverseSettlement();
        part.transition(OBSERFFORSENDBYSELLERPRODUCTSTATUS.CANCELLED, Instant.now());
    }

    private WalletAccountEntity BlockPlatformWallet() {
        return WAR.findLockedByType(WalletType.PLATFORM)
                .orElseThrow(() -> new InvalidOrderStateException("Platform wallet not found"));
    }

    private PaymentTransactionEntity ResearchSpecificTransaction(
            SellerOrderEntity part,
            WalletAccountEntity wallet,
            TransactionType type
    ) {
        return PTR.findBySellerOrderIdAndWalletAccountIdAndType(
                        part.getId(),
                        wallet.getId(),
                        type
                )
                .orElseThrow(() -> new InvalidOrderStateException(
                        "Settlement transaction not found: " + type
                ));
    }

    private void SaveInfoinPTR(
            Long orderId,
            Long userId,
            SellerOrderEntity part,
            WalletAccountEntity wallet,
            Long relatedTransactionId,
            TransactionType type,
            BigDecimal amount,
            String keyPrefix
    ) {
        PTR.save(new PaymentTransactionEntity(
                orderId,
                userId,
                part.getId(),
                wallet.getId(),
                relatedTransactionId,
                type,
                amount,
                TransactionStatus.COMPLETED,
                keyPrefix + orderId + ":" + part.getId(),
                null
        ));
    }
}
