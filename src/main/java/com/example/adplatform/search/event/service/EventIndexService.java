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

@Service
@RequiredArgsConstructor
public class EventIndexService {

    private final EventMapper eventMapper;
    private final EventIndexManager indexManager;
    private final ElasticsearchOperations operations;

    public void indexByEventId(String eventId) {
        EventEntity event = eventMapper.selectOne(new LambdaQueryWrapper<EventEntity>()
                .eq(EventEntity::getEventId, eventId));
        if (event == null) {
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
