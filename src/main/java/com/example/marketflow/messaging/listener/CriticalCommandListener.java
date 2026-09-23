package com.example.marketflow.messaging.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.marketflow.marketplace.OrderWorkflowService;
import com.example.marketflow.messaging.RabbitMqNames;
import com.example.marketflow.messaging.command.MarketFlowCommand;
import com.example.marketflow.service.WalletService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = "spring.rabbitmq.listener.simple.auto-startup",
        havingValue = "true",
        matchIfMissing = true
)
public class CriticalCommandListener {
    private final WalletService walletService;
    private final OrderWorkflowService orderWorkflowService;

    @RabbitListener(queues = RabbitMqNames.SELLER_WITHDRAWAL_QUEUE)
    public void processWithdrawal(MarketFlowCommand command) {
        log.info(
                "Withdrawal command received: commandId={}, transactionId={}, sellerId={}",
                command.commandId(), command.transactionId(), command.sellerId()
        );
        try {
            walletService.processWithdrawal(command);
        } catch (RuntimeException exception) {
            log.error(
                    "Withdrawal command failed: commandId={}, transactionId={}, sellerId={}",
                    command.commandId(), command.transactionId(), command.sellerId(), exception
            );
            throw exception;
        }
    }

    @RabbitListener(queues = RabbitMqNames.SELLER_FUNDS_RELEASE_QUEUE)
    public void releaseSellerFunds(MarketFlowCommand command) {
        log.info(
                "Funds release command received: commandId={}, orderId={}, sellerOrderId={}, sellerId={}",
                command.commandId(), command.orderId(), command.sellerOrderId(), command.sellerId()
        );
        try {
            orderWorkflowService.releaseSellerFunds(command);
        } catch (RuntimeException exception) {
            log.error(
                    "Funds release command failed: commandId={}, orderId={}, sellerOrderId={}, sellerId={}",
                    command.commandId(), command.orderId(), command.sellerOrderId(), command.sellerId(), exception
            );
            throw exception;
        }
    }
}
