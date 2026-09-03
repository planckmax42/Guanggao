package com.example.adplatform.infra.redis.tracking.materialMetadata;

import com.example.adplatform.admin.mapper.MaterialMapper;
import com.example.adplatform.admin.port.EventMetadataCacheMaintenancePort;
import com.example.adplatform.admin.query.MaterialPlanJoinRow;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.DependencyException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.infra.bloomfilter.tracking.materialMetadata.MaterialMetadataBloomService;
import com.example.adplatform.infra.redis.tracking.TrackingRedisKeys;
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
    private final MaterialMetadataBloomService materialIdMetadataBloomFilterService;
    private final MaterialMetadataRedisProperties properties;
    private final MaterialMetadataRedisLock lockManager;
    private final ConcurrentHashMap<String, CompletableFuture<EventMaterialMetadata>> inFlight = new ConcurrentHashMap<>();//线程安全的哈希表

    @Override
    public EventMaterialMetadata get(String materialPublicId) {
        if (!StringUtils.hasText(materialPublicId)) {
            throw materialNotFound();//防御性校验
        }

        if (materialIdMetadataBloomFilterService.definitelyNotContains(materialPublicId)) {//todo:看是否开启预热和动态重建机制
            materialIdMetadataBloomFilterService.recordDefiniteNotContain();
            throw materialNotFound();//布隆过滤器初筛
        }
        EventMaterialMetadata cached = readRedis(materialPublicId);//redis取值
        if (cached != null) {
            return cached;
        }

        CompletableFuture<EventMaterialMetadata> created = new CompletableFuture<>();// 同一 JVM 内的三个 Consumer Group 共享同一次回源结果。
        CompletableFuture<EventMaterialMetadata> existing = inFlight.putIfAbsent(materialPublicId, created);//线程安全hashmap实现单航班
        if (existing != null) {
            return awaitSingleFlight(materialPublicId, existing);
        }

        try {
            EventMaterialMetadata metadata = loadAndCacheAsLeader(materialPublicId);
            created.complete(metadata);
            return metadata;
        } catch (RuntimeException ex) {//todo:多种异常机制
            created.completeExceptionally(ex);
            throw ex;
        } catch (Error ex) {
            created.completeExceptionally(ex);
            throw ex;
        } finally {
            inFlight.remove(materialPublicId, created);
        }
    }

    private EventMaterialMetadata loadAndCacheAsLeader(String materialPublicId) {
        MaterialMetadataRedisLock.LockHandle loadLock =
                lockManager.tryAcquireForRead(materialPublicId).orElse(null);//todo:获取失败了怎么处理
        if (loadLock == null) {
            if (Thread.currentThread().isInterrupted()) {//todo:多种异常处理
                log.warn("事件元数据缓存回源锁等待被中断，materialPublicId={}", materialPublicId);
                throw dependencyUnavailable("事件元数据查询被中断，请稍后重试");
            }
            EventMaterialMetadata cached = readRedis(materialPublicId);
            if (cached != null) {
                return cached;
            }
            log.warn("事件元数据缓存回源锁等待超时，materialPublicId={}", materialPublicId);
            throw dependencyUnavailable("事件元数据查询繁忙，请稍后重试");
        }

        // 条带锁只由 Single Flight 领导者获取，用于与提交后缓存失效互斥。
        try (loadLock) {
            EventMaterialMetadata cached = readRedis(materialPublicId);
            if (cached != null) {
                return cached;
            }
            if (materialIdMetadataBloomFilterService.definitelyNotContains(materialPublicId)) {
                materialIdMetadataBloomFilterService.recordDefiniteNotContain();
                throw materialNotFound();//todo:挡在mysql前面的redis和bloom，再次检查能否减少一次mysql查询
            }

            MaterialPlanJoinRow row = materialMapper.selectMaterialPlanByPublicId(materialPublicId);
            if (row == null) {
                if (materialIdMetadataBloomFilterService.GetBloomFilterSnapshot().bloomFilterReady()) {
                    materialIdMetadataBloomFilterService.recordFalsePositive();
                }
                throw materialNotFound();
            }
            if (row.getPlanId() == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "广告计划不存在");
            }
            EventMaterialMetadata metadata = toMetadata(row);
            cache(materialPublicId, metadata);
            return metadata;
        }
    }

    private EventMaterialMetadata awaitSingleFlight(
            String materialPublicId,
            CompletableFuture<EventMaterialMetadata> future) {
        try {
            return future.get(
                    properties.getSingleFlightWaitTimeout().toNanos(),
                    TimeUnit.NANOSECONDS);
        } catch (InterruptedException ex) {//todo:多种异常处理分析
            Thread.currentThread().interrupt();
            log.warn("等待事件元数据共享回源结果被中断，materialPublicId={}", materialPublicId);
            throw dependencyUnavailable("事件元数据查询被中断，请稍后重试");
        } catch (TimeoutException ex) {
            EventMaterialMetadata cached = readRedis(materialPublicId);
            if (cached != null) {
                return cached;
            }
            log.warn("等待事件元数据共享回源结果超时，materialPublicId={}", materialPublicId);
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
    public void refreshAfterCommit(String materialPublicId, EventMaterialMetadata metadata) {
        if (!StringUtils.hasText(materialPublicId) || metadata == null) {
            return;
        }
        // INSERT 成功获得 ID 后立即加入 Bloom，避免提交到 afterCommit 之间的假阴性误杀。
        // 事务回滚只会留下可安全回源并在重建时清理的假阳性。
        materialIdMetadataBloomFilterService.addBloomFilter(materialPublicId);
        afterCommit(() -> {
            try (MaterialMetadataRedisLock.LockHandle ignored =
                         lockManager.acquireForWrite(List.of(materialPublicId))) {
                writeRedis(materialPublicId, metadata);
            }
        });
    }

    @Override
    public void evictPlanAfterCommit(Long planId) {
        if (planId == null) {
            return;
        }
        List<String> materialPublicIds = List.copyOf(materialMapper.selectMaterialPublicIdsByPlanId(planId));
        afterCommit(() -> {
            try (MaterialMetadataRedisLock.LockHandle ignored =
                         lockManager.acquireForWrite(materialPublicIds)) {
                evict(materialPublicIds);
            }
        });
    }

    private EventMaterialMetadata readRedis(String materialPublicId) {
        String value;
        try {
            value = stringRedisTemplate.opsForValue().get(TrackingRedisKeys.eventMaterialMetadata(materialPublicId));
        } catch (RuntimeException ex) {
            log.warn("读取事件素材元数据缓存失败，materialPublicId={}，本次回源 MySQL", materialPublicId);
            return null;
        }
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            EventMaterialMetadata metadata = objectMapper.readValue(value, EventMaterialMetadata.class);//JSON反序列化object
            return metadata;
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("事件素材元数据缓存格式无效，materialPublicId={}，已删除并回源 MySQL", materialPublicId);
            evict(List.of(materialPublicId));
            return null;
        }
    }

    private void cache(String materialPublicId, EventMaterialMetadata metadata) {
        // 先更新本地布隆：Redis 写失败时仍可回源 MySQL，不会误杀。
        materialIdMetadataBloomFilterService.addBloomFilter(materialPublicId);
        writeRedis(materialPublicId, metadata);
    }

    private void writeRedis(String materialPublicId, EventMaterialMetadata metadata) {
        try {
            String value = objectMapper.writeValueAsString(metadata);
            stringRedisTemplate.opsForValue().set(
                    TrackingRedisKeys.eventMaterialMetadata(materialPublicId),
                    value,
                    randomizedTtl());
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("写入事件素材元数据缓存失败，materialPublicId={}，后续请求将回源 MySQL",
                    materialPublicId);
        }
    }

    private void evict(List<String> materialPublicIds) {
        if (materialPublicIds.isEmpty()) {
            return;
        }
        List<String> keys = materialPublicIds.stream()
                .map(TrackingRedisKeys::eventMaterialMetadata)
                .toList();
        try {
            stringRedisTemplate.delete(keys);
        } catch (RuntimeException ex) {
            log.warn("删除计划关联的事件元数据缓存失败，materialPublicIds={}", materialPublicIds);
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
                row.getMaterialId(),
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

    private DependencyException dependencyUnavailable(String message) {
        return new DependencyException(ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE, message);
    }
}
