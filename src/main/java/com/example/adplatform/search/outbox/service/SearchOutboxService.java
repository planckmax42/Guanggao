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

@Service
@RequiredArgsConstructor
public class SearchOutboxService {

    private final OutboxMessageMapper outboxMessageMapper;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.config-change}")
    private String configChangeTopic;

    @Value("${app.kafka.topics.event-index}")
    private String eventIndexTopic;

    public void appendConfigChange(ConfigAggregateType type, Long aggregateId) {
        String eventId = UUID.randomUUID().toString();
        append(configChangeTopic, type.name() + ":" + aggregateId, ConfigChangeMessage.class.getSimpleName(),
                new ConfigChangeMessage(eventId, type, aggregateId), eventId);
    }

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
