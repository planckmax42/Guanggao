package com.example.adplatform.search.outbox.scheduler;

import com.example.adplatform.search.outbox.service.OutboxRelayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final OutboxRelayService outboxRelayService;

    @Scheduled(
            fixedDelayString = "${app.elasticsearch.outbox.publish-delay-ms:500}",
            initialDelayString = "${app.elasticsearch.outbox.publish-initial-delay-ms:3000}")
    public void publish() {
        try {
            outboxRelayService.publishPendingBatch();
        } catch (RuntimeException ex) {
            log.warn("Search outbox publish batch failed", ex);
        }
    }

    @Scheduled(cron = "0 20 3 * * *")
    public void cleanup() {
        try {
            outboxRelayService.cleanupSent();
        } catch (RuntimeException ex) {
            log.warn("Search outbox cleanup failed", ex);
        }
    }
}
