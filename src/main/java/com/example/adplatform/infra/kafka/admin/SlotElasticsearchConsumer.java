package com.example.adplatform.infra.kafka.admin;

import com.example.adplatform.infra.kafka.admin.port.SlotElasticsearchPort;
import com.example.adplatform.infra.kafka.port.CandidateIndexUpdatePort;
import com.example.adplatform.search.outbox.message.ConfigChangeMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SlotElasticsearchConsumer {
    private final SlotElasticsearchPort slotElasticsearchPort;

    /** 消费配置变化并更新候选写别名。 */
    @KafkaListener(
            topics = "${app.kafka.topics.slot-es-sync}",
            groupId = "candidate-index-consumer",
            containerFactory = "searchKafkaListenerContainerFactory")
    public void consumeConfigChange(String slotCode)  {
        slotElasticsearchPort.SlotElasticSearchUpdate(slotCode);
    }
}
