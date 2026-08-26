package com.example.adplatform.infra.kafka.admin;

import com.example.adplatform.infra.elasticsearch.delivery.Candidate.EsProperties;
import com.example.adplatform.search.outbox.entity.OutboxMessageEntity;
import com.example.adplatform.search.outbox.mapper.OutboxMessageMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 轮询回退模式下将 MySQL Outbox 可靠转发到 Kafka。
 *
 * <p>每批记录在数据库事务内通过 {@code FOR UPDATE SKIP LOCKED} 领取，允许多个应用实例
 * 并行工作而不处理同一行。收到 broker 确认后才标记 SENT；进程若在确认与标记之间
 * 崩溃，消息可能重复发送，所以下游消费者必须保持幂等。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayService {

    private final OutboxMessageMapper outboxMessageMapper;
    private final EsProperties properties;
    private final MeterRegistry meterRegistry;

    @Qualifier("outboxKafkaTemplate")
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * 发布一批到期的 PENDING 消息，失败记录按指数退避留待后续轮询。
     *
     * @return 本批收到 Kafka 确认的消息数
     */
    @Transactional(rollbackFor = Exception.class)
    public int publishPendingBatch() {
        long startNanos = System.nanoTime();
        List<OutboxMessageEntity> messages = outboxMessageMapper
                .lockPendingBatch(properties.getOutbox().getBatchSize());
        int sent = 0;
        for (OutboxMessageEntity message : messages) {
            try {
                // 等待 broker ack 后再更新 MySQL，不能使用 fire-and-forget。
                kafkaTemplate.send(message.getTopic(), message.getMessageKey(), message.getPayload())
                        .get(3, TimeUnit.SECONDS);
                outboxMessageMapper.markSent(message.getId());
                meterRegistry.counter("ad.search.outbox.publish", "result", "sent", "topic", message.getTopic())
                        .increment();
                sent++;
            } catch (Exception ex) {
                log.warn("Search outbox publish failed, id={}, topic={}, retryCount={}",
                        message.getId(), message.getTopic(), message.getRetryCount(), ex);
                outboxMessageMapper.scheduleRetry(message.getId());
                meterRegistry.counter("ad.search.outbox.publish", "result", "retry", "topic", message.getTopic())
                        .increment();
            }
        }
        meterRegistry.timer("ad.search.outbox.batch.duration")
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
        return sent;
    }

}
