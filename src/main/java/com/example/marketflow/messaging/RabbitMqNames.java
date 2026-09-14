package com.example.marketflow.messaging;

public final class RabbitMqNames {
    public static final String EVENTS_EXCHANGE = "marketflow.events.exchange";
    public static final String DEAD_LETTER_EXCHANGE = "marketflow.dlx.exchange";

    public static final String EVENT_HISTORY_QUEUE = "marketflow.event-history.queue";
    public static final String BUYER_NOTIFICATIONS_QUEUE = "marketflow.buyer-notifications.queue";
    public static final String SELLER_NOTIFICATIONS_QUEUE = "marketflow.seller-notifications.queue";
    public static final String DEAD_LETTER_QUEUE = "marketflow.dead-letter.queue";
    public static final String DEAD_LETTER_ROUTING_KEY = "dead-letter";

    //ROUTING KEY
    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_PAID = "order.paid";
    public static final String ORDER_CANCELLED = "order.cancelled";
    public static final String ORDER_COMPLETED = "order.completed";
    public static final String ORDER_REFUNDED = "order.refunded";

    public static final String SELLER_STARTED_WORK = "seller-order.started";
    public static final String SELLER_SENT_PRODUCT = "seller-order.sent";
    public static final String USER_RECEIVED_PRODUCT = "seller-order.received";

    public static final String SELLER_MONEY_PENDING = "money.seller.pending";
    public static final String PLATFORM_COMMISSION_ADDED = "money.platform.commission";
    public static final String SELLER_MONEY_AVAILABLE = "money.seller.available";
    public static final String SELLER_WITHDRAWAL_COMPLETED = "money.seller.withdrawn";
    public static final String SELLER_MONEY_RETURNED = "money.seller.returned";
    public static final String PLATFORM_COMMISSION_RETURNED = "money.platform.returned";

    private RabbitMqNames() {
    }
}
