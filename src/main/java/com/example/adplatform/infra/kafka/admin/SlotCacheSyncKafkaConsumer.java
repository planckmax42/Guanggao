package com.example.adplatform.infra.kafka.admin;

import com.example.adplatform.admin.port.slot.SlotCachePort;
import com.example.adplatform.search.outbox.message.SlotCacheSyncMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** 消费广告位缓存 Outbox 消息，以 MySQL 当前状态幂等收敛 Redis。 */
@Component
@RequiredArgsConstructor
public class SlotCacheSyncKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final SlotCachePort slotCachePort;
    private final MeterRegistry meterRegistry;

    @KafkaListener(
            topics = "${app.kafka.topics.slot-cache-sync}",
            groupId = "${app.kafka.consumer-groups.slot-cache-sync}",
            containerFactory = "slotCacheKafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record) throws Exception {
        SlotCacheSyncMessage message = objectMapper.readValue(record.value(), SlotCacheSyncMessage.class);
        validate(message);
        slotCachePort.reconcileSlot(message.slotPublicId(), message.previousSlotCode());
        meterRegistry.counter("ad.slot.cache.sync", "stage", "consumer", "result", "success")
                .increment();
        long delayMillis = Math.max(0L, System.currentTimeMillis() - record.timestamp());
        meterRegistry.timer("ad.slot.cache.sync.delay").record(Duration.ofMillis(delayMillis));
    }

    private void validate(SlotCacheSyncMessage message) {
        if (message.eventId() == null || message.eventId().isBlank()
                || message.slotPublicId() == null || message.slotPublicId().isBlank()) {
            throw new IllegalArgumentException("Invalid slot cache sync message");
        }
    }
}
