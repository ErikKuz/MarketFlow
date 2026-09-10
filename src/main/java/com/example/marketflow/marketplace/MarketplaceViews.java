package com.example.marketflow.marketplace;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import com.example.marketflow.Order.*;
import com.example.marketflow.payment.PaymentStatus;

/** DTO для истории покупателя и обработки заказа продавцом. */
public final class MarketplaceViews {
    private MarketplaceViews() {}
    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageView<T> of(Page<T> page) {
            return new PageView<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }
    public record OrderSummary(Long id, OrderStatus status, PaymentStatus paymentStatus,
                               BigDecimal totalPrice, Instant createdAt) {
        public static OrderSummary of(OrderEntity order) {
            return new OrderSummary(order.getId(), order.getStatus(), order.getPaymentStatus(),
                    order.getTotalPrice(), order.getCreatedAt());
        }
    }
    public record SellerOrderView(Long id, Long orderId, Long sellerId, OBSERFFORSENDBYSELLERPRODUCTSTATUS status,
                                  BigDecimal totalAmount, BigDecimal commissionRate,
                                  BigDecimal commissionAmount, BigDecimal sellerAmount,
                                  OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS settlementStatus, Instant createdAt,
                                  Instant shippedAt, Instant deliveredAt) {
        public static SellerOrderView of(SellerOrderEntity part) {
            return new SellerOrderView(part.getId(), part.getOrderId(), part.getSellerId(), part.getStatus(),
                    part.getTotalAmount(), part.getCommissionRate(), part.getCommissionAmount(),
                    part.getSellerAmount(), part.getSettlementStatus(), part.getCreatedAt(),
                    part.getShippedAt(), part.getDeliveredAt());
        }
    }
    public record SellerOrderDetails(SellerOrderView fulfillment, PaymentStatus paymentStatus,
                                     List<OrderItemDto> items) {}
}
