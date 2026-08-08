package com.example.adplatform.infra.redis.delivery.slot;

import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.infra.redis.delivery.DeliveryRedisKeys;
import com.example.adplatform.infra.bloom.delivery.slot.SlotBloomService;
import com.example.adplatform.infra.resilience.delivery.slot.SlotMysqlCircuitBreaker;
import com.example.adplatform.infra.warmup.SlotWarmUpTask;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SlotCacheConcurrencyTests {

    @Test
    void shouldLetCommittedRefreshWinOverOlderMysqlBackfill() throws Exception {
        Map<String, String> redis = new ConcurrentHashMap<>();
        StringRedisTemplate redisTemplate = redisTemplate(redis);
        SlotMapper slotMapper = mock(SlotMapper.class);
        CountDownLatch mysqlReadStarted = new CountDownLatch(1);
        CountDownLatch allowMysqlReturn = new CountDownLatch(1);
        when(slotMapper.selectOne(any())).thenAnswer(invocation -> {
            mysqlReadStarted.countDown();
            assertEquals(true, allowMysqlReturn.await(1, TimeUnit.SECONDS));
            return slot(1L, "OLD_CODE", CommonStatus.ENABLED);
        });
        SlotCacheServiceImpl service = service(redisTemplate, slotMapper);

        CompletableFuture<Optional<Long>> olderRead = CompletableFuture.supplyAsync(
                () -> service.getEnabledSlotIdByCode("OLD_CODE"));
        assertEquals(true, mysqlReadStarted.await(1, TimeUnit.SECONDS));
        CompletableFuture<Void> committedRefresh = CompletableFuture.runAsync(
                () -> service.refreshSlot(slot(1L, "NEW_CODE", CommonStatus.ENABLED), "OLD_CODE"));

        allowMysqlReturn.countDown();
        assertEquals(Optional.of(1L), olderRead.get(1, TimeUnit.SECONDS));
        committedRefresh.get(1, TimeUnit.SECONDS);

        assertFalse(redis.containsKey(DeliveryRedisKeys.slotCodeToId("OLD_CODE")));
        assertEquals("1", redis.get(DeliveryRedisKeys.slotCodeToId("NEW_CODE")));
    }

    @Test
    void shouldMergeConcurrentMysqlFallbackForSameSlotCode() throws Exception {
        Map<String, String> redis = new ConcurrentHashMap<>();
        CountDownLatch distinctReadersMissedRedis = new CountDownLatch(2);
        SlotMapper slotMapper = mock(SlotMapper.class);
        CountDownLatch mysqlReadStarted = new CountDownLatch(1);
        CountDownLatch allowMysqlReturn = new CountDownLatch(1);
        when(slotMapper.selectOne(any())).thenAnswer(invocation -> {
            mysqlReadStarted.countDown();
            assertEquals(true, allowMysqlReturn.await(1, TimeUnit.SECONDS));
            return slot(1L, "HOME_BANNER", CommonStatus.ENABLED);
        });
        SlotCacheServiceImpl service = service(
                redisTemplate(redis, distinctReadersMissedRedis),
                slotMapper);

        CompletableFuture<Optional<Long>> first = CompletableFuture.supplyAsync(
                () -> service.getEnabledSlotIdByCode("HOME_BANNER"));
        assertEquals(true, mysqlReadStarted.await(1, TimeUnit.SECONDS));
        CompletableFuture<Optional<Long>> second = CompletableFuture.supplyAsync(
                () -> service.getEnabledSlotIdByCode("HOME_BANNER"));
        assertEquals(true, distinctReadersMissedRedis.await(1, TimeUnit.SECONDS));
        allowMysqlReturn.countDown();

        assertEquals(Optional.of(1L), first.get(1, TimeUnit.SECONDS));
        assertEquals(Optional.of(1L), second.get(1, TimeUnit.SECONDS));
        verify(slotMapper, times(1)).selectOne(any());
    }

    @Test
    void shouldReturnDependencyUnavailableWhenReadLockTimesOut() throws Exception {
        SlotMapper slotMapper = mock(SlotMapper.class);
        Fixture fixture = fixture(
                redisTemplate(new ConcurrentHashMap<>()),
                slotMapper,
                Duration.ofMillis(30));

        try (SlotCacheLockManager.LockHandle ignored =
                     fixture.lockManager().acquireForWrite("BUSY_CODE")) {
            CompletableFuture<Throwable> attempt = CompletableFuture.supplyAsync(() -> {
                try {
                    fixture.service().getEnabledSlotIdByCode("BUSY_CODE");
                    return null;
                } catch (Throwable ex) {
                    return ex;
                }
            });

            BusinessException failure = assertInstanceOf(
                    BusinessException.class,
                    attempt.get(1, TimeUnit.SECONDS));
            assertEquals(ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE, failure.getErrorCode());
            verifyNoInteractions(slotMapper);
        }
    }

    @Test
    void shouldRevalidateWarmupSnapshotBeforeWritingRedis() {
        Map<String, String> redis = new ConcurrentHashMap<>();
        redis.put(DeliveryRedisKeys.slotCodeToId("OLD_CODE"), "1");
        SlotBloomService bloomFilterService = mock(SlotBloomService.class);
        SlotMapper slotMapper = mock(SlotMapper.class);
        when(slotMapper.selectOne(any())).thenReturn(null);
        SlotCacheServiceImpl service = service(redisTemplate(redis), slotMapper, bloomFilterService);
        when(bloomFilterService.regularRebuild()).thenReturn(Optional.of(List.of("OLD_CODE")));
        SlotWarmUpTask warmUpTask = new SlotWarmUpTask(bloomFilterService, service);

        warmUpTask.warmUp();

        assertFalse(redis.containsKey(DeliveryRedisKeys.slotCodeToId("OLD_CODE")));
    }

    private SlotCacheServiceImpl service(StringRedisTemplate redisTemplate, SlotMapper slotMapper) {
        return fixture(redisTemplate, slotMapper, Duration.ofMillis(100)).service();
    }

    private SlotCacheServiceImpl service(
            StringRedisTemplate redisTemplate,
            SlotMapper slotMapper,
            SlotBloomService bloomFilterService) {
        SlotCacheProperties properties = properties(Duration.ofMillis(100));
        return new SlotCacheServiceImpl(
                redisTemplate,
                slotMapper,
                properties,
                bloomFilterService,
                mock(SlotMysqlCircuitBreaker.class),
                new SlotCacheLockManager(properties));
    }

    private Fixture fixture(
            StringRedisTemplate redisTemplate,
            SlotMapper slotMapper,
            Duration readWaitTimeout) {
        SlotCacheProperties properties = properties(readWaitTimeout);
        SlotBloomService bloomFilterService = mock(SlotBloomService.class);
        when(bloomFilterService.definiteNotContain(anyString())).thenReturn(false);
        SlotMysqlCircuitBreaker circuitBreaker = mock(SlotMysqlCircuitBreaker.class);
        when(circuitBreaker.execute(any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(0)).get());
        SlotCacheLockManager lockManager = new SlotCacheLockManager(properties);
        SlotCacheServiceImpl service = new SlotCacheServiceImpl(
                redisTemplate,
                slotMapper,
                properties,
                bloomFilterService,
                circuitBreaker,
                lockManager);
        return new Fixture(service, lockManager);
    }

    private SlotCacheProperties properties(Duration readWaitTimeout) {
        SlotCacheProperties properties = new SlotCacheProperties();
        properties.setRedisTtl(Duration.ofDays(1));
        properties.getLock().setStripes(1_024);
        properties.getLock().setReadWaitTimeout(readWaitTimeout);
        return properties;
    }

    @SuppressWarnings("unchecked")
    private StringRedisTemplate redisTemplate(Map<String, String> redis) {
        return redisTemplate(redis, null);
    }

    @SuppressWarnings("unchecked")
    private StringRedisTemplate redisTemplate(
            Map<String, String> redis,
            CountDownLatch distinctReadersMissedRedis) {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        Set<Long> missedReaderThreads = ConcurrentHashMap.newKeySet();
        when(template.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(invocation -> {
            String value = redis.get(invocation.getArgument(0));
            if (value == null
                    && distinctReadersMissedRedis != null
                    && missedReaderThreads.add(Thread.currentThread().getId())) {
                distinctReadersMissedRedis.countDown();
            }
            return value;
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            redis.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(template.delete(anyString())).thenAnswer(invocation ->
                redis.remove(invocation.getArgument(0)) != null);
        return template;
    }

    private SlotEntity slot(Long id, String code, int status) {
        SlotEntity slot = new SlotEntity();
        slot.setId(id);
        slot.setSlotCode(code);
        slot.setStatus(status);
        return slot;
    }

    private record Fixture(
            SlotCacheServiceImpl service,
            SlotCacheLockManager lockManager) {
    }
}
