package com.example.adplatform.infra.redis.delivery.slot;

import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.delivery.port.SlotIdResult;
import com.example.adplatform.infra.bloomfilter.delivery.slot.SlotBloomOperationsService;
import com.example.adplatform.infra.redis.delivery.DeliveryRedisKeys;
import com.example.adplatform.infra.resilience.delivery.slot.MysqlCircuitBreaker;
import com.example.adplatform.infra.resilience.delivery.slot.SlotRedisCircuitBreaker;
import com.example.adplatform.infra.resilience.delivery.slot.SlotRedisCircuitBreakerProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlotCacheTransactionTests {

    @Test
    void shouldFailClosedWithoutFallingBackToMysqlWhenRedisReadFails() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(any())).thenThrow(new DataAccessResourceFailureException("redis down"));
        SlotMapper slotMapper = mock(SlotMapper.class);

        SlotIdResult result = service(redisTemplate, slotMapper, new SimpleMeterRegistry())
                .getEnabledIdByCode("HOME_BANNER");

        assertEquals(SlotIdResult.cacheUnavailable(), result);
        verify(slotMapper, never()).selectOne(any());
    }

    @Test
    void shouldExposeWriteFailureForOutboxRetry() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("redis down"))
                .when(values).set(any(), any(), any(Duration.class));

        SlotRedisAccessException failure = assertThrows(
                SlotRedisAccessException.class,
                () -> service(redisTemplate, mock(SlotMapper.class), new SimpleMeterRegistry())
                        .writeSlotToRedis(1L, "HOME_BANNER"));

        assertEquals("write", failure.getOperation());
        assertEquals("HOME_BANNER", failure.getSlotCode());
    }

    @Test
    void shouldEvictPreviousAndCurrentCodesWhenLatestSlotIsDisabled() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        SlotMapper slotMapper = mock(SlotMapper.class);
        SlotEntity disabled = new SlotEntity();
        disabled.setId(1L);
        disabled.setSlotCode("NEW_CODE");
        disabled.setStatus(CommonStatus.DISABLED);
        when(slotMapper.selectOne(any())).thenReturn(disabled);

        service(redisTemplate, slotMapper, new SimpleMeterRegistry())
                .reconcileSlot("slot_1", "OLD_CODE");

        verify(redisTemplate).delete(DeliveryRedisKeys.slotCodeToId("OLD_CODE"));
        verify(redisTemplate).delete(DeliveryRedisKeys.slotCodeToId("NEW_CODE"));
    }

    private SlotRedisServiceImpl service(
            StringRedisTemplate redisTemplate,
            SlotMapper slotMapper,
            SimpleMeterRegistry meterRegistry) {
        SlotRedisProperties properties = new SlotRedisProperties();
        properties.setRedisTtl(Duration.ofDays(1));
        properties.setStripes(32);
        properties.setReadWaitTimeout(Duration.ofMillis(50));
        MysqlCircuitBreaker mysqlCircuitBreaker = mock(MysqlCircuitBreaker.class);
        when(mysqlCircuitBreaker.executeSupplier(any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(0)).get());
        return new SlotRedisServiceImpl(
                redisTemplate,
                slotMapper,
                properties,
                mock(SlotBloomOperationsService.class),
                mysqlCircuitBreaker,
                new SlotRedisCircuitBreaker(new SlotRedisCircuitBreakerProperties(), meterRegistry),
                new SlotRedisLockManager(properties),
                meterRegistry,
                new SlotRedisFailureLogLimiter(properties, meterRegistry));
    }
}
