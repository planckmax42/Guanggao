package com.example.adplatform.tracking.service.impl;

import com.example.adplatform.tracking.port.EventMetadataReaderPort;
import com.example.adplatform.tracking.converter.EventConverter;
import com.example.adplatform.tracking.entity.EventEntity;
import com.example.adplatform.tracking.entity.EventType;
import com.example.adplatform.tracking.mapper.EventMapper;
import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.tracking.service.EventMaterialMetadata;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventArchiveProcessorTests {

    @Test
    void shouldOnlyArchiveEvent() {
        EventMetadataReaderPort metadataCacheService = mock(EventMetadataReaderPort.class);
        EventConverter converter = mock(EventConverter.class);
        EventMapper eventMapper = mock(EventMapper.class);
        EventMessage message = new EventMessage(
                "event-1", "request-1", EventType.CLICK,
                "mat_00000000000000000000000000000010", 20L, LocalDateTime.now());
        EventMaterialMetadata metadata = new EventMaterialMetadata(
                10L, 30L, 40L, 100_000L, 10_000L, 25L, "CPC");
        when(metadataCacheService.get(message.materialPublicId())).thenReturn(metadata);
        when(converter.toEntity(any(), any()))
                .thenReturn(new EventEntity());

        new EventArchiveProcessorImpl(metadataCacheService, converter, eventMapper).archive(message);

        verify(converter).toEntity(message, metadata);
        verify(eventMapper).insert(any(EventEntity.class));
    }
}
