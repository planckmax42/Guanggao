package com.example.adplatform.admin.service.impl;

import com.example.adplatform.admin.converter.MaterialConverter;
import com.example.adplatform.admin.entity.PlanEntity;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.MaterialMapper;
import com.example.adplatform.admin.mapper.PlanMapper;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.admin.port.EventMetadataCacheMaintenancePort;
import com.example.adplatform.admin.request.CreateMaterialRequest;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.search.outbox.service.SearchOutboxService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MaterialServiceImplTests {

    @Test
    void shouldRejectMaterialWhenSlotIsDisabled() {
        MaterialMapper materialMapper = mock(MaterialMapper.class);
        PlanMapper planMapper = mock(PlanMapper.class);
        SlotMapper slotMapper = mock(SlotMapper.class);
        MaterialConverter materialConverter = mock(MaterialConverter.class);
        SearchOutboxService searchOutboxService = mock(SearchOutboxService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        EventMetadataCacheMaintenancePort cacheMaintenancePort =
                mock(EventMetadataCacheMaintenancePort.class);
        MaterialServiceImpl service = new MaterialServiceImpl(
                materialMapper,
                planMapper,
                slotMapper,
                materialConverter,
                searchOutboxService,
                eventPublisher,
                cacheMaintenancePort);

        PlanEntity plan = new PlanEntity();
        plan.setId(10L);
        SlotEntity slot = new SlotEntity();
        slot.setId(20L);
        slot.setStatus(CommonStatus.DISABLED);
        when(planMapper.selectOne(any())).thenReturn(plan);
        when(slotMapper.selectOne(any())).thenReturn(slot);
        CreateMaterialRequest request = new CreateMaterialRequest(
                "plan_00000000000000000000000000000001",
                "slot_00000000000000000000000000000001",
                "标题",
                "描述",
                "https://cdn.example.com/image.png",
                "https://example.com/landing");

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("广告位已停用，不能创建素材");
        verifyNoInteractions(materialMapper, materialConverter, searchOutboxService, cacheMaintenancePort);
    }
}
