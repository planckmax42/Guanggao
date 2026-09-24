package com.example.adplatform.infra.kafka.admin;

import com.example.adplatform.infra.debezium.admin.SlotCacheUpdatePayload;
import com.example.adplatform.infra.debezium.admin.SlotCacheWritePayload;
import com.example.adplatform.infra.kafka.admin.port.SlotCacheKafkaPort;
import com.example.adplatform.infra.redis.delivery.slot.SlotCacheLockManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


/** 消费广告位缓存 Outbox 消息，以 MySQL 当前状态幂等收敛 Redis。 */
@Component
@RequiredArgsConstructor
public class SlotCacheConsumer {

    private final ObjectMapper objectMapper;//后续再加入MeterRegistry和锁

    private final SlotCacheKafkaPort slotCacheKafkaPort;

    private final SlotCacheLockManager lockManager;

    @KafkaListener(
            topics = "${app.kafka.topics.slot-cache-write}",
            groupId = "${app.kafka.consumer-groups.slot-cache-write}",
            containerFactory = "slotCacheKafkaListenerContainerFactory")
    public void consumeWrite(ConsumerRecord<String, String> record) throws Exception {
         SlotCacheWritePayload payload = objectMapper.readValue(record.value(), SlotCacheWritePayload.class);
         slotCacheKafkaPort.writeSlotToRedis(payload.Id(),payload.slotCode());
    }

    @KafkaListener(
            topics = "${app.kafka.topics.slot-cache-update}",
            groupId = "${app.kafka.consumer-groups.slot-cache-update}",
            containerFactory = "slotCacheKafkaListenerContainerFactory")
    public void consumeUpdate(ConsumerRecord<String, String> record) throws Exception {
        SlotCacheUpdatePayload payload = objectMapper.readValue(record.value(), SlotCacheUpdatePayload.class);
        slotCacheKafkaPort.evictSlotCodeFromRedis(payload.oldSlotCode());
        slotCacheKafkaPort.writeSlotToRedis(payload.Id(),payload.newSlotCode());
    }

}
//    @Override
//    public void reconcileSlot(String slotPublicId, String previousSlotCode) {
//        long startNanos = System.nanoTime();
//        try {
//            SlotEntity current = slotMapper.selectOne(new LambdaQueryWrapper<SlotEntity>()
//                    .eq(SlotEntity::getPublicId, slotPublicId));
//            String currentSlotCode = current == null ? null : current.getSlotCode();
//            try (LockAcquireAttempt ignored =
//                         lockManager.acquireForWrite(previousSlotCode, currentSlotCode)) {
//                if (StringUtils.hasText(previousSlotCode)
//                        && !Objects.equals(previousSlotCode, currentSlotCode)) {
//                    evictSlotCodeFromRedis(previousSlotCode);
//                }
//                if (current != null && Objects.equals(current.getStatus(), CommonStatus.ENABLED)) {
//                    slotBloomOperationsService.addSlotFilter(currentSlotCode);
//                    writeSlotToRedis(current.getId(), currentSlotCode);
//                } else if (current != null) {
//                    evictSlotCodeFromRedis(currentSlotCode);
//                }
//            }
//            recordOperation("reconcile", "success", startNanos);
//        } catch (RuntimeException ex) {
//            recordOperation("reconcile", "failure", startNanos);
//            throw ex;
//        }
//    }