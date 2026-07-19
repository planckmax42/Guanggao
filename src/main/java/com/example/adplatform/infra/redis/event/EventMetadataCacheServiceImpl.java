package com.example.adplatform.infra.redis.event;

import com.example.adplatform.admin.mapper.MaterialMapper;
import com.example.adplatform.admin.query.MaterialPlanJoinRow;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.infra.redis.RedisKeyConstants;
import com.example.adplatform.tracking.service.EventMaterialMetadata;
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
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventMetadataCacheServiceImpl implements EventMetadataCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final MaterialMapper materialMapper;
    private final MaterialIdBloomFilterManager bloomFilterManager;
    private final EventMetadataCacheProperties properties;
    private final EventMetadataCacheLockManager lockManager;

    @Override
    public EventMaterialMetadata get(Long materialId) {
        if (materialId == null || materialId <= 0) {
            throw materialNotFound();
        }

        if (bloomFilterManager.definitelyNotContains(materialId)) {
            throw materialNotFound();
        }
        EventMaterialMetadata cached = readRedis(materialId);
        if (cached != null) {
            return cached;
        }

        // 三个 Consumer Group 可能同时首次遇到同一素材。按 materialId 合并回源，
        // 获得锁后二次检查 Redis，避免同一 JVM 打出三次相同 JOIN。
        EventMetadataCacheLockManager.LockHandle readLock =
                lockManager.tryAcquireForRead(materialId).orElse(null);
        if (readLock == null) {
            if (Thread.currentThread().isInterrupted()) {
                log.warn("事件元数据缓存回源锁等待被中断，materialId={}", materialId);
                throw new BusinessException(
                        ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE,
                        "事件元数据查询被中断，请稍后重试");
            }
            cached = readRedis(materialId);
            if (cached != null) {
                return cached;
            }
            log.warn("事件元数据缓存回源锁等待超时，materialId={}", materialId);
            throw new BusinessException(
                    ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE,
                    "事件元数据查询繁忙，请稍后重试");
        }

        try (readLock) {
            cached = readRedis(materialId);
            if (cached != null) {
                return cached;
            }
            if (bloomFilterManager.definitelyNotContains(materialId)) {
                throw materialNotFound();
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

    @Override
    public void refreshAfterCommit(Long materialId, EventMaterialMetadata metadata) {
        if (materialId == null || metadata == null) {
            return;
        }
        // INSERT 成功获得 ID 后立即加入 Bloom，避免提交到 afterCommit 之间的假阴性误杀。
        // 事务回滚只会留下可安全回源并在重建时清理的假阳性。
        bloomFilterManager.put(materialId);
        afterCommit(() -> {
            try (EventMetadataCacheLockManager.LockHandle ignored =
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
            try (EventMetadataCacheLockManager.LockHandle ignored =
                         lockManager.acquireForWrite(materialIds)) {
                evict(materialIds);
            }
        });
    }

    @Override
    public boolean rebuildBloomFilter() {
        try {
            return bloomFilterManager.rebuild(materialMapper::selectAllMaterialIds).isPresent();
        } catch (RuntimeException ex) {
            log.warn("事件元数据 materialId 布隆过滤器重建失败，保留旧过滤器", ex);
            return false;
        }
    }

    @Override
    public boolean expandAndRebuildBloomFilter() {
        try {
            return bloomFilterManager.expandAndRebuild(materialMapper::selectAllMaterialIds).isPresent();
        } catch (RuntimeException ex) {
            log.warn("事件元数据 materialId 布隆过滤器扩容失败", ex);
            return false;
        }
    }

    private EventMaterialMetadata readRedis(Long materialId) {
        String value;
        try {
            value = stringRedisTemplate.opsForValue().get(RedisKeyConstants.eventMaterialMetadata(materialId));
        } catch (RuntimeException ex) {
            log.warn("读取事件素材元数据缓存失败，materialId={}，本次回源 MySQL", materialId);
            return null;
        }
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            EventMaterialMetadata metadata = objectMapper.readValue(value, EventMaterialMetadata.class);
            return metadata;
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("事件素材元数据缓存格式无效，materialId={}，已删除并回源 MySQL", materialId);
            evict(List.of(materialId));
            return null;
        }
    }

    private void cache(Long materialId, EventMaterialMetadata metadata) {
        // 先更新本地布隆：Redis 写失败时仍可回源 MySQL，不会误杀。
        bloomFilterManager.put(materialId);
        writeRedis(materialId, metadata);
    }

    private void writeRedis(Long materialId, EventMaterialMetadata metadata) {
        try {
            String value = objectMapper.writeValueAsString(metadata);
            stringRedisTemplate.opsForValue().set(
                    RedisKeyConstants.eventMaterialMetadata(materialId),
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
                .map(RedisKeyConstants::eventMaterialMetadata)
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
}
