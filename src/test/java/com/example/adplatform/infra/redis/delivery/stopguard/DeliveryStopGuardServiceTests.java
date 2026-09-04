package com.example.adplatform.infra.redis.delivery.stopguard;

import com.example.adplatform.infra.redis.delivery.slot.SlotCacheProperties;
import com.example.adplatform.infra.redis.delivery.slot.SlotCacheFailureLogLimiter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryStopGuardServiceTests {

    @Test
    void shouldFailClosedForSlotsButKeepExistingFailOpenForPlans() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.executePipelined(any(RedisCallback.class)))
                .thenThrow(new DataAccessResourceFailureException("redis down"));
        SlotCacheProperties properties = new SlotCacheProperties();
        properties.setFailurePolicy(SlotCacheProperties.FailurePolicy.FAIL_CLOSED);
        DeliveryStopGuardService service = new DeliveryStopGuardService(
                redisTemplate,
                new SimpleMeterRegistry(),
                properties,
                new SlotCacheFailureLogLimiter(properties, new SimpleMeterRegistry()));

        assertEquals(Set.of(1L, 2L), service.findStoppedSlots(List.of(1L, 2L)));
        assertEquals(Set.of(), service.findStoppedPlans(List.of(1L, 2L)));
    }
}
