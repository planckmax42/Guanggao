package com.example.adplatform.search.event.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.adplatform.search.event.model.AdEventDocument;
import com.example.adplatform.tracking.entity.EventEntity;
import com.example.adplatform.tracking.mapper.EventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 根据事件 ID 从 MySQL 构建 ES 事件读取模型。
 *
 * <p>Kafka 消息只传 eventId，消费时回查已经提交的 event 表，避免消息携带的旧快照与
 * 计费结果不一致。eventId 作为 ES 文档 ID，使重复消息表现为覆盖写而不是新增重复行。</p>
 */
@Service
@RequiredArgsConstructor
public class EventIndexService {

    private final EventMapper eventMapper;
    private final EventIndexManager indexManager;
    private final ElasticsearchOperations operations;

    /** 将一条已落库事件写入其业务日期对应的日索引。 */
    public void indexByEventId(String eventId) {
        EventEntity event = eventMapper.selectOne(new LambdaQueryWrapper<EventEntity>()
                .eq(EventEntity::getEventId, eventId));
        if (event == null) {
            // 事件可能已被运维清理；幂等地忽略，不创建不完整 ES 文档。
            return;
        }
        AdEventDocument document = new AdEventDocument();
        document.setEventId(event.getEventId());
        document.setRequestId(event.getRequestId());
        document.setEventType(event.getEventType());
        document.setPlanId(event.getPlanId());
        document.setMaterialId(event.getMaterialId());
        document.setSlotId(event.getSlotId());
        document.setViewerId(event.getViewerId());
        document.setBillingType(event.getBillingType());
        document.setCharged(event.getCharged() != null && event.getCharged() == 1);
        document.setCostAmount(event.getCostAmount());
        document.setEventTime(toInstant(event.getEventTime()));
        document.setCreatedAt(toInstant(event.getCreatedAt()));
        operations.save(document, IndexCoordinates.of(indexManager.indexName(event.getEventTime().toLocalDate())));
    }

    private java.time.Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
