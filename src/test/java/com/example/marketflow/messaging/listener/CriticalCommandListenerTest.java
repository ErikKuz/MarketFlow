package com.example.marketflow.messaging.listener;

import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.example.marketflow.marketplace.OrderWorkflowService;
import com.example.marketflow.messaging.command.MarketFlowCommand;
import com.example.marketflow.service.WalletService;

class CriticalCommandListenerTest {

    @Test
    void sendsWithdrawalCommandToWalletService() {
        WalletService walletService = org.mockito.Mockito.mock(WalletService.class);
        OrderWorkflowService workflowService = org.mockito.Mockito.mock(OrderWorkflowService.class);
        CriticalCommandListener listener = new CriticalCommandListener(walletService, workflowService);
        MarketFlowCommand command = MarketFlowCommand.withdrawal(
                81L, 7L, 15L, new BigDecimal("60.00"), Instant.parse("2026-09-15T10:00:00Z")
        );

        listener.processWithdrawal(command);

        verify(walletService).processWithdrawal(command);
    }

    @Test
    void sendsSettlementCommandToOrderWorkflowService() {
        WalletService walletService = org.mockito.Mockito.mock(WalletService.class);
        OrderWorkflowService workflowService = org.mockito.Mockito.mock(OrderWorkflowService.class);
        CriticalCommandListener listener = new CriticalCommandListener(walletService, workflowService);
        MarketFlowCommand command = MarketFlowCommand.releaseSellerFunds(
                42L, 31L, 7L, new BigDecimal("90.00"), Instant.parse("2026-09-15T10:00:00Z")
        );

        listener.releaseSellerFunds(command);

        verify(workflowService).releaseSellerFunds(command);
    }
}
