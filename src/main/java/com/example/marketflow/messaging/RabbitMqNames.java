package com.example.marketflow.messaging;

public final class RabbitMqNames {
    public static final String BUSINESS_EVENTS_EXCHANGE = "marketflow.events.exchange";
    public static final String MONEY_COMMANDS_EXCHANGE = "marketflow.commands.exchange";
    public static final String DEAD_LETTER_EXCHANGE = "marketflow.dlx.exchange";

    public static final String ORDER_EVENT_HISTORY_QUEUE = "marketflow.event-history.queue";
    public static final String BUYER_NOTIFICATION_QUEUE = "marketflow.buyer-notifications.queue";
    public static final String SELLER_NOTIFICATION_QUEUE = "marketflow.seller-notifications.queue";
    public static final String SELLER_WITHDRAWAL_QUEUE = "marketflow.withdrawal.commands.queue";
    public static final String SELLER_FUNDS_RELEASE_QUEUE = "marketflow.settlement.commands.queue";
    public static final String DEAD_LETTER_QUEUE = "marketflow.dead-letter.queue";
    public static final String DEAD_LETTER_ROUTING_KEY = "dead-letter";

    // События заказа
    public static final String ORDER_CREATED_EVENT = "order.created";
    public static final String ORDER_PAID_EVENT = "order.paid";
    public static final String ORDER_CANCELLED_EVENT = "order.cancelled";
    public static final String ORDER_COMPLETED_EVENT = "order.completed";
    public static final String ORDER_REFUNDED_EVENT = "order.refunded";

    // События выполнения заказа продавцом
    public static final String SELLER_STARTED_ORDER_PROCESSING_EVENT = "seller-order.started";
    public static final String SELLER_SENT_PRODUCT_EVENT = "seller-order.sent";
    public static final String BUYER_RECEIVED_PRODUCT_EVENT = "seller-order.received";

    // Денежные события
    public static final String SELLER_PENDING_BALANCE_CREDITED_EVENT = "money.seller.pending";
    public static final String PLATFORM_COMMISSION_CREDITED_EVENT = "money.platform.commission";
    public static final String SELLER_FUNDS_RELEASED_EVENT = "money.seller.available";
    public static final String SELLER_WITHDRAWAL_COMPLETED_EVENT = "money.seller.withdrawn";
    public static final String SELLER_ACCRUAL_REVERSED_EVENT = "money.seller.returned";
    public static final String PLATFORM_COMMISSION_REVERSED_EVENT = "money.platform.returned";

    // Денежные команды
    public static final String REQUEST_SELLER_WITHDRAWAL_COMMAND = "command.withdrawal.requested";
    public static final String RELEASE_SELLER_FUNDS_COMMAND = "command.settlement.release";

    private RabbitMqNames() {
    }
}
