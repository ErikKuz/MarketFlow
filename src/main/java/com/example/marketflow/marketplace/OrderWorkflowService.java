package com.example.marketflow.marketplace;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.marketflow.Order.OrderEntity;
import com.example.marketflow.Order.OrderItemDto;
import com.example.marketflow.Order.OrderItemEntity;
import com.example.marketflow.Order.OrderStatus;
import com.example.marketflow.Repository.OrderItemRepository;
import com.example.marketflow.Repository.OrderRepository;
import com.example.marketflow.Repository.PaymentTransactionRepository;
import com.example.marketflow.Repository.WalletAccountRepository;
import com.example.marketflow.exception.InvalidOrderStateException;
import com.example.marketflow.exception.OrderNotFoundException;
import com.example.marketflow.marketplace.MarketplaceViews.PageView;
import com.example.marketflow.marketplace.MarketplaceViews.SellerOrderDetails;
import com.example.marketflow.marketplace.MarketplaceViews.SellerOrderSpecific;
import com.example.marketflow.marketplace.MarketplaceViews.ShortInfoAboutMyOrderInListOrder;
import com.example.marketflow.payment.PaymentStatus;
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

@Service
@RequiredArgsConstructor
public class OrderWorkflowService {
    private final SellerOrderRepository SOR;
    private final OrderRepository OR;
    private final OrderItemRepository OIR;
    private final WalletAccountRepository WAR;
    private final PaymentTransactionRepository PTR;
    private final MarketplaceAccess access;
    private final Clock clock;
    private final OutboxService outboxService;

    //группирует товары заказа по продавцам,считает для кажвыго сумму
    @Transactional
    public void initialize(OrderEntity order, List<OrderItemEntity> orderItems) {
        Map<Long, BigDecimal> totals = orderItems.stream().collect(Collectors.toMap(
                OrderItemEntity::getSellerId, OrderItemEntity::getTotalPrice, BigDecimal::add, TreeMap::new));
        totals.forEach((seller, amount) ->
                SOR.save(new SellerOrderEntity(order.getId(), seller, amount, clock.instant())));
    }

    //переводит все части указанного заказа в статус CANCELLED
    @Transactional
    public void cancelled(OrderEntity order) {
        SOR.findAllByOrderIdOrderBySellerId(order.getId())
                .forEach(part -> part.transition(OBSERFFORSENDBYSELLERPRODUCTSTATUS.CANCELLED, clock.instant()));
    }

    //проверяет роль покупателя и возвращает страницу его заказов
    @Transactional(readOnly = true)
    public PageView<ShortInfoAboutMyOrderInListOrder> buyerOrders(Long buyerId, int page, int size) {
        access.checkonRights(buyerId, "BUYER");
        return PageView.of(OR.findAllByBuyerIdOrderByCreatedAtDescIdDesc(
                buyerId, MarketplaceAccess.page(page, size)).map(ShortInfoAboutMyOrderInListOrder::of));
    }

    @Transactional(readOnly = true)
    public List<SellerOrderSpecific> buyerParts(Long buyerId, Long orderId) {
        buyerSummary(buyerId, orderId);
        return SOR.findAllByOrderIdOrderBySellerId(orderId).stream().map(SellerOrderSpecific::of).toList();
    }

    @Transactional(readOnly = true)
    public ShortInfoAboutMyOrderInListOrder buyerSummary(Long buyerId, Long orderId) {
        access.checkonRights(buyerId, "BUYER");
        return OR.findByIdAndBuyerId(orderId, buyerId).map(ShortInfoAboutMyOrderInListOrder::of)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public PageView<SellerOrderSpecific> SOR(Long sellerId, int page, int size) {
        access.checkonRights(sellerId, "SELLER");
        return PageView.of(SOR.findPaidBySellerId(
                sellerId, MarketplaceAccess.page(page, size)).map(SellerOrderSpecific::of));
    }

    @Transactional(readOnly = true)
    public SellerOrderDetails sellerDetails(Long sellerId, Long partId) {
        access.checkonRights(sellerId, "SELLER");
        var part = sellerPart(sellerId, partId);
        var order = OR.findById(part.getOrderId()).orElseThrow();
        var detailItems = OIR.findAllByOrderIdAndSellerId(order.getId(), sellerId).stream()
                .map(i -> new OrderItemDto(i.getProductId(), i.getSellerId(), i.getProductName(),
                        i.getUnitPrice(), i.getQuantity(), i.getTotalPrice(), i.getImageUrl())).toList();
        return new SellerOrderDetails(SellerOrderSpecific.of(part), order.getPaymentStatus(), detailItems);
    }

    private SellerOrderEntity sellerPart(Long sellerId, Long partId) {
        return SOR.findById(partId).filter(part -> part.getSellerId().equals(sellerId))
                .orElseThrow(() -> MarketplaceException.missing("Seller order not found"));
    }

    @Transactional
    public void sellerTransition(Long sellerId, Long partId, OBSERFFORSENDBYSELLERPRODUCTSTATUS next) {
        access.checkonRights(sellerId, "SELLER");
        var orderId = SOR.findOrderId(partId)
                .orElseThrow(() -> MarketplaceException.missing("Seller order not found"));
        // Блокируем заказ до загрузки его частей, чтобы параллельные действия продавцов не потеряли обновления статуса.
        var order = OR.findLocked(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        var part = sellerPart(sellerId, partId);
        if (next != OBSERFFORSENDBYSELLERPRODUCTSTATUS.PROCESSING
                && next != OBSERFFORSENDBYSELLERPRODUCTSTATUS.SELLERSENDPRODUCT) {
            throw new InvalidOrderStateException("Seller may only process or ship their own products");
        }
        if (order.getPaymentStatus() != PaymentStatus.PAID
                || order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.COMPLETED) {
            throw new InvalidOrderStateException("Only a paid, open order can be fulfilled");
        }
        if (part.getStatus() == next) return;
        var previousPartStatus = part.getStatus();
        part.transition(next, clock.instant());
        if (order.getStatus() == OrderStatus.CONFIRMED) order.changeStatus(OrderStatus.SELLERSSTARTWORK);
        CheckSELLERSENDWORKANDSEND(order);
        MarketFlowEventType eventType = next == OBSERFFORSENDBYSELLERPRODUCTSTATUS.PROCESSING
                ? MarketFlowEventType.SELLER_STARTED_WORK
                : MarketFlowEventType.SELLER_SENT_PRODUCT;
        String routingKey = next == OBSERFFORSENDBYSELLERPRODUCTSTATUS.PROCESSING
                ? RabbitMqNames.SELLER_STARTED_ORDER_PROCESSING_EVENT
                : RabbitMqNames.SELLER_SENT_PRODUCT_EVENT;
        saveEvent(
                eventType, routingKey, order, part, null,
                previousPartStatus.name(), next.name()
        );
    }

    private void CheckSELLERSENDWORKANDSEND(OrderEntity order) {
        var parts = SOR.findAllByOrderIdOrderBySellerId(order.getId());
        boolean allSent = !parts.isEmpty() && parts.stream().allMatch(part ->
                part.getStatus() == OBSERFFORSENDBYSELLERPRODUCTSTATUS.SELLERSENDPRODUCT
                        || part.getStatus() == OBSERFFORSENDBYSELLERPRODUCTSTATUS.USERGETPRODUCT);
        if (allSent && order.getStatus() == OrderStatus.SELLERSSTARTWORK) {
            order.changeStatus(OrderStatus.SELLERSENDWORKANDSEND);
        }
    }

    @Transactional
    public void ConfirmThatUSERGETPRODUCTBySellerID(Long buyerId, Long orderId, Long partId) {
        access.checkonRights(buyerId, "BUYER");
        var order = OR.findForPayment(orderId, buyerId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        var part = SOR.findById(partId).filter(p -> p.getOrderId().equals(orderId))
                .orElseThrow(() -> MarketplaceException.missing("Seller order not found"));
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new InvalidOrderStateException("Only a paid shipment can be received");
        }
        if (part.getStatus() == OBSERFFORSENDBYSELLERPRODUCTSTATUS.USERGETPRODUCT) {
            if (part.getSettlementStatus()
                    == OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS.PENDINGWALLET) {
                queueSellerFundsRelease(order, part);
            }
            return;
        }
        if (order.getStatus() != OrderStatus.SELLERSSTARTWORK
                && order.getStatus() != OrderStatus.SELLERSENDWORKANDSEND) {
            throw new InvalidOrderStateException("The order is not being delivered");
        }
        var previousPartStatus = part.getStatus();
        part.transition(OBSERFFORSENDBYSELLERPRODUCTSTATUS.USERGETPRODUCT, clock.instant());
        queueSellerFundsRelease(order, part);
        saveEvent(
                MarketFlowEventType.USER_RECEIVED_PRODUCT,
                RabbitMqNames.BUYER_RECEIVED_PRODUCT_EVENT,
                order,
                part,
                null,
                previousPartStatus.name(),
                OBSERFFORSENDBYSELLERPRODUCTSTATUS.USERGETPRODUCT.name()
        );
        CheckSELLERSENDWORKANDSEND(order);
        if (SOR.findAllByOrderIdOrderBySellerId(orderId).stream()
                .allMatch(p -> p.getStatus() == OBSERFFORSENDBYSELLERPRODUCTSTATUS.USERGETPRODUCT)) {
            order.recordDelivery(clock.instant());
        }
    }

    private void queueSellerFundsRelease(OrderEntity order, SellerOrderEntity part) {
        outboxService.saveCommand(MarketFlowCommand.releaseSellerFunds(
                order.getId(),
                part.getId(),
                part.getSellerId(),
                part.getSellerAmount(),
                clock.instant()
        ), RabbitMqNames.RELEASE_SELLER_FUNDS_COMMAND);
    }

    @Transactional
    public void releaseSellerFunds(MarketFlowCommand command) {
        if (command.commandType() != CommandType.RELEASE_SELLER_FUNDS) {
            throw new IllegalArgumentException("Получена команда другого типа");
        }

        OrderEntity order = OR.findLocked(command.orderId())
                .orElseThrow(() -> new OrderNotFoundException(command.orderId()));
        SellerOrderEntity part = SOR.findLockedById(command.sellerOrderId())
                .orElseThrow(() -> MarketplaceException.missing("Часть заказа продавца не найдена"));
        if (!Objects.equals(part.getOrderId(), command.orderId())
                || !Objects.equals(part.getSellerId(), command.sellerId())
                || part.getSellerAmount().compareTo(command.amount()) != 0) {
            throw new InvalidOrderStateException("Команда освобождения не соответствует части заказа");
        }
        if (part.getSettlementStatus() == OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS.RETURNMONEY) {
            return;
        }
        if (part.getSettlementStatus() == OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS.MAINWALLET) {
            completeOrderWhenAllFundsReleased(order);
            return;
        }
        if (part.getStatus() != OBSERFFORSENDBYSELLERPRODUCTSTATUS.USERGETPRODUCT
                || part.getSettlementStatus()
                != OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS.PENDINGWALLET) {
            throw new InvalidOrderStateException("Деньги этой части заказа пока нельзя освободить");
        }
        TrounseferMoneyFromPendingInMainWallet(part);
        saveEvent(
                MarketFlowEventType.SELLER_MONEY_AVAILABLE,
                RabbitMqNames.SELLER_FUNDS_RELEASED_EVENT,
                order,
                part,
                part.getSellerAmount(),
                OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS.PENDINGWALLET.name(),
                OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS.MAINWALLET.name()
        );
        completeOrderWhenAllFundsReleased(order);
    }

    private void completeOrderWhenAllFundsReleased(OrderEntity order) {
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            return;
        }
        var parts = SOR.findAllByOrderIdOrderBySellerId(order.getId());
        boolean completed = !parts.isEmpty() && parts.stream().allMatch(part ->
                part.getStatus() == OBSERFFORSENDBYSELLERPRODUCTSTATUS.USERGETPRODUCT
                        && part.getSettlementStatus()
                        == OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS.MAINWALLET);
        if (!completed) {
            return;
        }
        OrderStatus previousOrderStatus = order.getStatus();
        order.changeStatus(OrderStatus.COMPLETED);
        if (order.getDeliveredAt() == null) {
            order.recordDelivery(clock.instant());
        }
        saveEvent(
                MarketFlowEventType.ORDER_COMPLETED,
                RabbitMqNames.ORDER_COMPLETED_EVENT,
                order,
                null,
                order.getTotalPrice(),
                previousOrderStatus.name(),
                OrderStatus.COMPLETED.name()
        );
    }

    private void TrounseferMoneyFromPendingInMainWallet(SellerOrderEntity part) {
        if (part.getSettlementStatus() != OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS.PENDINGWALLET) {
            throw new InvalidOrderStateException(
                    "Only pending settlement can be released"
            );
        }

        // В каждой денежной операции сначала блокируем кошелёк платформы, а затем кошелёк продавца.
        // Постоянный порядок блокировок предотвращает взаимную блокировку оплаты и доставки.
        WalletAccountEntity platformWallet = WAR.findLockedByType(WalletType.PLATFORM)
                .orElseThrow(() -> new InvalidOrderStateException("Platform wallet not found"));
        WalletAccountEntity sellerWallet = WAR.findLockedByUserId(part.getSellerId())
                .orElseThrow(() -> new InvalidOrderStateException(
                        "Seller wallet not found for seller " + part.getSellerId()
                ));

        PaymentTransactionEntity sellerAccrual = requireSettlementTransaction(
                part,
                sellerWallet,
                TransactionType.SELLER_PENDINGWALLET
        );
        sellerWallet.releasePending(part.getSellerAmount());
        saveOperationTrounseferMoneyFromPendingInMainWalletInPaymentTransaction(part, sellerWallet, part.getSellerId(), part.getSellerAmount(), sellerAccrual.getId(), "seller");

        if (part.getCommissionAmount().signum() > 0) {
            PaymentTransactionEntity platformAccrual = requireSettlementTransaction(
                    part,
                    platformWallet,
                    TransactionType.PLATFORM_COMMISSION
            );
            platformWallet.releasePending(part.getCommissionAmount());
            saveOperationTrounseferMoneyFromPendingInMainWalletInPaymentTransaction(part, platformWallet, null, part.getCommissionAmount(), platformAccrual.getId(), "platform");
        }

        part.markSettlementAvailable();
    }

    private PaymentTransactionEntity requireSettlementTransaction(
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

    private void saveOperationTrounseferMoneyFromPendingInMainWalletInPaymentTransaction(
            SellerOrderEntity part,
            WalletAccountEntity wallet,
            Long userId,
            BigDecimal amount,
            Long relatedTransactionId,
            String owner
    ) {
        PTR.save(new PaymentTransactionEntity(
                part.getOrderId(),
                userId,
                part.getId(),
                wallet.getId(),
                relatedTransactionId,
                TransactionType.SELLER_MAINWALLET,
                amount,
                TransactionStatus.COMPLETED,
                "funds-release:" + owner + ":" + part.getOrderId() + ":" + part.getId(),
                null
        ));
    }

    private void saveEvent(
            MarketFlowEventType type,
            String routingKey,
            OrderEntity order,
            SellerOrderEntity part,
            BigDecimal amount,
            String previousStatus,
            String currentStatus
    ) {
        outboxService.save(MarketFlowEvent.create(
                type,
                order.getId(),
                part == null ? null : part.getId(),
                order.getBuyerId(),
                part == null ? null : part.getSellerId(),
                amount,
                previousStatus,
                currentStatus,
                clock.instant()
        ), routingKey);
    }
}
