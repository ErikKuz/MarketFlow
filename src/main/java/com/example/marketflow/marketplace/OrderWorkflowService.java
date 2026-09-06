package com.example.marketflow.marketplace;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.marketflow.Order.*;
import com.example.marketflow.Repository.*;
import com.example.marketflow.payment.PaymentStatus;
import static com.example.marketflow.marketplace.MarketplaceViews.*;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class OrderWorkflowService {
    private final SellerOrderRepository sellerOrders;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final AuditEventRepository audit;
    private final PlatformSettingsRepository settings;
    private final MarketplaceProperties properties;
    private final MarketplaceAccess access;
    private final Clock clock;

    @Transactional
    public void initialize(OrderEntity order, List<OrderItemEntity> orderItems) {
        var rate = settings.findById(1L).map(PlatformSettingsEntity::getCommissionRate).orElse(new BigDecimal("0.10"));
        order.configurePayment(clock.instant().plus(properties.getPaymentTimeoutMinutes(), ChronoUnit.MINUTES), rate);
        Map<Long, BigDecimal> totals = orderItems.stream().collect(Collectors.toMap(
                OrderItemEntity::getSellerId, OrderItemEntity::getTotalPrice, BigDecimal::add, TreeMap::new));
        totals.forEach((seller, total) -> sellerOrders.save(new SellerOrderEntity(order.getId(), seller, total, clock.instant())));
        record(order.getBuyerId(), order.getId(), null, "ORDER_CREATED", null, "CREATED", "Stock reserved");
    }

    @Transactional
    public void record(Long actor, Long order, Long seller, String action, String previous, String next, String detail) {
        audit.save(new AuditEventEntity(actor, order, seller, action, previous, next, detail, clock.instant()));
    }

    @Transactional
    public void cancelled(OrderEntity order, Long actor, String reason) {
        for (var part : sellerOrders.findAllByOrderIdOrderBySellerId(order.getId())) {
            var previous = part.getStatus();
            if (previous != FulfillmentStatus.CANCELLED) part.transition(FulfillmentStatus.CANCELLED, clock.instant());
            record(actor, order.getId(), part.getSellerId(), "FULFILLMENT_CANCELLED", previous.name(), "CANCELLED", reason);
        }
        record(actor, order.getId(), null, "ORDER_CANCELLED", order.getStatus().name(), "CANCELLED", reason);
    }

    @Transactional(readOnly = true)
    public PageView<OrderSummary> buyerOrders(Long buyerId, int page, int size) {
        access.require(buyerId, "BUYER");
        return PageView.of(orders.findAllByBuyerIdOrderByCreatedAtDescIdDesc(buyerId, MarketplaceAccess.page(page, size)).map(OrderSummary::of));
    }

    @Transactional(readOnly = true)
    public List<SellerOrderView> buyerParts(Long buyerId, Long orderId) {
        buyerOrder(buyerId, orderId);
        return sellerOrders.findAllByOrderIdOrderBySellerId(orderId).stream().map(SellerOrderView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<AuditView> buyerHistory(Long buyerId, Long orderId) {
        buyerOrder(buyerId, orderId);
        return audit.findAllByOrderIdOrderByCreatedAtAscIdAsc(orderId).stream().map(AuditView::of).toList();
    }

    private void buyerOrder(Long buyerId, Long orderId) {
        access.require(buyerId, "BUYER");
        orders.findByIdAndBuyerId(orderId, buyerId).orElseThrow(() -> MarketplaceException.missing("Order not found"));
    }

    @Transactional(readOnly = true)
    public OrderSummary buyerSummary(Long buyerId, Long orderId) {
        access.require(buyerId, "BUYER");
        return orders.findByIdAndBuyerId(orderId, buyerId).map(OrderSummary::of)
                .orElseThrow(() -> MarketplaceException.missing("Order not found"));
    }

    @Transactional(readOnly = true)
    public PageView<SellerOrderView> sellerOrders(Long sellerId, int page, int size) {
        access.require(sellerId, "SELLER");
        return PageView.of(sellerOrders.findAllBySellerIdOrderByCreatedAtDescIdDesc(sellerId, MarketplaceAccess.page(page, size)).map(SellerOrderView::of));
    }

    @Transactional(readOnly = true)
    public SellerOrderDetails sellerDetails(Long sellerId, Long partId) {
        access.require(sellerId, "SELLER");
        var part = sellerOrders.findById(partId).filter(p -> p.getSellerId().equals(sellerId))
                .orElseThrow(() -> MarketplaceException.missing("Seller order not found"));
        var order = orders.findById(part.getOrderId()).orElseThrow();
        var detailItems = items.findAllByOrderIdAndSellerId(order.getId(), sellerId).stream()
                .map(i -> new OrderItemDto(i.getProductId(), i.getSellerId(), i.getProductName(),
                        i.getUnitPrice(), i.getQuantity(), i.getTotalPrice(), i.getImageUrl())).toList();
        var history = audit.findAllByOrderIdOrderByCreatedAtAscIdAsc(order.getId()).stream()
                .filter(a -> a.getSellerId() == null || sellerId.equals(a.getSellerId())).map(AuditView::of).toList();
        return new SellerOrderDetails(SellerOrderView.of(part), order.getPaymentStatus(), detailItems, history);
    }

    @Transactional
    public void sellerTransition(Long sellerId, Long partId, FulfillmentStatus next) {
        access.require(sellerId, "SELLER");
        // Read only the parent ID first; take the parent lock BEFORE loading mutable child entities.
        var partRef = sellerOrders.findOrderId(partId).orElseThrow(() -> MarketplaceException.missing("Seller order not found"));
        var order = orders.findLocked(partRef).orElseThrow();
        var part = sellerOrders.findById(partId).filter(p -> p.getSellerId().equals(sellerId))
                .orElseThrow(() -> MarketplaceException.missing("Seller order not found"));
        if (next != FulfillmentStatus.ACCEPTED && next != FulfillmentStatus.PACKING && next != FulfillmentStatus.SHIPPED)
            throw MarketplaceException.conflict("Seller can only accept, pack or ship");
        if (order.getPaymentStatus() != PaymentStatus.PAID || order.getStatus() == OrderStatus.CANCELLED)
            throw MarketplaceException.conflict("Only paid orders can be processed");
        if (part.getStatus() == next) return;
        var previous = part.getStatus();
        part.transition(next, clock.instant());
        record(sellerId, order.getId(), sellerId, "FULFILLMENT_CHANGED", previous.name(), next.name(), null);
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            order.changeStatus(OrderStatus.PROCESSING);
            record(sellerId, order.getId(), null, "ORDER_PROCESSING", "CONFIRMED", "PROCESSING", null);
        }
    }

    @Transactional
    public void confirmDelivery(Long buyerId, Long orderId, Long partId) {
        access.require(buyerId, "BUYER");
        var order = orders.findForPayment(orderId, buyerId).orElseThrow(() -> MarketplaceException.missing("Order not found"));
        var part = sellerOrders.findById(partId).filter(p -> p.getOrderId().equals(orderId))
                .orElseThrow(() -> MarketplaceException.missing("Seller order not found"));
        if (part.getStatus() == FulfillmentStatus.DELIVERED) return;
        if (order.getPaymentStatus() != PaymentStatus.PAID || order.getStatus() != OrderStatus.PROCESSING)
            throw MarketplaceException.conflict("Order is not being fulfilled");
        part.transition(FulfillmentStatus.DELIVERED, clock.instant());
        record(buyerId, orderId, part.getSellerId(), "DELIVERY_CONFIRMED", "SHIPPED", "DELIVERED", null);
        if (sellerOrders.findAllByOrderIdOrderBySellerId(orderId).stream().allMatch(p -> p.getStatus() == FulfillmentStatus.DELIVERED)) {
            order.changeStatus(OrderStatus.COMPLETED);
            order.recordDelivery(clock.instant(), clock.instant().plus(properties.getReturnWindowDays(), ChronoUnit.DAYS));
            record(buyerId, orderId, null, "ORDER_COMPLETED", "PROCESSING", "COMPLETED", null);
        }
    }
}
