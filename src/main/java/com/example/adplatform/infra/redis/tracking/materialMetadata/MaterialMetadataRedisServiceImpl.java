package com.example.adplatform.infra.redis.tracking.materialMetadata;

import com.example.adplatform.admin.mapper.MaterialMapper;
import com.example.adplatform.admin.port.EventMetadataCacheMaintenancePort;
import com.example.adplatform.admin.query.MaterialPlanJoinRow;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.infra.redis.tracking.TrackingRedisKeys;
import com.example.adplatform.infra.bloom.tracking.materialMetadata.MaterialIdMetadataBloomFilterServiceImpl;
import com.example.adplatform.tracking.service.EventMaterialMetadata;
import com.example.adplatform.tracking.port.EventMetadataReaderPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialMetadataRedisServiceImpl implements EventMetadataReaderPort, EventMetadataCacheMaintenancePort {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final MaterialMapper materialMapper;
    private final MaterialIdMetadataBloomFilterServiceImpl materialIdMetadataBloomFilterService;
    private final MaterialMetadataRedisProperties properties;
    private final MaterialMetadataRedisLock lockManager;
    private final ConcurrentHashMap<Long, CompletableFuture<EventMaterialMetadata>> inFlight = new ConcurrentHashMap<>();//线程安全的哈希表

    @Override
    public EventMaterialMetadata get(Long materialId) {
        if (materialId == null || materialId <= 0) {
            throw materialNotFound();//防御性校验
        }

        if (materialIdMetadataBloomFilterService.definitelyNotContains(materialId)) {//todo:看是否开启预热和动态重建机制
            throw materialNotFound();//布隆过滤器初筛
        }
        EventMaterialMetadata cached = readRedis(materialId);//redis取值
        if (cached != null) {
            return cached;
        }

        CompletableFuture<EventMaterialMetadata> created = new CompletableFuture<>();// 同一 JVM 内的三个 Consumer Group 共享同一次回源结果。
        CompletableFuture<EventMaterialMetadata> existing = inFlight.putIfAbsent(materialId, created);//线程安全hashmap实现单航班
        if (existing != null) {
            return awaitSingleFlight(materialId, existing);
        }

        try {
            EventMaterialMetadata metadata = loadAndCacheAsLeader(materialId);
            created.complete(metadata);
            return metadata;
        } catch (RuntimeException ex) {//todo:多种异常机制
            created.completeExceptionally(ex);
            throw ex;
        } catch (Error ex) {
            created.completeExceptionally(ex);
            throw ex;
        } finally {
            inFlight.remove(materialId, created);
        }
    }

    private EventMaterialMetadata loadAndCacheAsLeader(Long materialId) {
        MaterialMetadataRedisLock.LockHandle loadLock =
                lockManager.tryAcquireForRead(materialId).orElse(null);//todo:获取失败了怎么处理
        if (loadLock == null) {
            if (Thread.currentThread().isInterrupted()) {//todo:多种异常处理
                log.warn("事件元数据缓存回源锁等待被中断，materialId={}", materialId);
                throw dependencyUnavailable("事件元数据查询被中断，请稍后重试");
            }
            EventMaterialMetadata cached = readRedis(materialId);
            if (cached != null) {
                return cached;
            }
            log.warn("事件元数据缓存回源锁等待超时，materialId={}", materialId);
            throw dependencyUnavailable("事件元数据查询繁忙，请稍后重试");
        }

        // 条带锁只由 Single Flight 领导者获取，用于与提交后缓存失效互斥。
        try (loadLock) {
            EventMaterialMetadata cached = readRedis(materialId);
            if (cached != null) {
                return cached;
            }
            if (materialIdMetadataBloomFilterService.definitelyNotContains(materialId)) {
                throw materialNotFound();//todo:挡在mysql前面的redis和bloom，再次检查能否减少一次mysql查询
            }

            MaterialPlanJoinRow row = materialMapper.selectMaterialPlanById(materialId);
            if (row == null) {
                throw materialNotFound();
            }
            if (row.getPlanId() == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告计划不存在");
            }
            EventMaterialMetadata metadata = toMetadata(row);
            cache(materialId, metadata);
            return metadata;
        }
    }

    private EventMaterialMetadata awaitSingleFlight(
            Long materialId,
            CompletableFuture<EventMaterialMetadata> future) {
        try {
            return future.get(
                    properties.getSingleFlightWaitTimeout().toNanos(),
                    TimeUnit.NANOSECONDS);
        } catch (InterruptedException ex) {//todo:多种异常处理分析
            Thread.currentThread().interrupt();
            log.warn("等待事件元数据共享回源结果被中断，materialId={}", materialId);
            throw dependencyUnavailable("事件元数据查询被中断，请稍后重试");
        } catch (TimeoutException ex) {
            EventMaterialMetadata cached = readRedis(materialId);
            if (cached != null) {
                return cached;
            }
            log.warn("等待事件元数据共享回源结果超时，materialId={}", materialId);
            throw dependencyUnavailable("事件元数据查询繁忙，请稍后重试");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("事件元数据共享回源任务异常", cause);
        }
    }

    @Override
    public void refreshAfterCommit(Long materialId, EventMaterialMetadata metadata) {
        if (materialId == null || metadata == null) {
            return;
        }
        // INSERT 成功获得 ID 后立即加入 Bloom，避免提交到 afterCommit 之间的假阴性误杀。
        // 事务回滚只会留下可安全回源并在重建时清理的假阳性。
        materialIdMetadataBloomFilterService.addBloomFilter(materialId);
        afterCommit(() -> {
            try (MaterialMetadataRedisLock.LockHandle ignored =
                         lockManager.acquireForWrite(List.of(materialId))) {
                writeRedis(materialId, metadata);
            }
        });
    }

    @Override
    public void evictPlanAfterCommit(Long planId) {
        if (planId == null) {
            return;
        }
        List<Long> materialIds = List.copyOf(materialMapper.selectMaterialIdsByPlanId(planId));
        afterCommit(() -> {
            try (MaterialMetadataRedisLock.LockHandle ignored =
                         lockManager.acquireForWrite(materialIds)) {
                evict(materialIds);
            }
        });
    }

    private EventMaterialMetadata readRedis(Long materialId) {
        String value;
        try {
            value = stringRedisTemplate.opsForValue().get(TrackingRedisKeys.eventMaterialMetadata(materialId));
        } catch (RuntimeException ex) {
            log.warn("读取事件素材元数据缓存失败，materialId={}，本次回源 MySQL", materialId);
            return null;
        }
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            EventMaterialMetadata metadata = objectMapper.readValue(value, EventMaterialMetadata.class);//JSON反序列化object
            return metadata;
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("事件素材元数据缓存格式无效，materialId={}，已删除并回源 MySQL", materialId);
            evict(List.of(materialId));
            return null;
        }
    }

    private void cache(Long materialId, EventMaterialMetadata metadata) {
        // 先更新本地布隆：Redis 写失败时仍可回源 MySQL，不会误杀。
        materialIdMetadataBloomFilterService.addBloomFilter(materialId);
        writeRedis(materialId, metadata);
    }

    private void writeRedis(Long materialId, EventMaterialMetadata metadata) {
        try {
            String value = objectMapper.writeValueAsString(metadata);
            stringRedisTemplate.opsForValue().set(
                    TrackingRedisKeys.eventMaterialMetadata(materialId),
                    value,
                    randomizedTtl());
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("写入事件素材元数据缓存失败，materialId={}，后续请求将回源 MySQL",
                    materialId);
        }
    }

    private void evict(List<Long> materialIds) {
        if (materialIds.isEmpty()) {
            return;
        }
        List<String> keys = materialIds.stream()
                .map(TrackingRedisKeys::eventMaterialMetadata)
                .toList();
        try {
            stringRedisTemplate.delete(keys);
        } catch (RuntimeException ex) {
            log.warn("删除计划关联的事件元数据缓存失败，materialIds={}", materialIds);
        }
    }

    private Duration randomizedTtl() {
        long jitterSeconds = Math.max(0, properties.getRedisTtlJitter().toSeconds());
        long extraSeconds = jitterSeconds == 0
                ? 0
                : ThreadLocalRandom.current().nextLong(jitterSeconds + 1);
        return properties.getRedisTtl().plusSeconds(extraSeconds);
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    private EventMaterialMetadata toMetadata(MaterialPlanJoinRow row) {
        return new EventMaterialMetadata(
                row.getPlanId(),
                row.getSlotId(),
                row.getBudgetTotal(),
                row.getBudgetDaily(),
                row.getBidPrice(),
                row.getBillingType());
    }

    private BusinessException materialNotFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告素材不存在");
    }

    private BusinessException dependencyUnavailable(String message) {
        return new BusinessException(ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE, message);
    }
}
