package com.example.marketflow.marketplace;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;

import com.example.marketflow.Order.OrderEntity;
import com.example.marketflow.Order.OrderItemDto;
import com.example.marketflow.Order.OrderStatus;
import com.example.marketflow.payment.PaymentStatus;

//DTO для истории покупателя и обработки заказа продавцом.
public final class MarketplaceViews {
    private MarketplaceViews() {}
    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {//Хранит одну страницу рез
        public static <T> PageView<T> of(Page<T> page) {
            return new PageView<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }
    //Хранит краткую информацию о заказе для списка «Мои заказы»
    public record ShortInfoAboutMyOrderInListOrder(Long id, OrderStatus status, PaymentStatus paymentStatus,
                               BigDecimal totalPrice, Instant createdAt) {
        public static ShortInfoAboutMyOrderInListOrder of(OrderEntity order) {
            return new ShortInfoAboutMyOrderInListOrder(order.getId(), order.getStatus(), order.getPaymentStatus(),
                    order.getTotalPrice(), order.getCreatedAt());
        }
    }

    //Хранит информацию о части заказа конкретного продавца
    public record SellerOrderSpecific(Long id, Long orderId, Long sellerId, OBSERFFORSENDBYSELLERPRODUCTSTATUS status,
                                  BigDecimal totalAmount, BigDecimal commissionRate,
                                  BigDecimal commissionAmount, BigDecimal sellerAmount,
                                  OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS settlementStatus, Instant createdAt,
                                  Instant shippedAt, Instant deliveredAt) {
        public static SellerOrderSpecific of(SellerOrderEntity part) {
            return new SellerOrderSpecific(part.getId(), part.getOrderId(), part.getSellerId(), part.getStatus(),
                    part.getTotalAmount(), part.getCommissionRate(), part.getCommissionAmount(),
                    part.getSellerAmount(), part.getSettlementStatus(), part.getCreatedAt(),
                    part.getShippedAt(), part.getDeliveredAt());
        }
    }

    //Хранит полную информацию для страницы продавца
    public record SellerOrderDetails(SellerOrderSpecific fulfillment, PaymentStatus paymentStatus,
                                     List<OrderItemDto> items) {}
}
