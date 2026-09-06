package com.example.marketflow.marketplace;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import com.example.marketflow.Order.*;
import com.example.marketflow.payment.*;
import com.example.marketflow.User.UserEntity;

public final class MarketplaceViews {
    private MarketplaceViews() {}
    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageView<T> of(Page<T> page) {
            return new PageView<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
    public record OrderSummary(Long id, OrderStatus status, PaymentStatus paymentStatus, BigDecimal totalPrice,
            Instant createdAt, Instant paymentExpiresAt, Instant returnDeadline) {
        public static OrderSummary of(OrderEntity o) {
            return new OrderSummary(o.getId(), o.getStatus(), o.getPaymentStatus(), o.getTotalPrice(),
                    o.getCreatedAt(), o.getPaymentExpiresAt(), o.getReturnDeadline());
        }
    }
    public record SellerOrderView(Long id, Long orderId, Long sellerId, FulfillmentStatus status, BigDecimal totalAmount,
            Instant createdAt, Instant shippedAt, Instant deliveredAt) {
        public static SellerOrderView of(SellerOrderEntity s) {
            return new SellerOrderView(s.getId(), s.getOrderId(), s.getSellerId(), s.getStatus(), s.getTotalAmount(),
                    s.getCreatedAt(), s.getShippedAt(), s.getDeliveredAt());
        }
    }
    public record SellerOrderDetails(SellerOrderView fulfillment, PaymentStatus paymentStatus, List<OrderItemDto> items, List<AuditView> history) {}
    public record AuditView(Long id, Long actorId, Long orderId, Long sellerId, String action, String previousStatus,
            String nextStatus, String detail, Instant createdAt) {
        public static AuditView of(AuditEventEntity a) {
            return new AuditView(a.getId(), a.getActorId(), a.getOrderId(), a.getSellerId(), a.getAction(),
                    a.getPreviousStatus(), a.getNextStatus(), a.getDetail(), a.getCreatedAt());
        }
    }
    public record WalletView(BigDecimal available, BigDecimal pending, BigDecimal reservedForWithdrawal) {}
    public record PaymentEntry(Long id, Long orderId, TransactionType type, BigDecimal amount,
            TransactionStatus status, boolean pending, Instant createdAt) {
        public static PaymentEntry of(PaymentTransactionEntity p) {
            return new PaymentEntry(p.getId(), p.getOrderId(), p.getType(), p.getAmount(), p.getStatus(), p.isPending(), p.getCreatedAt());
        }
    }
    public record ReturnView(Long id, Long orderId, Long buyerId, String reason, ReturnRequestEntity.Status status,
            String decisionReason, Instant requestedAt, Instant decidedAt, boolean restocked) {
        public static ReturnView of(ReturnRequestEntity r) {
            return new ReturnView(r.getId(), r.getOrderId(), r.getBuyerId(), r.getReason(), r.getStatus(),
                    r.getDecisionReason(), r.getRequestedAt(), r.getDecidedAt(), r.isRestocked());
        }
    }
    public record WithdrawalView(Long id, Long sellerId, Long cardId, BigDecimal amount, WithdrawalRequestEntity.Status status,
            String decisionReason, Instant requestedAt, Instant decidedAt) {
        public static WithdrawalView of(WithdrawalRequestEntity w) {
            return new WithdrawalView(w.getId(), w.getSellerId(), w.getCardId(), w.getAmount(), w.getStatus(),
                    w.getDecisionReason(), w.getRequestedAt(), w.getDecidedAt());
        }
    }
    public record SellerApplicationView(Long id, Long userId, SellerApplicationEntity.Status status, String reason,
            Instant createdAt, Instant decidedAt) {
        public static SellerApplicationView of(SellerApplicationEntity a) {
            return new SellerApplicationView(a.getId(), a.getUserId(), a.getStatus(), a.getReason(), a.getCreatedAt(), a.getDecidedAt());
        }
    }
    public record UserView(Long id, String email, String displayName, String status, List<String> roles) {
        public static UserView of(UserEntity u, List<String> roles) {
            return new UserView(u.getId(), u.getEmail(), u.getDisplayName(), u.getStatus().name(), roles);
        }
    }
    public record PopularProduct(Long productId, String name, long quantity, BigDecimal grossSales) {}
    public record ModeratedProduct(Long id, Long sellerId, String name, boolean hidden, Boolean active) {}
    public record Report(Instant from, Instant to, long ordersCreated, long ordersPaid, long ordersCancelled,
            long ordersRefunded, BigDecimal paymentsReceived, BigDecimal refunded, BigDecimal commissionEarned,
            BigDecimal commissionReversed, BigDecimal netCommission, List<PopularProduct> popularProducts) {}
}
