package com.example.adplatform.admin.service.impl;

import com.example.adplatform.admin.converter.PlanConverter;
import com.example.adplatform.admin.entity.*;
import com.example.adplatform.admin.mapper.*;
import com.example.adplatform.admin.port.EventMetadataPort;
import com.example.adplatform.admin.request.plan.*;
import com.example.adplatform.search.outbox.service.SearchOutboxService;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlanServiceImplTests {
    private final PlanMapper mapper = mock(PlanMapper.class);
    private final AdvertiserMapper advertisers = mock(AdvertiserMapper.class);
    private final EventMetadataPort metadata = mock(EventMetadataPort.class);
    private final SearchOutboxService outbox = mock(SearchOutboxService.class);
    private final PlanServiceImpl service = new PlanServiceImpl(mapper, advertisers, Mappers.getMapper(PlanConverter.class),
            outbox, mock(ApplicationEventPublisher.class), metadata);

    @Test
    void shouldCreateDraftWithAdvertiserPublicId() {
        AdvertiserEntity advertiser = new AdvertiserEntity();
        advertiser.setStatus(1);
        when(advertisers.selectOne(any())).thenReturn(advertiser);
        service.create(new CreatePlanRequest("adv_1", "计划", 100L, 10L, 1L, null,
                LocalDateTime.now(), LocalDateTime.now().plusDays(1)));
        ArgumentCaptor<PlanEntity> inserted = ArgumentCaptor.forClass(PlanEntity.class);
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getAdvertiserPublicId()).isEqualTo("adv_1");
        assertThat(inserted.getValue().getStatus()).isEqualTo("DRAFT");
        verify(advertisers, never()).selectById(any());
    }

    @Test
    void shouldUpdateBusinessFieldsWithoutStatusOrOwner() {
        PlanEntity entity = plan();
        when(mapper.selectOne(any())).thenReturn(entity);
        when(mapper.selectById(1L)).thenReturn(entity);
        service.update(new UpdatePlanRequest("plan_1", "新计划", 100L, 10L, 1L, "CPC",
                LocalDateTime.now(), LocalDateTime.now().plusDays(1)));
        ArgumentCaptor<PlanEntity> updated = ArgumentCaptor.forClass(PlanEntity.class);
        verify(mapper).updateById(updated.capture());
        assertThat(updated.getValue().getStatus()).isNull();
        assertThat(updated.getValue().getAdvertiserPublicId()).isNull();
        assertThat(entity.getStatus()).isEqualTo("ONLINE");
        verify(metadata).evictPlanAfterCommit(1L);
    }

    @Test
    void shouldDeleteAndRejectRepeatedDeletion() {
        when(mapper.selectOne(any())).thenReturn(plan(), null);
        when(mapper.deleteById(1L)).thenReturn(1);
        assertThat(service.delete(new DeletePlanRequest("plan_1")).planPublicId()).isEqualTo("plan_1");
        assertThatThrownBy(() -> service.delete(new DeletePlanRequest("plan_1"))).hasMessage("广告计划不存在");
    }

    private PlanEntity plan() {
        PlanEntity entity = new PlanEntity();
        entity.setId(1L);
        entity.initializePublicId("plan_1");
        entity.setAdvertiserPublicId("adv_1");
        entity.setStatus("ONLINE");
        return entity;
    }
}
