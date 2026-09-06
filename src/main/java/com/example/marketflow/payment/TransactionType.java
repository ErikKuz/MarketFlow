package com.example.marketflow.payment;

public enum TransactionType {
    PAYMENT,
    SELLER_PAYOUT,
    PLATFORM_COMMISSION,
    REFUND,
    SELLER_PAYOUT_REVERSAL,//возврат пользователю
    PLATFORM_COMMISSION_REVERSAL,//возврат пользователю
    WITHDRAWAL
}
