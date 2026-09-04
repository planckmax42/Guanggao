package com.example.adplatform.admin.service.impl;

import com.example.adplatform.admin.converter.SlotConverter;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.admin.port.slot.SlotFilterPort;
import com.example.adplatform.admin.event.SlotCacheImmediateEvent;
import com.example.adplatform.admin.request.CreateSlotRequest;
import com.example.adplatform.admin.request.UpdateSlotRequest;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.search.outbox.message.ConfigAggregateType;
import com.example.adplatform.search.outbox.service.SearchOutboxService;
import com.example.adplatform.search.outbox.service.SlotCacheOutboxService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlotServiceImplTests {

    @Test
    void shouldAppendOutboxAndPublishAfterCommitCacheEvent() {
        Fixture fixture = fixture();
        CreateSlotRequest request = new CreateSlotRequest(
                "HOME_BANNER", "首页 Banner", 1080, 300, "APP_HOME");
        SlotEntity entity = slot(1L, "HOME_BANNER");
        when(fixture.converter().toEntity(request)).thenReturn(entity);

        fixture.service().create(request);

        verify(fixture.slotCacheOutbox()).append(entity.getPublicId(), null);
        verify(fixture.searchOutbox()).appendConfigChange(ConfigAggregateType.SLOT, entity.getPublicId());
        verify(fixture.eventPublisher()).publishEvent(new SlotCacheImmediateEvent(
                SlotCacheImmediateEvent.Action.WRITE, "HOME_BANNER", 1L));
        assertThat(entity.getPublicId()).matches("^slot_[0-9a-f]{32}$");
    }

    @Test
    void shouldEvictOldCodeAfterCommitAndPersistCompensationMessage() {
        Fixture fixture = fixture();
        UpdateSlotRequest request = new UpdateSlotRequest(
                "NEW_BANNER", "New Banner", 1080, 300, "APP_HOME");
        SlotEntity existing = slot(1L, "OLD_BANNER");
        existing.initializePublicId("slot_00000000000000000000000000000001");
        SlotEntity updated = slot(1L, "NEW_BANNER");
        updated.initializePublicId("slot_00000000000000000000000000000001");
        when(fixture.mapper().selectOne(any())).thenReturn(existing);
        when(fixture.mapper().selectById(1L)).thenReturn(updated);

        fixture.service().update(existing.getPublicId(), request);

        verify(fixture.slotCacheOutbox()).append(existing.getPublicId(), "OLD_BANNER");
        verify(fixture.eventPublisher()).publishEvent(new SlotCacheImmediateEvent(
                SlotCacheImmediateEvent.Action.EVICT, "OLD_BANNER", 1L));
    }

    @Test
    void shouldNotAppendCacheMessageWhenCodeDidNotChange() {
        Fixture fixture = fixture();
        UpdateSlotRequest request = new UpdateSlotRequest(
                "HOME_BANNER", "Renamed", 1080, 300, "APP_HOME");
        SlotEntity existing = slot(1L, "HOME_BANNER");
        existing.initializePublicId("slot_00000000000000000000000000000001");
        when(fixture.mapper().selectOne(any())).thenReturn(existing);
        when(fixture.mapper().selectById(1L)).thenReturn(existing);

        fixture.service().update(existing.getPublicId(), request);

        verify(fixture.slotCacheOutbox(), never()).append(any(), any());
    }

    private Fixture fixture() {
        SlotMapper mapper = mock(SlotMapper.class);
        SlotConverter converter = mock(SlotConverter.class);
        SlotFilterPort slotFilter = mock(SlotFilterPort.class);
        SearchOutboxService searchOutbox = mock(SearchOutboxService.class);
        SlotCacheOutboxService slotCacheOutbox = mock(SlotCacheOutboxService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        SlotServiceImpl service = new SlotServiceImpl(
                mapper,
                converter,
                slotFilter,
                searchOutbox,
                slotCacheOutbox,
                eventPublisher);
        return new Fixture(service, mapper, converter, searchOutbox, slotCacheOutbox, eventPublisher);
    }

    private SlotEntity slot(Long id, String code) {
        SlotEntity slot = new SlotEntity();
        slot.setId(id);
        slot.setSlotCode(code);
        slot.setStatus(CommonStatus.ENABLED);
        return slot;
    }

    private record Fixture(
            SlotServiceImpl service,
            SlotMapper mapper,
            SlotConverter converter,
            SearchOutboxService searchOutbox,
            SlotCacheOutboxService slotCacheOutbox,
            ApplicationEventPublisher eventPublisher) {
    }
}
