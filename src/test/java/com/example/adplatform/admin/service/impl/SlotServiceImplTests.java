package com.example.adplatform.admin.service.impl;

import com.example.adplatform.admin.converter.SlotConverter;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.admin.port.SlotCacheMaintenancePort;
import com.example.adplatform.admin.request.CreateSlotRequest;
import com.example.adplatform.admin.request.UpdateSlotRequest;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.search.outbox.message.ConfigAggregateType;
import com.example.adplatform.search.outbox.service.SearchOutboxService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlotServiceImplTests {

    @Test
    void shouldRegisterNewSlotForTransactionAwareCacheRefresh() {
        SlotMapper slotMapper = mock(SlotMapper.class);
        SlotConverter slotConverter = mock(SlotConverter.class);
        SlotCacheMaintenancePort slotCacheService = mock(SlotCacheMaintenancePort.class);
        SearchOutboxService searchOutboxService = mock(SearchOutboxService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        SlotServiceImpl service = new SlotServiceImpl(
                slotMapper,
                slotConverter,
                slotCacheService,
                searchOutboxService,
                eventPublisher);
        CreateSlotRequest request = new CreateSlotRequest(
                "HOME_BANNER", "首页 Banner", 1080, 300, "APP_HOME");
        SlotEntity entity = new SlotEntity();
        entity.setId(1L);
        entity.setSlotCode("HOME_BANNER");
        when(slotConverter.toEntity(request)).thenReturn(entity);

        service.create(request);

        InOrder order = inOrder(slotMapper, slotCacheService);
        order.verify(slotMapper).insert(entity);
        order.verify(slotCacheService).refreshSlot(entity, null);
        verify(searchOutboxService).appendConfigChange(ConfigAggregateType.SLOT, 1L);
    }

    @Test
    void shouldRequestCacheRefreshAfterUpdatingSlot() {
        SlotMapper slotMapper = mock(SlotMapper.class);
        SlotConverter slotConverter = mock(SlotConverter.class);
        SlotCacheMaintenancePort slotCacheService = mock(SlotCacheMaintenancePort.class);
        SearchOutboxService searchOutboxService = mock(SearchOutboxService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        SlotServiceImpl service = new SlotServiceImpl(
                slotMapper,
                slotConverter,
                slotCacheService,
                searchOutboxService,
                eventPublisher);
        UpdateSlotRequest request = new UpdateSlotRequest(
                "NEW_BANNER", "New Banner", 1080, 300, "APP_HOME", CommonStatus.ENABLED);
        SlotEntity existing = new SlotEntity();
        existing.setId(1L);
        existing.setSlotCode("OLD_BANNER");
        existing.setStatus(CommonStatus.ENABLED);
        SlotEntity updated = new SlotEntity();
        updated.setId(1L);
        updated.setSlotCode("NEW_BANNER");
        updated.setStatus(CommonStatus.ENABLED);
        when(slotMapper.selectById(1L)).thenReturn(existing, updated);

        service.update(1L, request);

        InOrder order = inOrder(slotMapper, slotCacheService);
        order.verify(slotMapper).updateById(existing);
        order.verify(slotCacheService).refreshSlot(updated, "OLD_BANNER");
    }
}
