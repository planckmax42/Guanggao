package com.example.adplatform.tracking.service.impl;

import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.infra.redis.event.EventMetadataCacheService;
import com.example.adplatform.tracking.service.EventMaterialMetadata;
import com.example.adplatform.tracking.service.EventStatisticsProcessor;
import com.example.adplatform.tracking.service.EventStatisticsStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 实时统计链路：通过 Redis Lua 原子完成 eventId 去重、频控和事件计数。 */
@RequiredArgsConstructor
@Service
public class EventStatisticsProcessorImpl implements EventStatisticsProcessor {

    private final EventMetadataCacheService metadataCacheService;
    private final EventStatisticsStore eventStatisticsStore;

    @Override
    public void record(EventMessage message) {
        message = message.withDefaultEventTime();
        EventMaterialMetadata metadata = metadataCacheService.get(message.materialId());
        eventStatisticsStore.recordEventOnce(message, metadata);
    }
}
