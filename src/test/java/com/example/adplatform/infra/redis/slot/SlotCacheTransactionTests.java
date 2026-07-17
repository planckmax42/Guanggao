package com.example.adplatform.infra.redis.slot;

import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.infra.redis.RedisKeyConstants;
import com.example.adplatform.infra.redis.slot.bloom.SlotBloomFilterMetrics;
import com.example.adplatform.infra.redis.slot.bloom.SlotCodeBloomFilterManager;
import com.example.adplatform.infra.redis.slot.resilience.SlotMysqlCircuitBreaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlotCacheTransactionTests {

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void shouldRefreshBloomFilterAndRedisOnlyAfterCommit() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        SlotCodeBloomFilterManager bloomFilterManager = mock(SlotCodeBloomFilterManager.class);
        SlotCacheProperties properties = new SlotCacheProperties();
        properties.setRedisTtl(Duration.ofDays(1));
        SlotCacheServiceImpl service = new SlotCacheServiceImpl(
                redisTemplate,
                mock(SlotMapper.class),
                properties,
                bloomFilterManager,
                mock(SlotBloomFilterMetrics.class),
                mock(SlotMysqlCircuitBreaker.class));
        SlotEntity slot = enabledSlot();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        service.refreshSlot(slot, null);

        verify(bloomFilterManager, never()).put("HOME_BANNER");
        verify(valueOperations, never()).set(
                RedisKeyConstants.slotCodeToId("HOME_BANNER"), "1", Duration.ofDays(1));

        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);

        verify(bloomFilterManager).put("HOME_BANNER");
        verify(valueOperations).set(
                RedisKeyConstants.slotCodeToId("HOME_BANNER"), "1", Duration.ofDays(1));
    }

    @Test
    void shouldNotRefreshCacheWhenTransactionDoesNotCommit() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        SlotCodeBloomFilterManager bloomFilterManager = mock(SlotCodeBloomFilterManager.class);
        SlotCacheProperties properties = new SlotCacheProperties();
        properties.setRedisTtl(Duration.ofDays(1));
        SlotCacheServiceImpl service = new SlotCacheServiceImpl(
                redisTemplate,
                mock(SlotMapper.class),
                properties,
                bloomFilterManager,
                mock(SlotBloomFilterMetrics.class),
                mock(SlotMysqlCircuitBreaker.class));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        service.refreshSlot(enabledSlot(), null);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(bloomFilterManager, never()).put("HOME_BANNER");
        verify(redisTemplate, never()).opsForValue();
    }

    private SlotEntity enabledSlot() {
        SlotEntity slot = new SlotEntity();
        slot.setId(1L);
        slot.setSlotCode("HOME_BANNER");
        slot.setStatus(CommonStatus.ENABLED);
        return slot;
    }
}
