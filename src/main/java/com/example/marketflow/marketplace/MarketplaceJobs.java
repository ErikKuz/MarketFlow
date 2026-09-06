package com.example.marketflow.marketplace;

import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;
import com.example.marketflow.Repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name = "marketflow.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class MarketplaceJobs {
    private final OrderRepository orders;
    private final OrderMaintenanceService maintenance;
    private final Clock clock;

    @Scheduled(initialDelayString = "${marketflow.jobs.delay-ms:60000}", fixedDelayString = "${marketflow.jobs.delay-ms:60000}")
    public void processDeadlines() {
        // Each order uses its own transaction in another bean; one failure does not abort the batch.
        for (Long id : orders.findExpiredIds(clock.instant(), PageRequest.of(0, 100))) {
            try { maintenance.expireOne(id); }
            catch (RuntimeException e) { log.error("Failed to expire order {}", id, e); }
        }
        for (Long id : orders.findSettlementIds(clock.instant(), PageRequest.of(0, 100))) {
            try { maintenance.settleOne(id); }
            catch (RuntimeException e) { log.error("Failed to settle order {}", id, e); }
        }
    }
}
