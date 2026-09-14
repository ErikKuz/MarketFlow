# Задача 10. RabbitMQ и события

## Статус

Реализовано.

## Путь события

```text
бизнес-сервис
→ outbox_events в общей PostgreSQL-транзакции
→ OutboxPublisher
→ marketflow.events.exchange
→ одна или несколько очередей
→ идемпотентный consumer
→ order_event_history / notifications
```

Бизнес-сервисы не вызывают `RabbitTemplate` напрямую. Если RabbitMQ временно недоступен, заказ и оплата продолжают работать, а событие остаётся в `outbox_events` для повторной отправки.

## Очереди

```text
marketflow.event-history.queue
marketflow.buyer-notifications.queue
marketflow.seller-notifications.queue
marketflow.dead-letter.queue
```

Topic exchange позволяет одному событию одновременно попасть, например, в историю и в уведомления. У каждого сообщения есть `eventId`; уникальные ограничения защищают consumers от повторной записи.

## Надёжность

- JSON вместо Java serialization;
- publisher confirms и mandatory returns;
- повторная публикация Outbox с увеличением `attempts`;
- статус `FAILED` после исчерпания попыток publisher;
- три попытки обработки listener;
- отклонённые сообщения направляются через DLX в DLQ;
- повторная доставка consumer-у не создаёт вторую историю или уведомление.

## Проверка

1. Выполнить `docker compose up -d`.
2. Открыть RabbitMQ Management: `http://localhost:15672`.
3. Запустить приложение и пройти путь заказа.
4. Проверить exchange, четыре очереди и строки `PUBLISHED` в `outbox_events`.
5. Проверить `order_event_history` и `notifications`.

Интеграционный тест `RabbitMqIntegrationTest` использует Testcontainers и автоматически пропускается, если Docker недоступен.
