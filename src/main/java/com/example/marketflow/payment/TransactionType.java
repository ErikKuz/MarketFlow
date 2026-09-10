package com.example.marketflow.payment;

/** Назначение операции с виртуальными деньгами внутри MarketFlow. */
public enum TransactionType {
    PAYMENT,
    SELLER_PENDINGWALLET,
    PLATFORM_COMMISSION,
    SELLER_MAINWALLET,
    SELLER_TRANSFERMONEYFROMMAINWALLET,
    RETURNMONEY,
    SELLER_RETURNMONEY,
    PLATFORM_RETURMONEY
}
