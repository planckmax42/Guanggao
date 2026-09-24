package com.example.adplatform.infra.debezium.admin;

import com.example.adplatform.admin.port.slot.SlotDebeziumPort;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.common.id.PublicIdGenerator;
import com.example.adplatform.search.outbox.entity.OutboxMessageEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SlotCacheOutbox implements SlotDebeziumPort {

    @Value("${app.kafka.topics.slot-cache-write}")
    private String topicWrite;

    @Value("${app.kafka.topics.slot-cache-update}")
    private String topicUpdate;

    private final ObjectMapper objectMapper;

    public void writeRedisWithRetry(Long slotId,String slotCode){
        SlotCacheWritePayload payload = new SlotCacheWritePayload(slotId,slotCode);
        OutboxMessageEntity entity = new OutboxMessageEntity();
        entity.setEventId(PublicIdGenerator.generate(PublicIdGenerator.EVENT_PREFIX));
        entity.setTopic(topicWrite);
        entity.setMessageKey("SLOT:" + slotId);
        entity.setMessageType(SlotCacheWritePayload.class.getSimpleName());
        try {
            entity.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.SERIALIZATION_FAILED,"广告位缓存创建序列化失败",ex);
        }
    }
    public void updateRedisWithRetry(Long slotId,String oldSlotCode,String newSlotCode){
        SlotCacheUpdatePayload payload = new SlotCacheUpdatePayload(slotId,oldSlotCode,newSlotCode);
        OutboxMessageEntity entity = new OutboxMessageEntity();
        entity.setEventId(PublicIdGenerator.generate(PublicIdGenerator.EVENT_PREFIX));
        entity.setTopic(topicUpdate);
        entity.setMessageKey("SLOT:" + slotId);
        entity.setMessageType(SlotCacheUpdatePayload.class.getSimpleName());
        try {
            entity.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.SERIALIZATION_FAILED,"广告位缓存更新序列化失败",ex);
        }
    }
}
