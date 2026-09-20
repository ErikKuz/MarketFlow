package com.example.marketflow.messaging.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.marketflow.marketplace.OrderWorkflowService;
import com.example.marketflow.messaging.RabbitMqNames;
import com.example.marketflow.messaging.command.MarketFlowCommand;
import com.example.marketflow.service.WalletService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
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
        walletService.processWithdrawal(command);
    }

    @RabbitListener(queues = RabbitMqNames.SELLER_FUNDS_RELEASE_QUEUE)
    public void releaseSellerFunds(MarketFlowCommand command) {
        orderWorkflowService.releaseSellerFunds(command);
    }
}
