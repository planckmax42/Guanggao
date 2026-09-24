package com.example.adplatform.infra.redis.delivery.slot;

import com.example.adplatform.admin.converter.SlotConverter;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.admin.port.slot.SlotCacheAdminPort;
import com.example.adplatform.admin.port.slot.SlotFilterPort;
import com.example.adplatform.admin.service.impl.SlotServiceImpl;
import com.example.adplatform.search.outbox.service.SearchOutboxService;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SlotCacheCreateListenerTests {

    @Test
    void shouldWriteSlotToRedisAfterCreateCommit() {
        SlotCacheAdminPort cachePort = mock(SlotCacheAdminPort.class);
        SlotServiceImpl service = new SlotServiceImpl(
                mock(SlotMapper.class),
                mock(SlotConverter.class),
                mock(SlotFilterPort.class),
                mock(SearchOutboxService.class),
                mock(SlotCacheOutboxService.class),
                mock(ApplicationEventPublisher.class),
                cachePort,
                new SlotCacheLockManager(properties()));

        service.afterCommit(new SlotServiceImpl.SlotCacheCreateEvent("HOME_BANNER", 1L));

        verify(cachePort).writeSlotToRedis(1L, "HOME_BANNER");
    }

    private SlotCacheProperties properties() {
        SlotCacheProperties properties = new SlotCacheProperties();
        properties.setRedisTtl(Duration.ofDays(1));
        properties.setStripes(32);
        properties.setReadWaitTimeout(Duration.ofMillis(50));
        return properties;
    }
}
