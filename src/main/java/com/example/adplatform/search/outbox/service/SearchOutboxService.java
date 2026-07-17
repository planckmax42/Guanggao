package com.example.adplatform.search.outbox.service;

import com.example.adplatform.search.outbox.entity.OutboxMessageEntity;
import com.example.adplatform.search.outbox.mapper.OutboxMessageMapper;
import com.example.adplatform.search.outbox.message.ConfigAggregateType;
import com.example.adplatform.search.outbox.message.ConfigChangeMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 在业务事务内追加搜索链路 Outbox 消息。
 *
 * <p>调用方必须是管理配置的 {@code @Transactional} 方法，使配置数据和 outbox_message
 * 同时提交或同时回滚。该服务只写 MySQL，不直接调用 Kafka，从而消除“数据库成功但
 * 消息发送失败”的双写不一致窗口。</p>
 */
@Service
@RequiredArgsConstructor
public class SearchOutboxService {

    private final OutboxMessageMapper outboxMessageMapper;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.config-change}")
    private String configChangeTopic;

    /**
     * 追加配置变更消息。消息 key 使用“类型:ID”，保证同一聚合落在同一个 Kafka 分区。
     */
    public void appendConfigChange(ConfigAggregateType type, Long aggregateId) {
        String eventId = UUID.randomUUID().toString();
        append(configChangeTopic, type.name() + ":" + aggregateId, ConfigChangeMessage.class.getSimpleName(),
                new ConfigChangeMessage(eventId, type, aggregateId), eventId);
    }

    private void append(String topic, String key, String messageType, Object message, String outboxEventId) {
        OutboxMessageEntity entity = new OutboxMessageEntity();
        entity.setEventId(outboxEventId);
        entity.setTopic(topic);
        entity.setMessageKey(key);
        entity.setMessageType(messageType);
        try {
            entity.setPayload(objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize search outbox message", ex);
        }
        outboxMessageMapper.insert(entity);
    }
}
