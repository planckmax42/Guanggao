package com.example.adplatform.infra.redis.delivery.slot;

import com.example.adplatform.admin.event.SlotCacheImmediateEvent;
import com.example.adplatform.admin.port.slot.SlotCachePort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SlotCacheImmediateListenerTests {

    @Test
    void shouldAttemptOnceAndSwallowFailureAfterCommit() {
        SlotCachePort cachePort = mock(SlotCachePort.class);
        SlotCacheProperties properties = properties();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SlotCacheAccessException failure = new SlotCacheAccessException(
                "evict", "OLD_CODE", new IllegalStateException("redis down"));
        doThrow(failure).when(cachePort).evictSlotCodeFromRedis("OLD_CODE");
        SlotCacheImmediateListener listener = new SlotCacheImmediateListener(
                cachePort,
                new SlotCacheLockManager(properties),
                registry,
                new SlotCacheFailureLogLimiter(properties, registry));

        listener.afterCommit(new SlotCacheImmediateEvent(
                SlotCacheImmediateEvent.Action.EVICT, "OLD_CODE", 1L));

        verify(cachePort).evictSlotCodeFromRedis("OLD_CODE");
        assertEquals(1D, registry.counter(
                "ad.slot.cache.sync", "stage", "immediate", "result", "failure").count());
    }

    private SlotCacheProperties properties() {
        SlotCacheProperties properties = new SlotCacheProperties();
        properties.setRedisTtl(Duration.ofDays(1));
        properties.getLock().setStripes(32);
        properties.getLock().setReadWaitTimeout(Duration.ofMillis(50));
        return properties;
    }
}
