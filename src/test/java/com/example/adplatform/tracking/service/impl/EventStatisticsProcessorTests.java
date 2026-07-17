package com.example.adplatform.tracking.service.impl;

import com.example.adplatform.infra.redis.event.EventMetadataCacheService;
import com.example.adplatform.tracking.entity.EventType;
import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.tracking.service.EventMaterialMetadata;
import com.example.adplatform.tracking.service.EventStatisticsStore;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventStatisticsProcessorTests {

    @Test
    void shouldDelegateToIdempotentStatisticsStore() {
        EventMetadataCacheService metadataCacheService = mock(EventMetadataCacheService.class);
        EventStatisticsStore store = mock(EventStatisticsStore.class);
        EventMessage message = new EventMessage(
                "event-1", "request-1", EventType.IMPRESSION, 10L, 20L, LocalDateTime.now());
        EventMaterialMetadata metadata = new EventMaterialMetadata(
                30L, 40L, 100_000L, 10_000L, 25L, "CPM");
        when(metadataCacheService.get(message.materialId())).thenReturn(metadata);

        new EventStatisticsProcessorImpl(metadataCacheService, store).record(message);

        verify(store).recordEventOnce(message, metadata);
    }
}
