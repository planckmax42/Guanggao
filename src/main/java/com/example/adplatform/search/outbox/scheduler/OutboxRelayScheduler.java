package com.example.adplatform.search.outbox.scheduler;

import com.example.adplatform.search.outbox.service.OutboxRelayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 应用轮询式 Outbox Relay，仅作为 Debezium 不可用时的显式回退方案。
 *
 * <p>默认 transport=debezium 时不会创建此 Bean，避免应用和 Debezium 重复发布同一行。
 * 异常在任务边界记录，避免 Spring 调度线程因一次依赖故障永久停止后续轮询。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.elasticsearch.outbox",
        name = "transport",
        havingValue = "polling")
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

}
