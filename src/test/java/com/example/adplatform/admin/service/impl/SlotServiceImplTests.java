package com.example.adplatform.admin.service.impl;

import com.example.adplatform.admin.converter.SlotConverter;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.admin.port.slot.*;
import com.example.adplatform.admin.request.slot.*;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SlotServiceImplTests {
    private final SlotMapper mapper = mock(SlotMapper.class);
    private final SlotFilterPort filter = mock(SlotFilterPort.class);
    private final SlotCacheDebeziumPort cache = mock(SlotCacheDebeziumPort.class);
    private final SlotElasticsearchDebeziumPort elasticsearch = mock(SlotElasticsearchDebeziumPort.class);
    private final SlotServiceImpl service = new SlotServiceImpl(mapper, Mappers.getMapper(SlotConverter.class), filter,
            mock(ApplicationEventPublisher.class), cache, elasticsearch, mock(SlotCacheStopGuardPort.class));

    @Test
    void shouldRenameEnabledSlotAndNotWriteStatus() {
        SlotEntity entity = slot();
        when(mapper.selectOne(any())).thenReturn(entity);
        service.update(new UpdateSlotRequest("slot_1", "NEW", null, null, null, null));
        ArgumentCaptor<SlotEntity> update = ArgumentCaptor.forClass(SlotEntity.class);
        verify(mapper).updateById(update.capture());
        assertThat(update.getValue().getStatus()).isNull();
        assertThat(entity.getSlotCode()).isEqualTo("OLD");
        verify(filter).addSlotFilter("NEW");
        verify(cache).updateCacheWithRetry(1L, "OLD", "NEW");
        verify(elasticsearch).syncElasticsearchWithRetry("OLD", "NEW");
    }

    @Test
    void shouldPreserveCodeForPartialUpdate() {
        when(mapper.selectOne(any())).thenReturn(slot());
        service.update(new UpdateSlotRequest("slot_1", null, "New name", null, null, null));
        verifyNoInteractions(cache, filter, elasticsearch);
    }

    @Test
    void shouldDeleteUsingStoredCodeAndReportMissingOnRepeat() {
        when(mapper.selectOne(any())).thenReturn(slot(), null);
        when(mapper.deleteById(1L)).thenReturn(1);
        assertThat(service.delete(new DeleteSlotRequest("slot_1")).publicId()).isEqualTo("slot_1");
        verify(cache).evictCacheWithRetry("OLD");
        assertThatThrownBy(() -> service.delete(new DeleteSlotRequest("slot_1"))).hasMessage("广告位不存在");
    }

    private SlotEntity slot() {
        SlotEntity entity = new SlotEntity();
        entity.setId(1L);
        entity.initializePublicId("slot_1");
        entity.setSlotCode("OLD");
        entity.setStatus(1);
        return entity;
    }
}
