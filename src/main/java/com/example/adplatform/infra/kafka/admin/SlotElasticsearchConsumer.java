package com.example.adplatform.infra.kafka.admin;

import com.example.adplatform.infra.debezium.admin.SlotElasticSearchUpdatePayload;
import com.example.adplatform.infra.kafka.admin.port.SlotElasticsearchPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SlotElasticsearchConsumer {
    private final SlotElasticsearchPort slotElasticsearchPort;
    private final ObjectMapper objectMapper;

    /** 消费配置变化并更新候选写别名。 */
    @KafkaListener(
            topics = "${app.kafka.topics.slot-es-sync}",
            groupId = "candidate-index-consumer",
            containerFactory = "searchKafkaListenerContainerFactory")
    public void consumeConfigChange(ConsumerRecord<String, String> record) throws JsonProcessingException {
        SlotElasticSearchUpdatePayload payload = objectMapper.readValue(record.value(),SlotElasticSearchUpdatePayload.class);
        slotElasticsearchPort.SlotElasticSearchUpdate(payload.oldSlotCode(),payload.newSlotCode());
    }
}
