package com.example.marketflow.marketplace;

import java.time.Clock;
import java.util.Comparator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.marketflow.Order.*;
import com.example.marketflow.Repository.*;
import com.example.marketflow.payment.PaymentStatus;
import com.example.marketflow.service.PaymentService;
import static com.example.marketflow.marketplace.MarketplaceViews.*;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class ReturnService {
    private final ReturnRequestRepository returns;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final ProductRepository products;
    private final SellerOrderRepository sellerOrders;
    private final PaymentService payments;
    private final OrderWorkflowService workflow;
    private final MarketplaceAccess access;
    private final Clock clock;

    @Transactional
    public ReturnView request(Long buyerId, Long orderId, String reason) {
        access.require(buyerId, "BUYER");
        var order = orders.findForPayment(orderId, buyerId).orElseThrow(() -> MarketplaceException.missing("Order not found"));
        var existing = returns.findByOrderId(orderId);
        if (existing.isPresent()) return ReturnView.of(existing.get());
        if (order.getStatus() != OrderStatus.COMPLETED || order.getPaymentStatus() != PaymentStatus.PAID
                || order.getReturnDeadline() == null || !clock.instant().isBefore(order.getReturnDeadline()) || order.isFundsReleased())
            throw MarketplaceException.conflict("A return is allowed only within the return window after complete delivery");
        if (reason == null || reason.isBlank() || reason.length() > 1000)
            throw MarketplaceException.conflict("A return reason of 1 to 1000 characters is required");
        var result = returns.save(new ReturnRequestEntity(orderId, buyerId, reason, clock.instant()));
        workflow.record(buyerId, orderId, null, "RETURN_REQUESTED", null, "PENDING", reason);
        return ReturnView.of(result);
    }

    @Transactional(readOnly = true)
    public ReturnView buyerRequest(Long buyerId, Long orderId) {
        access.require(buyerId, "BUYER");
        orders.findByIdAndBuyerId(orderId, buyerId).orElseThrow(() -> MarketplaceException.missing("Order not found"));
        return returns.findByOrderId(orderId).map(ReturnView::of).orElse(null);
    }

    @Transactional(readOnly = true)
    public PageView<ReturnView> pending(Long ownerId, int page, int size) {
        access.require(ownerId, "OWNER");
        return PageView.of(returns.findAllByOrderByRequestedAtDescIdDesc(MarketplaceAccess.page(page, size)).map(ReturnView::of));
    }

    @Transactional
    public void decide(Long ownerId, Long requestId, boolean approved, boolean restock, String reason) {
        access.require(ownerId, "OWNER");
        Long orderId = returns.findOrderId(requestId).orElseThrow(() -> MarketplaceException.missing("Return request not found"));
        var order = orders.findLocked(orderId).orElseThrow();
        var request = returns.findById(requestId).orElseThrow();
        if (request.getStatus() != ReturnRequestEntity.Status.PENDING) {
            if ((request.getStatus() == ReturnRequestEntity.Status.APPROVED) == approved) return;
            throw MarketplaceException.conflict("Return request already decided");
        }
        if (approved) {
            payments.refundOrder(order);
            if (restock) {
                for (var item : items.findAllByOrderId(orderId).stream().sorted(Comparator.comparing(OrderItemEntity::getProductId)).toList()) {
                    if (products.increaseStock(item.getProductId(), item.getQuantity()) != 1)
                        throw MarketplaceException.conflict("Unable to restore product stock");
                }
            }
            for (var part : sellerOrders.findAllByOrderIdOrderBySellerId(orderId)) {
                part.transition(FulfillmentStatus.RETURNED, clock.instant());
                workflow.record(ownerId, orderId, part.getSellerId(), "FULFILLMENT_RETURNED", "DELIVERED", "RETURNED", reason);
            }
            // The delivery remains a historical fact: OrderStatus stays COMPLETED, PaymentStatus becomes REFUNDED.
            order.releaseFunds();
        }
        request.decide(approved, reason, restock, clock.instant());
        workflow.record(ownerId, orderId, null, "RETURN_DECIDED", "PENDING", request.getStatus().name(), reason);
    }
}
