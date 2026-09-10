# Задача 10. RabbitMQ и события

## Проблема

Создание заказа, уведомления, аналитика и действия продавца не должны быть жёстко связаны одним большим синхронным методом.

## Для чего это нужно

RabbitMQ позволяет публиковать факт произошедшего события и независимо обрабатывать его несколькими потребителями.

RabbitMQ подключается после того, как заказ, оплата и статусы уже работают синхронно.

## События

```text
OrderCreatedEvent
OrderPaidEvent
SellerOrderAcceptedEvent
OrderShippedEvent
OrderDeliveredEvent
OrderCancelledEvent
RefundCompletedEvent
```

Пример:

```java
public record OrderStatusChangedEvent(
        UUID eventId,
        Long orderId,
        Long sellerOrderId,
        String previousStatus,
        String currentStatus,
        Instant occurredAt
) {
}
```

## Что создать

```text
config/RabbitMqConfig.java
event/OrderStatusChangedEvent.java
messaging/OrderEventPublisher.java
messaging/OrderEventListener.java
```

## Очереди

```text
order.created.queue
order.paid.queue
order.status.queue
notification.queue
analytics.queue
order.dead-letter.queue
```

## Надёжность

- Сообщения передавать в JSON, а не через Java serialization.
- У каждого события должен быть уникальный `eventId`.
- Consumer должен быть идемпотентным.
- Настроить retry и Dead Letter Queue.
- Для согласованности БД и RabbitMQ позднее применить Transactional Outbox.

## Задержка доставки

Для учебной имитации `SELLERSENDPRODUCT -> USERGETPRODUCT` использовать TTL + Dead Letter Exchange. Перед автоматическим переходом повторно проверить текущий статус.

В production статус `USERGETPRODUCT` обычно приходит от службы доставки, а не устанавливается только по таймеру.

