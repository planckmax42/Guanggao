package com.example.adplatform.infra.kafka.admin;

import com.example.adplatform.infra.kafka.port.CandidateIndexUpdatePort;
import com.example.adplatform.search.outbox.message.ConfigChangeMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 搜索读取模型的 Kafka 消费入口。
 *
 * <p>配置消费者按聚合 key 分区并从 MySQL 重建受影响候选，因此可安全重复消费。
 * 异常统一交给监听容器重试，超过次数后进入对应 DLT。</p>
 */
@Component
@RequiredArgsConstructor
public class SearchIndexKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final CandidateIndexUpdatePort candidateIndexUpdatePort;

    /** 消费配置变化并更新候选写别名。 */
    @KafkaListener(
            topics = "${app.kafka.topics.config-change}",
            groupId = "candidate-index-consumer",
            containerFactory = "searchKafkaListenerContainerFactory")
    public void consumeConfigChange(String payload) throws Exception {
        candidateIndexUpdatePort.update(objectMapper.readValue(payload, ConfigChangeMessage.class));//ES增量写入消费者
    }
}
