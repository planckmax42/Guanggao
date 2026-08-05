package com.example.adplatform.infra.redis.tracking.materialMetadata;

import com.example.adplatform.infra.bloom.tracking.materialMetadata.MaterialIdMetadataBloomFilterServiceImpl;

import com.example.adplatform.admin.mapper.MaterialMapper;
import com.example.adplatform.admin.query.MaterialPlanJoinRow;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.infra.redis.tracking.TrackingRedisKeys;
import com.example.adplatform.tracking.service.EventMaterialMetadata;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EventMetadataCacheConcurrencyTests {

    @Test
    void shouldMergeThreeConsumerGroupFallbacksIntoOneMysqlJoin() throws Exception {
        Map<String, String> redis = new ConcurrentHashMap<>();
        CountDownLatch threeReadersMissedRedis = new CountDownLatch(3);
        CountDownLatch mysqlReadStarted = new CountDownLatch(1);
        CountDownLatch allowMysqlReturn = new CountDownLatch(1);
        MaterialMapper materialMapper = mock(MaterialMapper.class);
        when(materialMapper.selectMaterialPlanById(10L)).thenAnswer(invocation -> {
            mysqlReadStarted.countDown();
            assertEquals(true, allowMysqlReturn.await(1, TimeUnit.SECONDS));
            return row();
        });
        Fixture fixture = fixture(
                redisTemplate(redis, threeReadersMissedRedis),
                materialMapper,
                Duration.ofMillis(30),
                Duration.ofSeconds(1));

        CompletableFuture<EventMaterialMetadata> archive = CompletableFuture.supplyAsync(
                () -> fixture.service().get(10L));
        assertEquals(true, mysqlReadStarted.await(1, TimeUnit.SECONDS));
        CompletableFuture<EventMaterialMetadata> billing = CompletableFuture.supplyAsync(
                () -> fixture.service().get(10L));
        CompletableFuture<EventMaterialMetadata> statistics = CompletableFuture.supplyAsync(
                () -> fixture.service().get(10L));
        assertEquals(true, threeReadersMissedRedis.await(1, TimeUnit.SECONDS));
        Thread.sleep(100L);
        assertFalse(billing.isDone());
        assertFalse(statistics.isDone());
        allowMysqlReturn.countDown();

        assertEquals(metadata(), archive.get(1, TimeUnit.SECONDS));
        assertEquals(metadata(), billing.get(1, TimeUnit.SECONDS));
        assertEquals(metadata(), statistics.get(1, TimeUnit.SECONDS));
        verify(materialMapper, times(1)).selectMaterialPlanById(10L);
    }

    @Test
    void shouldLetPlanInvalidationWinOverOlderMysqlBackfill() throws Exception {
        Map<String, String> redis = new ConcurrentHashMap<>();
        CountDownLatch mysqlReadStarted = new CountDownLatch(1);
        CountDownLatch allowMysqlReturn = new CountDownLatch(1);
        CountDownLatch invalidationStarted = new CountDownLatch(1);
        MaterialMapper materialMapper = mock(MaterialMapper.class);
        when(materialMapper.selectMaterialPlanById(10L)).thenAnswer(invocation -> {
            mysqlReadStarted.countDown();
            assertEquals(true, allowMysqlReturn.await(1, TimeUnit.SECONDS));
            return row();
        });
        when(materialMapper.selectMaterialIdsByPlanId(20L)).thenAnswer(invocation -> {
            invalidationStarted.countDown();
            return List.of(10L);
        });
        Fixture fixture = fixture(
                redisTemplate(redis, null),
                materialMapper,
                Duration.ofSeconds(1));

        CompletableFuture<EventMaterialMetadata> olderRead = CompletableFuture.supplyAsync(
                () -> fixture.service().get(10L));
        assertEquals(true, mysqlReadStarted.await(1, TimeUnit.SECONDS));
        CompletableFuture<Void> committedInvalidation = CompletableFuture.runAsync(
                () -> fixture.service().evictPlanAfterCommit(20L));
        assertEquals(true, invalidationStarted.await(1, TimeUnit.SECONDS));

        allowMysqlReturn.countDown();
        assertEquals(metadata(), olderRead.get(1, TimeUnit.SECONDS));
        committedInvalidation.get(1, TimeUnit.SECONDS);

        assertFalse(redis.containsKey(TrackingRedisKeys.eventMaterialMetadata(10L)));
    }

    @Test
    void shouldTimeoutFollowerWithoutCancellingSharedMysqlLoad() throws Exception {
        Map<String, String> redis = new ConcurrentHashMap<>();
        CountDownLatch mysqlReadStarted = new CountDownLatch(1);
        CountDownLatch allowMysqlReturn = new CountDownLatch(1);
        MaterialMapper materialMapper = mock(MaterialMapper.class);
        when(materialMapper.selectMaterialPlanById(10L)).thenAnswer(invocation -> {
            mysqlReadStarted.countDown();
            assertEquals(true, allowMysqlReturn.await(1, TimeUnit.SECONDS));
            return row();
        });
        Fixture fixture = fixture(
                redisTemplate(redis, null),
                materialMapper,
                Duration.ofSeconds(1),
                Duration.ofMillis(30));

        CompletableFuture<EventMaterialMetadata> leader = CompletableFuture.supplyAsync(
                () -> fixture.service().get(10L));
        assertEquals(true, mysqlReadStarted.await(1, TimeUnit.SECONDS));
        CompletableFuture<Throwable> follower = CompletableFuture.supplyAsync(() -> {
            try {
                fixture.service().get(10L);
                return null;
            } catch (Throwable ex) {
                return ex;
            }
        });

        BusinessException failure = assertInstanceOf(
                BusinessException.class,
                follower.get(1, TimeUnit.SECONDS));
        assertEquals(ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE, failure.getErrorCode());

        allowMysqlReturn.countDown();
        assertEquals(metadata(), leader.get(1, TimeUnit.SECONDS));
        verify(materialMapper, times(1)).selectMaterialPlanById(10L);
    }

    @Test
    void shouldReturnDependencyUnavailableWhenReadLockTimesOut() throws Exception {
        MaterialMapper materialMapper = mock(MaterialMapper.class);
        Fixture fixture = fixture(
                redisTemplate(new ConcurrentHashMap<>(), null),
                materialMapper,
                Duration.ofMillis(30));

        try (MaterialMetadataRedisLock.LockHandle ignored =
                     fixture.lockManager().acquireForWrite(List.of(10L))) {
            CompletableFuture<Throwable> attempt = CompletableFuture.supplyAsync(() -> {
                try {
                    fixture.service().get(10L);
                    return null;
                } catch (Throwable ex) {
                    return ex;
                }
            });

            BusinessException failure = assertInstanceOf(
                    BusinessException.class,
                    attempt.get(1, TimeUnit.SECONDS));
            assertEquals(ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE, failure.getErrorCode());
            verifyNoInteractions(materialMapper);
        }
    }

    @Test
    void shouldRestoreInterruptAndReturnDependencyUnavailableWhenLockWaitIsInterrupted() throws Exception {
        MaterialMapper materialMapper = mock(MaterialMapper.class);
        Fixture fixture = fixture(
                redisTemplate(new ConcurrentHashMap<>(), null),
                materialMapper,
                Duration.ofSeconds(1));

        try (MaterialMetadataRedisLock.LockHandle ignored =
                     fixture.lockManager().acquireForWrite(List.of(10L))) {
            CompletableFuture<InterruptedResult> attempt = new CompletableFuture<>();
            Thread thread = new Thread(() -> {
                Thread.currentThread().interrupt();
                try {
                    fixture.service().get(10L);
                    attempt.complete(new InterruptedResult(null, Thread.currentThread().isInterrupted()));
                } catch (Throwable ex) {
                    attempt.complete(new InterruptedResult(ex, Thread.currentThread().isInterrupted()));
                }
            });
            thread.start();

            InterruptedResult result = attempt.get(1, TimeUnit.SECONDS);
            BusinessException failure = assertInstanceOf(BusinessException.class, result.failure());
            assertEquals(ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE, failure.getErrorCode());
            assertEquals(true, result.interrupted());
            verifyNoInteractions(materialMapper);
        }
    }

    private Fixture fixture(
            StringRedisTemplate redisTemplate,
            MaterialMapper materialMapper,
            Duration readWaitTimeout) {
        return fixture(redisTemplate, materialMapper, readWaitTimeout, Duration.ofSeconds(1));
    }

    private Fixture fixture(
            StringRedisTemplate redisTemplate,
            MaterialMapper materialMapper,
            Duration readWaitTimeout,
            Duration singleFlightWaitTimeout) {
        MaterialMetadataRedisProperties properties = properties(readWaitTimeout);
        properties.setSingleFlightWaitTimeout(singleFlightWaitTimeout);
        MaterialIdMetadataBloomFilterServiceImpl bloomFilterManager = mock(MaterialIdMetadataBloomFilterServiceImpl.class);
        when(bloomFilterManager.definitelyNotContains(any())).thenReturn(false);
        MaterialMetadataRedisLock lockManager = new MaterialMetadataRedisLock(properties);
        MaterialMetadataRedisServiceImpl service = new MaterialMetadataRedisServiceImpl(
                redisTemplate,
                new ObjectMapper(),
                materialMapper,
                bloomFilterManager,
                properties,
                lockManager);
        return new Fixture(service, lockManager);
    }

    private MaterialMetadataRedisProperties properties(Duration readWaitTimeout) {
        MaterialMetadataRedisProperties properties = new MaterialMetadataRedisProperties();
        properties.setRedisTtl(Duration.ofHours(1));
        properties.setRedisTtlJitter(Duration.ZERO);
        properties.setSingleFlightWaitTimeout(Duration.ofSeconds(1));
        properties.getLock().setStripes(1_024);
        properties.getLock().setReadWaitTimeout(readWaitTimeout);
        return properties;
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
        doAnswer(invocation -> {
            redis.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(template.delete(anyCollection())).thenAnswer(invocation -> {
            Collection<String> keys = invocation.getArgument(0);
            return keys.stream().map(redis::remove).filter(value -> value != null).count();
        });
        return template;
    }

    private MaterialPlanJoinRow row() {
        MaterialPlanJoinRow row = new MaterialPlanJoinRow();
        row.setMaterialId(10L);
        row.setMaterialPlanId(20L);
        row.setSlotId(30L);
        row.setPlanId(20L);
        row.setBudgetTotal(10_000L);
        row.setBudgetDaily(1_000L);
        row.setBidPrice(100L);
        row.setBillingType("CPC");
        return row;
    }

    private EventMaterialMetadata metadata() {
        return new EventMaterialMetadata(20L, 30L, 10_000L, 1_000L, 100L, "CPC");
    }

    private record Fixture(
            MaterialMetadataRedisServiceImpl service,
            MaterialMetadataRedisLock lockManager) {
    }

    private record InterruptedResult(Throwable failure, boolean interrupted) {
    }
}
