package com.example.adplatform.search.outbox.scheduler;

import com.example.adplatform.search.outbox.service.OutboxRelayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox 周期调度入口。
 *
 * <p>高频任务只负责小批量发布；历史清理固定在低峰期执行。异常在任务边界记录，避免
 * Spring 调度线程因一次依赖故障永久停止后续轮询。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayScheduler {

    private final OutboxRelayService outboxRelayService;

    /** 周期领取并发布到期消息。 */
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

    /** 每天 03:20 清理已超过保留期的 SENT 记录。 */
    @Scheduled(cron = "0 20 3 * * *")
    public void cleanup() {
        try {
            outboxRelayService.cleanupSent();
        } catch (RuntimeException ex) {
            log.warn("Search outbox cleanup failed", ex);
        }
    }
}
