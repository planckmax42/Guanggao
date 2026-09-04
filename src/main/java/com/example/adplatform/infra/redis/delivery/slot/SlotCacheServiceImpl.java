package com.example.adplatform.infra.redis.delivery.slot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.admin.port.slot.SlotCachePort;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.exception.DependencyException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.delivery.port.SlotLookupPort;
import com.example.adplatform.delivery.port.SlotLookupResult;
import com.example.adplatform.infra.bloomfilter.delivery.slot.SlotBloomOperationsService;
import com.example.adplatform.infra.redis.delivery.DeliveryRedisKeys;
import com.example.adplatform.infra.resilience.delivery.slot.SlotMysqlCircuitBreaker;
import com.example.adplatform.infra.resilience.delivery.slot.SlotRedisCircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 广告位编码缓存的默认实现。
 *
 * <p>Redis 健康时允许缓存未命中回源 MySQL；Redis 访问异常或熔断时返回
 * {@link SlotLookupResult.Status#CACHE_UNAVAILABLE}，由投放层按 no-fill 停投。</p>
 */
@RequiredArgsConstructor
@Service
public class SlotCacheServiceImpl implements SlotLookupPort, SlotCachePort {

    private static final Logger log = LoggerFactory.getLogger(SlotCacheServiceImpl.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final SlotMapper slotMapper;
    private final SlotCacheProperties properties;
    private final SlotBloomOperationsService slotBloomOperationsService;
    private final SlotMysqlCircuitBreaker mysqlCircuitBreaker;
    private final SlotRedisCircuitBreaker redisCircuitBreaker;
    private final SlotCacheLockManager lockManager;
    private final MeterRegistry meterRegistry;
    private final SlotCacheFailureLogLimiter failureLogLimiter;

    @Override
    public SlotLookupResult getEnabledSlotIdByCode(String slotCode) {
        RedisReadResult firstRead = readSlotId(slotCode);
        if (firstRead.status() == RedisReadStatus.HIT) {
            return SlotLookupResult.enabled(firstRead.slotId());
        }
        if (firstRead.status() == RedisReadStatus.UNAVAILABLE) {
            return SlotLookupResult.cacheUnavailable();
        }

        // Redis 先于本地布隆过滤器，避免多实例布隆快照延迟导致新广告位假阴性。
        if (slotBloomOperationsService.definiteNotContain(slotCode)) {
            slotBloomOperationsService.recordDefiniteNotContain();
            return SlotLookupResult.notFound();
        }

        SlotCacheLockManager.LockHandle readLock = lockManager.tryAcquireForRead(slotCode).orElse(null);
        if (readLock == null) {
            if (Thread.currentThread().isInterrupted()) {
                log.warn("广告位缓存回源锁等待被中断，slotCode={}", slotCode);
                throw new DependencyException(
                        ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE,
                        "广告位查询被中断，请稍后重试");
            }
            RedisReadResult retryRead = readSlotId(slotCode);
            if (retryRead.status() == RedisReadStatus.HIT) {
                return SlotLookupResult.enabled(retryRead.slotId());
            }
            if (retryRead.status() == RedisReadStatus.UNAVAILABLE) {
                return SlotLookupResult.cacheUnavailable();
            }
            log.warn("广告位缓存回源锁等待超时，slotCode={}", slotCode);
            throw new DependencyException(
                    ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE,
                    "广告位查询繁忙，请稍后重试");
        }

        try (readLock) {
            RedisReadResult retryRead = readSlotId(slotCode);
            if (retryRead.status() == RedisReadStatus.HIT) {
                return SlotLookupResult.enabled(retryRead.slotId());
            }
            if (retryRead.status() == RedisReadStatus.UNAVAILABLE) {
                return SlotLookupResult.cacheUnavailable();
            }
            if (slotBloomOperationsService.definiteNotContain(slotCode)) {
                slotBloomOperationsService.recordDefiniteNotContain();
                return SlotLookupResult.notFound();
            }
            return loadEnabledSlotFromMysql(slotCode);
        }
    }

    private SlotLookupResult loadEnabledSlotFromMysql(String slotCode) {
        SlotEntity slot;
        try {
            slot = mysqlCircuitBreaker.execute(() -> selectEnabledSlotByCode(slotCode));
        } catch (CallNotPermittedException ex) {
            throw new DependencyException(
                    ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE,
                    "广告位查询服务已熔断，请稍后重试",
                    ex);
        } catch (RuntimeException ex) {
            log.warn("广告位缓存回源 MySQL 失败，slotCode={}", slotCode, ex);
            throw new DependencyException(
                    ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE,
                    "广告位查询服务暂时不可用，请稍后重试",
                    ex);
        }

        if (slot == null) {
            if (slotBloomOperationsService.getBloomSnapshot().bloomFilterReady()) {
                slotBloomOperationsService.recordFalsePositive();
            }
            return SlotLookupResult.notFound();
        }
        try {
            cacheSlot(slot);
        } catch (SlotCacheAccessException ex) {
            // MySQL 已确认本次请求的状态，回填失败不影响当前结果。
            if (failureLogLimiter.shouldLog("backfill")) {
                log.warn("广告位回源后写入 Redis 失败，slotCode={}", slotCode, ex);
            }
        }
        return SlotLookupResult.enabled(slot.getId());
    }

    public void cacheSlot(SlotEntity slot) {
        if (slot == null || !StringUtils.hasText(slot.getSlotCode())) {
            return;
        }
        if (Objects.equals(slot.getStatus(), CommonStatus.ENABLED)) {
            slotBloomOperationsService.addSlotFilter(slot.getSlotCode());
            writeSlotToRedis(slot.getSlotCode(), slot.getId());
        } else {
            evictSlotCodeFromRedis(slot.getSlotCode());
        }
    }

    @Override
    public void refreshSlotByCode(String slotCode) {
        if (!StringUtils.hasText(slotCode)) {
            return;
        }
        try (SlotCacheLockManager.LockHandle ignored = lockManager.acquireForWrite(slotCode)) {
            SlotEntity current = selectEnabledSlotByCode(slotCode);
            if (current == null) {
                evictSlotCodeFromRedis(slotCode);
            } else {
                writeSlotToRedis(current.getSlotCode(), current.getId());
            }
        }
    }

    @Override
    public void reconcileSlot(String slotPublicId, String previousSlotCode) {
        long startNanos = System.nanoTime();
        try {
            SlotEntity current = slotMapper.selectOne(new LambdaQueryWrapper<SlotEntity>()
                    .eq(SlotEntity::getPublicId, slotPublicId));
            String currentSlotCode = current == null ? null : current.getSlotCode();
            try (SlotCacheLockManager.LockHandle ignored =
                         lockManager.acquireForWrite(previousSlotCode, currentSlotCode)) {
                if (StringUtils.hasText(previousSlotCode)
                        && !Objects.equals(previousSlotCode, currentSlotCode)) {
                    evictSlotCodeFromRedis(previousSlotCode);
                }
                if (current != null && Objects.equals(current.getStatus(), CommonStatus.ENABLED)) {
                    slotBloomOperationsService.addSlotFilter(currentSlotCode);
                    writeSlotToRedis(currentSlotCode, current.getId());
                } else if (current != null) {
                    evictSlotCodeFromRedis(currentSlotCode);
                }
            }
            recordOperation("reconcile", "success", startNanos);
        } catch (RuntimeException ex) {
            recordOperation("reconcile", "failure", startNanos);
            throw ex;
        }
    }

    private SlotEntity selectEnabledSlotByCode(String slotCode) {
        return slotMapper.selectOne(new LambdaQueryWrapper<SlotEntity>()
                .eq(SlotEntity::getSlotCode, slotCode)
                .eq(SlotEntity::getStatus, CommonStatus.ENABLED));
    }

    private RedisReadResult readSlotId(String slotCode) {
        long startNanos = System.nanoTime();
        String value;
        try {
            value = redisCircuitBreaker.execute(() -> stringRedisTemplate.opsForValue()
                    .get(DeliveryRedisKeys.slotCodeToId(slotCode)));
        } catch (DataAccessException | CallNotPermittedException ex) {
            recordOperation("read", ex instanceof CallNotPermittedException ? "rejected" : "failure", startNanos);
            boolean failClosed = properties.getFailurePolicy() == SlotCacheProperties.FailurePolicy.FAIL_CLOSED;
            if (failureLogLimiter.shouldLog("read")) {
                log.warn("读取广告位缓存失败，slotCode={}，policy={}",
                        slotCode, properties.getFailurePolicy(), ex);
            }
            return failClosed ? RedisReadResult.unavailable() : RedisReadResult.miss();
        }
        if (!StringUtils.hasText(value)) {
            recordOperation("read", "miss", startNanos);
            return RedisReadResult.miss();
        }
        try {
            Long slotId = Long.valueOf(value);
            recordOperation("read", "hit", startNanos);
            return RedisReadResult.hit(slotId);
        } catch (NumberFormatException ex) {
            log.error("广告位缓存值格式错误，slotCode={}，value={}", slotCode, value, ex);
            try {
                evictSlotCodeFromRedis(slotCode);
                return RedisReadResult.miss();
            } catch (SlotCacheAccessException deleteFailure) {
                return RedisReadResult.unavailable();
            }
        }
    }

    @Override
    public void writeSlotToRedis(String slotCode, Long slotId) {
        long startNanos = System.nanoTime();
        try {
            redisCircuitBreaker.execute(() -> {
                stringRedisTemplate.opsForValue().set(
                        DeliveryRedisKeys.slotCodeToId(slotCode),
                        String.valueOf(slotId),
                        properties.getRedisTtl());
                return null;
            });
            recordOperation("write", "success", startNanos);
        } catch (DataAccessException | CallNotPermittedException ex) {
            recordOperation("write", ex instanceof CallNotPermittedException ? "rejected" : "failure", startNanos);
            throw new SlotCacheAccessException("write", slotCode, ex);
        }
    }

    @Override
    public void evictSlotCodeFromRedis(String slotCode) {
        long startNanos = System.nanoTime();
        try {
            // false 表示键已不存在，删除的最终目标已达成，仍视为成功。
            redisCircuitBreaker.execute(() ->
                    stringRedisTemplate.delete(DeliveryRedisKeys.slotCodeToId(slotCode)));
            recordOperation("evict", "success", startNanos);
        } catch (DataAccessException | CallNotPermittedException ex) {
            recordOperation("evict", ex instanceof CallNotPermittedException ? "rejected" : "failure", startNanos);
            throw new SlotCacheAccessException("evict", slotCode, ex);
        }
    }

    private void recordOperation(String operation, String result, long startNanos) {
        meterRegistry.counter("ad.slot.cache.operation", "operation", operation, "result", result)
                .increment();
        meterRegistry.timer("ad.slot.cache.operation.duration", "operation", operation)
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    private enum RedisReadStatus {
        HIT,
        MISS,
        UNAVAILABLE
    }

    private record RedisReadResult(RedisReadStatus status, Long slotId) {
        private static RedisReadResult hit(Long slotId) {
            return new RedisReadResult(RedisReadStatus.HIT, slotId);
        }

        private static RedisReadResult miss() {
            return new RedisReadResult(RedisReadStatus.MISS, null);
        }

        private static RedisReadResult unavailable() {
            return new RedisReadResult(RedisReadStatus.UNAVAILABLE, null);
        }
    }
}
