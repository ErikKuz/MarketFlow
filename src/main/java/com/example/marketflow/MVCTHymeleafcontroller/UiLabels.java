package com.example.marketflow.MVCTHymeleafcontroller;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.example.marketflow.Order.OrderStatus;
import com.example.marketflow.User.UserStatus;
import com.example.marketflow.marketplace.OBSERFFORSENDBYSELLERPRODUCTSTATUS;
import com.example.marketflow.marketplace.OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS;
import com.example.marketflow.payment.PaymentStatus;
import com.example.marketflow.payment.TransactionStatus;
import com.example.marketflow.payment.TransactionType;

@Component("uiLabels")
public class UiLabels {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    public String orderStatus(OrderStatus status) {
        if (status == null) return "—";
        return switch (status) {
            case CREATED -> "Создан";
            case CONFIRMED -> "Подтверждён";
            case SELLERSSTARTWORK -> "Продавцы собирают заказ";
            case SELLERSENDWORKANDSEND -> "Передан в доставку";
            case COMPLETED -> "Завершён";
            case CANCELLED -> "Отменён";
        };
    }

    public String paymentStatus(PaymentStatus status) {
        if (status == null) return "—";
        return switch (status) {
            case NOT_PAID -> "Не оплачено";
            case PROCESSING -> "Оплата обрабатывается";
            case PAID -> "Оплачено";
            case FAILED -> "Ошибка оплаты";
            case REFUNDED -> "Деньги возвращены";
        };
    }

    public String fulfillmentStatus(OBSERFFORSENDBYSELLERPRODUCTSTATUS status) {
        if (status == null) return "—";
        return switch (status) {
            case NEW -> "Ожидает обработки";
            case PROCESSING -> "Продавец собирает товар";
            case SELLERSENDPRODUCT -> "Передан в доставку";
            case USERGETPRODUCT -> "Получен покупателем";
            case CANCELLED -> "Отменён";
            case RETURNED -> "Возвращён";
        };
    }

    public String settlementStatus(OBSERFFORSENDFROMUSERMONEYINSELLERSTATUS status) {
        if (status == null) return "—";
        return switch (status) {
            case NOT_DISTRIBUTE -> "Ещё не распределены";
            case PENDINGWALLET -> "Ожидают подтверждения получения";
            case MAINWALLET -> "Доступны продавцу";
            case RETURNMONEY -> "Возвращены покупателю";
        };
    }

    public String transactionType(TransactionType type) {
        if (type == null) return "—";
        return switch (type) {
            case PAYMENT -> "Оплата заказа";
            case SELLER_PENDINGWALLET -> "Начисление в ожидающий баланс";
            case PLATFORM_COMMISSION -> "Комиссия платформы";
            case SELLER_MAINWALLET -> "Перевод в доступный баланс";
            case SELLER_TRANSFERMONEYFROMMAINWALLET -> "Вывод на карту";
            case RETURNMONEY -> "Возврат покупателю";
            case SELLER_RETURNMONEY -> "Отмена начисления продавцу";
            case PLATFORM_RETURMONEY -> "Отмена комиссии платформы";
        };
    }

    public String transactionStatus(TransactionStatus status) {
        if (status == null) return "—";
        return switch (status) {
            case PENDING -> "В обработке";
            case COMPLETED -> "Выполнено";
            case FAILED -> "Ошибка";
            case REFUNDED -> "Возвращено";
            case REVERSED -> "Отменено";
        };
    }

    public String userStatus(UserStatus status) {
        if (status == null) return "—";
        return switch (status) {
            case ACTIVE -> "Активен";
            case BLOCKED -> "Заблокирован";
            case DELETED -> "Удалён";
        };
    }

    public String dateTime(Instant value) {
        return value == null ? "—" : DATE_TIME.format(value);
    }
}
