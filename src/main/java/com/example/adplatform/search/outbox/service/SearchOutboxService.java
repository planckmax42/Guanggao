package com.example.adplatform.search.outbox.service;

import com.example.adplatform.search.outbox.entity.OutboxMessageEntity;
import com.example.adplatform.search.outbox.mapper.OutboxMessageMapper;
import com.example.adplatform.search.outbox.message.ConfigAggregateType;
import com.example.adplatform.search.outbox.message.ConfigChangeMessage;
import com.example.adplatform.search.outbox.message.EventIndexMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 在业务事务内追加搜索链路 Outbox 消息。
 *
 * <p>调用方必须是管理配置或事件落库的 {@code @Transactional} 方法，使业务数据和
 * outbox_message 同时提交或同时回滚。该服务只写 MySQL，不直接调用 Kafka，从而消除
 * “数据库成功但消息发送失败”的双写不一致窗口。</p>
 */
@Service
@RequiredArgsConstructor
public class SearchOutboxService {

    private final OutboxMessageMapper outboxMessageMapper;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.config-change}")
    private String configChangeTopic;

    @Value("${app.kafka.topics.event-index}")
    private String eventIndexTopic;

    /**
     * 追加配置变更消息。消息 key 使用“类型:ID”，保证同一聚合落在同一个 Kafka 分区。
     */
    public void appendConfigChange(ConfigAggregateType type, Long aggregateId) {
        String eventId = UUID.randomUUID().toString();
        append(configChangeTopic, type.name() + ":" + aggregateId, ConfigChangeMessage.class.getSimpleName(),
                new ConfigChangeMessage(eventId, type, aggregateId), eventId);
    }

    /** 追加事件索引消息；消费者会根据 eventId 回查 MySQL 最新记录。 */
    public void appendEventIndex(String eventId) {
        append(eventIndexTopic, eventId, EventIndexMessage.class.getSimpleName(),
                new EventIndexMessage(eventId), UUID.randomUUID().toString());
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
