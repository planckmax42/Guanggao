package com.example.adplatform.search.consumer;

import com.example.adplatform.search.candidate.service.CandidateIndexSyncService;
import com.example.adplatform.search.event.service.EventIndexService;
import com.example.adplatform.search.outbox.message.ConfigChangeMessage;
import com.example.adplatform.search.outbox.message.EventIndexMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 搜索读取模型的 Kafka 消费入口。
 *
 * <p>配置消费者按聚合 key 分区并从 MySQL 重建受影响候选，因此可安全重复消费；事件
 * 消费者使用 eventId 作为 ES 文档 ID，重复写入会覆盖同一文档。异常统一交给监听容器
 * 重试，超过次数后进入对应 DLT。</p>
 */
@Component
@RequiredArgsConstructor
public class SearchIndexKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final CandidateIndexSyncService candidateIndexSyncService;
    private final EventIndexService eventIndexService;

    /** 消费配置变化并更新候选写别名。 */
    @KafkaListener(
            topics = "${app.kafka.topics.config-change}",
            groupId = "candidate-index-consumer",
            containerFactory = "searchKafkaListenerContainerFactory")
    public void consumeConfigChange(String payload) throws Exception {
        candidateIndexSyncService.synchronize(objectMapper.readValue(payload, ConfigChangeMessage.class));
    }

    /** 消费已落库事件通知并写入对应日期的事件索引。 */
    @KafkaListener(
            topics = "${app.kafka.topics.event-index}",
            groupId = "event-index-consumer",
            containerFactory = "searchKafkaListenerContainerFactory")
    public void consumeEventIndex(String payload) throws Exception {
        EventIndexMessage message = objectMapper.readValue(payload, EventIndexMessage.class);
        eventIndexService.indexByEventId(message.eventId());
    }
}
