package com.example.marketflow.messaging.listener;

import java.time.Clock;
import java.math.BigDecimal;
import java.util.List;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.marketflow.Repository.NotificationRepository;
import com.example.marketflow.marketplace.SellerOrderRepository;
import com.example.marketflow.messaging.MarketFlowEvent;
import com.example.marketflow.messaging.RabbitMqNames;
import com.example.marketflow.messaging.notification.NotificationEntity;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "spring.rabbitmq.listener.simple.auto-startup",
        havingValue = "true",
        matchIfMissing = true
)
public class SellerNotificationListener {
    private final NotificationRepository notificationRepository;
    private final SellerOrderRepository sellerOrderRepository;
    private final Clock clock;

    @RabbitListener(queues = RabbitMqNames.SELLER_NOTIFICATION_QUEUE)
    @Transactional
    public void handle(MarketFlowEvent event) {
        String title = switch (event.eventType()) {
            case ORDER_PAID -> "Поступил оплаченный заказ";
            case USER_RECEIVED_PRODUCT -> "Покупатель получил товар";
            case SELLER_MONEY_PENDING -> "Деньги начислены в ожидающий кошелёк";
            case SELLER_MONEY_AVAILABLE -> "Деньги доступны для вывода";
            case SELLER_WITHDRAWAL_COMPLETED -> "Вывод денег выполнен";
            case SELLER_MONEY_RETURNED -> "Начисление продавцу отменено";
            case ORDER_COMPLETED -> "Заказ завершён";
            case ORDER_CANCELLED -> "Заказ отменён";
            case ORDER_REFUNDED -> "Оплата заказа возвращена";
            default -> null;
        };
        if (title == null) {
            return;
        }
        for (Long sellerId : recipients(event)) {
            if (!notificationRepository.existsByEventIdAndUserIdAndRecipientType(
                    event.eventId(), sellerId, "SELLER")) {
                notificationRepository.save(new NotificationEntity(
                        event.eventId(), sellerId, "SELLER", title,
                        message(title, event, sellerId), clock.instant()
                ));
            }
        }
    }

    private List<Long> recipients(MarketFlowEvent event) {
        if (event.sellerId() != null) {
            return List.of(event.sellerId());
        }
        if (event.orderId() == null) {
            return List.of();
        }
        return sellerOrderRepository.findAllByOrderIdOrderBySellerId(event.orderId()).stream()
                .map(part -> part.getSellerId())
                .distinct()
                .toList();
    }

    private String message(String title, MarketFlowEvent event, Long sellerId) {
        String order = event.orderId() == null ? "" : ". Заказ №" + event.orderId();
        String part = event.sellerOrderId() == null ? "" : ", часть заказа №" + event.sellerOrderId();
        BigDecimal notificationAmount = event.amount();

        if (event.eventType() == com.example.marketflow.messaging.MarketFlowEventType.ORDER_PAID
                && event.orderId() != null) {
            var sellerPart = sellerOrderRepository.findAllByOrderIdOrderBySellerId(event.orderId()).stream()
                    .filter(value -> value.getSellerId().equals(sellerId))
                    .findFirst();
            if (sellerPart.isPresent()) {
                part = ", ваша часть №" + sellerPart.get().getId();
                notificationAmount = sellerPart.get().getTotalAmount();
            }
        }

        boolean showAmount = switch (event.eventType()) {
            case ORDER_PAID, SELLER_MONEY_PENDING, SELLER_MONEY_AVAILABLE,
                    SELLER_WITHDRAWAL_COMPLETED, SELLER_MONEY_RETURNED -> true;
            default -> false;
        };
        String amount = showAmount && notificationAmount != null
                ? ", сумма " + notificationAmount
                : "";
        return title + order + part + amount;
    }
}
