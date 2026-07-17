package com.example.adplatform.admin.service.impl;

import com.example.adplatform.admin.converter.SlotConverter;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.admin.request.CreateSlotRequest;
import com.example.adplatform.infra.redis.slot.SlotCacheService;
import com.example.adplatform.search.outbox.message.ConfigAggregateType;
import com.example.adplatform.search.outbox.service.SearchOutboxService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlotServiceImplTests {

    @Test
    void shouldRegisterNewSlotForTransactionAwareCacheRefresh() {
        SlotMapper slotMapper = mock(SlotMapper.class);
        SlotConverter slotConverter = mock(SlotConverter.class);
        SlotCacheService slotCacheService = mock(SlotCacheService.class);
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

        verify(slotMapper).insert(entity);
        verify(slotCacheService).refreshSlot(entity, null);
        verify(slotCacheService, never()).cacheSlot(entity);
        verify(searchOutboxService).appendConfigChange(ConfigAggregateType.SLOT, 1L);
    }
}
