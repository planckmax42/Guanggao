package com.example.adplatform.search.outbox.service;

import com.example.adplatform.common.id.PublicIdGenerator;
import com.example.adplatform.search.outbox.entity.OutboxMessageEntity;
import com.example.adplatform.search.outbox.mapper.OutboxMessageMapper;
import com.example.adplatform.search.outbox.message.SlotCacheSyncMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 在广告位业务事务内写入 Redis 缓存同步 Outbox。 */
@Service
@RequiredArgsConstructor
public class SlotCacheOutboxService {

    private final OutboxMessageMapper outboxMessageMapper;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.slot-cache-sync}")
    private String topic;

    public void append(String slotPublicId, String previousSlotCode) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Slot cache outbox must be appended in a transaction");
        }
        String eventId = PublicIdGenerator.generate(PublicIdGenerator.EVENT_PREFIX);
        SlotCacheSyncMessage message = new SlotCacheSyncMessage(eventId, slotPublicId, previousSlotCode);
        OutboxMessageEntity entity = new OutboxMessageEntity();
        entity.setEventId(eventId);
        entity.setTopic(topic);
        entity.setMessageKey("SLOT:" + slotPublicId);
        entity.setMessageType(SlotCacheSyncMessage.class.getSimpleName());
        try {
            entity.setPayload(objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize slot cache outbox message", ex);
        }
        outboxMessageMapper.insert(entity);
    }
}
