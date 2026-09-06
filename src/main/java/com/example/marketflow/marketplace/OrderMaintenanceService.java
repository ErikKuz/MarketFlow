package com.example.marketflow.marketplace;

import java.time.Clock;
import java.util.Comparator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.marketflow.Order.OrderStatus;
import com.example.marketflow.Repository.*;
import com.example.marketflow.payment.*;
import com.example.marketflow.service.OrderService;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class OrderMaintenanceService {
    private final OrderRepository orders;
    private final OrderService orderService;
    private final PaymentTransactionRepository transactions;
    private final WalletAccountRepository wallets;
    private final ReturnRequestRepository returns;
    private final OrderWorkflowService workflow;
    private final Clock clock;

    @Transactional
    public void expireOne(Long id) {
        var order = orders.findLocked(id).orElseThrow();
        if (order.getStatus() == OrderStatus.CREATED && order.paymentExpired(clock.instant())
                && (order.getPaymentStatus() == PaymentStatus.NOT_PAID || order.getPaymentStatus() == PaymentStatus.FAILED)) {
            orderService.cancelOrder(id, order.getBuyerId(), null, "PAYMENT_DEADLINE_EXPIRED");
        }
    }

    @Transactional
    public void settleOne(Long id) {
        var order = orders.findLocked(id).orElseThrow();
        if (order.isFundsReleased() || order.getStatus() != OrderStatus.COMPLETED
                || order.getReturnDeadline() == null || clock.instant().isBefore(order.getReturnDeadline())) return;
        if (returns.existsByOrderIdAndStatus(id, ReturnRequestEntity.Status.PENDING)) return;
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            var held = transactions.findAllByOrderId(id).stream()
                    .filter(p -> p.isPending() && p.getStatus() == TransactionStatus.COMPLETED)
                    .sorted(Comparator.comparing(PaymentTransactionEntity::getUserId).thenComparing(PaymentTransactionEntity::getId)).toList();
            for (var entry : held) {
                if (wallets.releasePendingBalance(entry.getUserId(), entry.getAmount()) != 1)
                    throw MarketplaceException.conflict("Held balance is inconsistent");
                entry.makeAvailable();
            }
            workflow.record(null, id, null, "FUNDS_RELEASED", "PENDING", "AVAILABLE", "Return window closed");
        }
        order.releaseFunds();
    }
}
