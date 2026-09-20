package com.example.marketflow.messaging.command;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MarketFlowCommand(
        UUID commandId,
        CommandType commandType,
        Long transactionId,
        Long orderId,
        Long sellerOrderId,
        Long sellerId,
        Long cardId,
        BigDecimal amount,
        Instant createdAt
) {
    public MarketFlowCommand {
        if (commandId == null || commandType == null || createdAt == null) {
            throw new IllegalArgumentException("Идентификатор, тип и время команды обязательны");
        }
        if (sellerId == null || sellerId < 1 || amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Команда должна содержать продавца и положительную сумму");
        }
        if (commandType == CommandType.WITHDRAWAL_REQUESTED
                && (transactionId == null || transactionId < 1 || cardId == null || cardId < 1)) {
            throw new IllegalArgumentException("Команда вывода должна содержать транзакцию и карту");
        }
        if (commandType == CommandType.RELEASE_SELLER_FUNDS
                && (orderId == null || orderId < 1 || sellerOrderId == null || sellerOrderId < 1)) {
            throw new IllegalArgumentException("Команда освобождения должна содержать заказ и его часть");
        }
    }

    public static MarketFlowCommand withdrawal(
            Long transactionId,
            Long sellerId,
            Long cardId,
            BigDecimal amount,
            Instant createdAt
    ) {
        return new MarketFlowCommand(
                UUID.randomUUID(), CommandType.WITHDRAWAL_REQUESTED,
                transactionId, null, null, sellerId, cardId, amount, createdAt
        );
    }

    public static MarketFlowCommand releaseSellerFunds(
            Long orderId,
            Long sellerOrderId,
            Long sellerId,
            BigDecimal amount,
            Instant createdAt
    ) {
        return new MarketFlowCommand(
                UUID.randomUUID(), CommandType.RELEASE_SELLER_FUNDS,
                null, orderId, sellerOrderId, sellerId, null, amount, createdAt
        );
    }

    public enum CommandType {
        WITHDRAWAL_REQUESTED,
        RELEASE_SELLER_FUNDS
    }
}
