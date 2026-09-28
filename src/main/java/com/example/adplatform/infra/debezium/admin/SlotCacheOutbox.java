package com.example.adplatform.infra.debezium.admin;

import com.example.adplatform.admin.port.slot.SlotCacheDebeziumPort;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.common.id.PublicIdGenerator;
import com.example.adplatform.search.outbox.entity.OutboxMessageEntity;
import com.example.adplatform.search.outbox.mapper.OutboxMessageMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SlotCacheOutbox implements SlotCacheDebeziumPort {

    @Value("${app.kafka.topics.slot-cache-write}")
    private String topicWrite;

    @Value("${app.kafka.topics.slot-cache-update}")
    private String updateTopic;

    @Value("${app.kafka.topics.slot-cache-delete}")
    private String delectTopic;

    private final ObjectMapper objectMapper;

    private final OutboxMessageMapper outboxMessageMapper;

    public void writeCacheWithRetry(Long slotId, String slotCode){
        SlotCacheWritePayload payload = new SlotCacheWritePayload(slotId,slotCode);
        OutboxMessageEntity entity = new OutboxMessageEntity();
        entity.setEventId(PublicIdGenerator.generate(PublicIdGenerator.EVENT_PREFIX));
        entity.setTopic(topicWrite);
        entity.setMessageKey("SlotCacheWrite:" + slotId);
        try {
            entity.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.SERIALIZATION_FAILED,"广告位缓存写入序列化失败",ex);
        }
        outboxMessageMapper.insert(entity);
    }
    public void updateCacheWithRetry(Long slotId, String oldSlotCode, String newSlotCode){
        SlotCacheUpdatePayload payload = new SlotCacheUpdatePayload(slotId,oldSlotCode,newSlotCode);
        OutboxMessageEntity entity = new OutboxMessageEntity();
        entity.setEventId(PublicIdGenerator.generate(PublicIdGenerator.EVENT_PREFIX));
        entity.setTopic(updateTopic);
        entity.setMessageKey("SlotCacheUpdate:" + slotId);
        try {
            entity.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.SERIALIZATION_FAILED,"广告位缓存更新序列化失败",ex);
        }
        outboxMessageMapper.insert(entity);
    }

    public void evictCacheWithRetry(String slotCode){
        SlotCacheEvictPayload payload = new SlotCacheEvictPayload( slotCode);
        OutboxMessageEntity entity = new OutboxMessageEntity();
        entity.setEventId(PublicIdGenerator.generate(PublicIdGenerator.EVENT_PREFIX));
        entity.setTopic(delectTopic);
        entity.setMessageKey("SlotCacheDelete:" + slotCode);
        try {
            entity.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.SERIALIZATION_FAILED,"广告位缓存删除序列化失败",ex);
        }
        outboxMessageMapper.insert(entity);
    }
}
