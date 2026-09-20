package com.example.marketflow.messaging.listener;

import java.time.Clock;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.marketflow.Repository.NotificationRepository;
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
public class BuyerNotificationListener {
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    @RabbitListener(queues = RabbitMqNames.BUYER_NOTIFICATION_QUEUE)
    @Transactional
    public void handle(MarketFlowEvent event) {
        if (event.buyerId() == null
                || notificationRepository.existsByEventIdAndUserIdAndRecipientType(
                        event.eventId(), event.buyerId(), "BUYER")) {
            return;
        }
        String title = switch (event.eventType()) {
            case ORDER_CREATED -> "Заказ создан";
            case ORDER_PAID -> "Заказ оплачен";
            case SELLER_SENT_PRODUCT -> "Продавец отправил товар";
            case ORDER_COMPLETED -> "Заказ завершён";
            case ORDER_CANCELLED -> "Заказ отменён";
            case ORDER_REFUNDED -> "Деньги возвращены";
            default -> null;
        };
        if (title != null) {
            notificationRepository.save(new NotificationEntity(
                    event.eventId(), event.buyerId(), "BUYER", title,
                    message(title, event), clock.instant()
            ));
        }
    }

    private String message(String title, MarketFlowEvent event) {
        return event.orderId() == null ? title : title + ". Заказ №" + event.orderId();
    }
}
