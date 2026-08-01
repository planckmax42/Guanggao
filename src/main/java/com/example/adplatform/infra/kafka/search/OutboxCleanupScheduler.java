package com.example.adplatform.infra.kafka.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Outbox 历史清理任务；它不承担消息发布，因此 Debezium 模式下仍然保留。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxCleanupScheduler {

    private final OutboxCleanupService outboxCleanupService;

    @Scheduled(cron = "${app.elasticsearch.outbox.cleanup-cron:0 20 3 * * *}")
    public void cleanup() {
        try {
            int deleted = outboxCleanupService.cleanupExpired();
            if (deleted > 0) {
                log.info("Cleaned expired search outbox messages, count={}", deleted);
            }
        } catch (RuntimeException ex) {
            log.warn("Search outbox cleanup failed", ex);
        }
    }
}
