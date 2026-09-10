package com.example.marketflow.marketplace;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
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
import com.example.marketflow.marketplace.MarketplaceViews.OrderSummary;
import com.example.marketflow.marketplace.MarketplaceViews.PageView;
import com.example.marketflow.marketplace.MarketplaceViews.SellerOrderDetails;
import com.example.marketflow.marketplace.MarketplaceViews.SellerOrderView;
import com.example.marketflow.payment.PaymentStatus;
import com.example.marketflow.payment.PaymentTransactionEntity;
import com.example.marketflow.payment.TransactionStatus;
import com.example.marketflow.payment.TransactionType;
import com.example.marketflow.payment.WalletAccountEntity;
import com.example.marketflow.payment.WalletType;

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

    @Transactional
    public void initialize(OrderEntity order, List<OrderItemEntity> orderItems) {
        Map<Long, BigDecimal> totals = orderItems.stream().collect(Collectors.toMap(
                OrderItemEntity::getSellerId, OrderItemEntity::getTotalPrice, BigDecimal::add, TreeMap::new));
        totals.forEach((seller, amount) ->
                SOR.save(new SellerOrderEntity(order.getId(), seller, amount, clock.instant())));
    }

    @Transactional
    public void cancelled(OrderEntity order) {
        SOR.findAllByOrderIdOrderBySellerId(order.getId())
                .forEach(part -> part.transition(OBSERFFORSENDBYSELLERPRODUCTSTATUS.CANCELLED, clock.instant()));
    }

    @Transactional(readOnly = true)
    public PageView<OrderSummary> buyerOrders(Long buyerId, int page, int size) {
        access.require(buyerId, "BUYER");
        return PageView.of(OR.findAllByBuyerIdOrderByCreatedAtDescIdDesc(
                buyerId, MarketplaceAccess.page(page, size)).map(OrderSummary::of));
    }

    @Transactional(readOnly = true)
    public List<SellerOrderView> buyerParts(Long buyerId, Long orderId) {
        buyerSummary(buyerId, orderId);
        return SOR.findAllByOrderIdOrderBySellerId(orderId).stream().map(SellerOrderView::of).toList();
    }

    @Transactional(readOnly = true)
    public OrderSummary buyerSummary(Long buyerId, Long orderId) {
        access.require(buyerId, "BUYER");
        return OR.findByIdAndBuyerId(orderId, buyerId).map(OrderSummary::of)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public PageView<SellerOrderView> SOR(Long sellerId, int page, int size) {
        access.require(sellerId, "SELLER");
        return PageView.of(SOR.findPaidBySellerId(
                sellerId, MarketplaceAccess.page(page, size)).map(SellerOrderView::of));
    }

    @Transactional(readOnly = true)
    public SellerOrderDetails sellerDetails(Long sellerId, Long partId) {
        access.require(sellerId, "SELLER");
        var part = sellerPart(sellerId, partId);
        var order = OR.findById(part.getOrderId()).orElseThrow();
        var detailItems = OIR.findAllByOrderIdAndSellerId(order.getId(), sellerId).stream()
                .map(i -> new OrderItemDto(i.getProductId(), i.getSellerId(), i.getProductName(),
                        i.getUnitPrice(), i.getQuantity(), i.getTotalPrice(), i.getImageUrl())).toList();
        return new SellerOrderDetails(SellerOrderView.of(part), order.getPaymentStatus(), detailItems);
    }

    private SellerOrderEntity sellerPart(Long sellerId, Long partId) {
        return SOR.findById(partId).filter(part -> part.getSellerId().equals(sellerId))
                .orElseThrow(() -> MarketplaceException.missing("Seller order not found"));
    }

    @Transactional
    public void sellerTransition(Long sellerId, Long partId, OBSERFFORSENDBYSELLERPRODUCTSTATUS next) {
        access.require(sellerId, "SELLER");
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
        part.transition(next, clock.instant());
        if (order.getStatus() == OrderStatus.CONFIRMED) order.changeStatus(OrderStatus.SELLERSSTARTWORK);
        updateShippingStatus(order);
    }

    private void updateShippingStatus(OrderEntity order) {
        var parts = SOR.findAllByOrderIdOrderBySellerId(order.getId());
        boolean allSent = !parts.isEmpty() && parts.stream().allMatch(part ->
                part.getStatus() == OBSERFFORSENDBYSELLERPRODUCTSTATUS.SELLERSENDPRODUCT
                        || part.getStatus() == OBSERFFORSENDBYSELLERPRODUCTSTATUS.USERGETPRODUCT);
        if (allSent && order.getStatus() == OrderStatus.SELLERSSTARTWORK) {
            order.changeStatus(OrderStatus.SELLERSENDWORKANDSEND);
        }
    }

    @Transactional
    public void confirmDelivery(Long buyerId, Long orderId, Long partId) {
        access.require(buyerId, "BUYER");
        var order = OR.findForPayment(orderId, buyerId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        var part = SOR.findById(partId).filter(p -> p.getOrderId().equals(orderId))
                .orElseThrow(() -> MarketplaceException.missing("Seller order not found"));
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            throw new InvalidOrderStateException("Only a paid shipment can be received");
        }
        if (part.getStatus() == OBSERFFORSENDBYSELLERPRODUCTSTATUS.USERGETPRODUCT) return;
        if (order.getStatus() != OrderStatus.SELLERSSTARTWORK
                && order.getStatus() != OrderStatus.SELLERSENDWORKANDSEND) {
            throw new InvalidOrderStateException("The order is not being delivered");
        }
        part.transition(OBSERFFORSENDBYSELLERPRODUCTSTATUS.USERGETPRODUCT, clock.instant());
        releaseSettlement(part);
        updateShippingStatus(order);
        if (SOR.findAllByOrderIdOrderBySellerId(orderId).stream()
                .allMatch(p -> p.getStatus() == OBSERFFORSENDBYSELLERPRODUCTSTATUS.USERGETPRODUCT)) {
            order.changeStatus(OrderStatus.COMPLETED);
            order.recordDelivery(clock.instant());
        }
    }

    private void releaseSettlement(SellerOrderEntity part) {
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
        saveRelease(part, sellerWallet, part.getSellerId(), part.getSellerAmount(), sellerAccrual.getId(), "seller");

        if (part.getCommissionAmount().signum() > 0) {
            PaymentTransactionEntity platformAccrual = requireSettlementTransaction(
                    part,
                    platformWallet,
                    TransactionType.PLATFORM_COMMISSION
            );
            platformWallet.releasePending(part.getCommissionAmount());
            saveRelease(part, platformWallet, null, part.getCommissionAmount(), platformAccrual.getId(), "platform");
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

    private void saveRelease(
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
}
