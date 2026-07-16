package com.example.adplatform.search.outbox.service;

import com.example.adplatform.search.config.AdElasticsearchProperties;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayService {

    private final OutboxMessageMapper outboxMessageMapper;
    private final AdElasticsearchProperties properties;
    private final MeterRegistry meterRegistry;

    @Qualifier("outboxKafkaTemplate")
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Transactional(rollbackFor = Exception.class)
    public int publishPendingBatch() {
        long startNanos = System.nanoTime();
        List<OutboxMessageEntity> messages = outboxMessageMapper
                .lockPendingBatch(properties.getOutbox().getBatchSize());
        int sent = 0;
        for (OutboxMessageEntity message : messages) {
            try {
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

    @Transactional
    public int cleanupSent() {
        return outboxMessageMapper.deleteSentBefore(properties.getOutbox().getSentRetentionDays());
    }
}
