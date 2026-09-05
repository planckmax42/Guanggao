package com.example.adplatform.infra.redis.delivery.slot;

import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.delivery.port.SlotIdResult;
import com.example.adplatform.infra.bloomfilter.delivery.slot.SlotBloomOperationsService;
import com.example.adplatform.infra.redis.delivery.DeliveryRedisKeys;
import com.example.adplatform.infra.resilience.delivery.slot.SlotMysqlCircuitBreaker;
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

        SlotCacheAccessException failure = assertThrows(
                SlotCacheAccessException.class,
                () -> service(redisTemplate, mock(SlotMapper.class), new SimpleMeterRegistry())
                        .writeSlotToRedis("HOME_BANNER", 1L));

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

    private SlotCacheAdminDeliveryServiceImpl service(
            StringRedisTemplate redisTemplate,
            SlotMapper slotMapper,
            SimpleMeterRegistry meterRegistry) {
        SlotCacheProperties properties = new SlotCacheProperties();
        properties.setRedisTtl(Duration.ofDays(1));
        properties.getLock().setStripes(32);
        properties.getLock().setReadWaitTimeout(Duration.ofMillis(50));
        SlotMysqlCircuitBreaker mysqlCircuitBreaker = mock(SlotMysqlCircuitBreaker.class);
        when(mysqlCircuitBreaker.execute(any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(0)).get());
        return new SlotCacheAdminDeliveryServiceImpl(
                redisTemplate,
                slotMapper,
                properties,
                mock(SlotBloomOperationsService.class),
                mysqlCircuitBreaker,
                new SlotRedisCircuitBreaker(new SlotRedisCircuitBreakerProperties(), meterRegistry),
                new SlotCacheLockManager(properties),
                meterRegistry,
                new SlotCacheFailureLogLimiter(properties, meterRegistry));
    }
}
