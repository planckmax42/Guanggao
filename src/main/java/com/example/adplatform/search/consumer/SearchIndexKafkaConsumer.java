package com.example.adplatform.search.consumer;

import com.example.adplatform.search.candidate.service.CandidateIndexSyncService;
import com.example.adplatform.search.event.service.EventIndexService;
import com.example.adplatform.search.outbox.message.ConfigChangeMessage;
import com.example.adplatform.search.outbox.message.EventIndexMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SearchIndexKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final CandidateIndexSyncService candidateIndexSyncService;
    private final EventIndexService eventIndexService;

    @KafkaListener(
            topics = "${app.kafka.topics.config-change}",
            groupId = "candidate-index-consumer",
            containerFactory = "searchKafkaListenerContainerFactory")
    public void consumeConfigChange(String payload) throws Exception {
        candidateIndexSyncService.synchronize(objectMapper.readValue(payload, ConfigChangeMessage.class));
    }

    @KafkaListener(
            topics = "${app.kafka.topics.event-index}",
            groupId = "event-index-consumer",
            containerFactory = "searchKafkaListenerContainerFactory")
    public void consumeEventIndex(String payload) throws Exception {
        EventIndexMessage message = objectMapper.readValue(payload, EventIndexMessage.class);
        eventIndexService.indexByEventId(message.eventId());
    }
}
