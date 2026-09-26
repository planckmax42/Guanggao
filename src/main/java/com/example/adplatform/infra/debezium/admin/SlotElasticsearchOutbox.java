package com.example.adplatform.infra.debezium.admin;

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
public class SlotElasticsearchOutbox {

    private final ObjectMapper objectMapper;

    private final OutboxMessageMapper outboxMessageMapper;

    @Value("${app.kafka.topics.slot-es-sync}")
    private String topic;

    public void syncElasticsearchWithRetry(String slotCode){
        OutboxMessageEntity entity = new OutboxMessageEntity();
        entity.setEventId(PublicIdGenerator.generate(PublicIdGenerator.EVENT_PREFIX));
        entity.setTopic(topic);
        entity.setMessageKey("SlotElasticsearchSync:" + slotCode);
        try {
            entity.setPayload(objectMapper.writeValueAsString(slotCode));
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.SERIALIZATION_FAILED,"广告位ES同步序列化失败",ex);
        }
        outboxMessageMapper.insert(entity);
    }
}
