package com.example.adplatform.infra.redis.slot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.infra.redis.RedisKeyConstants;
import com.example.adplatform.infra.redis.slot.bloom.SlotBloomFilterMetrics;
import com.example.adplatform.infra.redis.slot.bloom.SlotCodeBloomFilterManager;
import com.example.adplatform.infra.redis.slot.resilience.SlotMysqlCircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class SlotCacheServiceImpl implements SlotCacheService {

    private static final Logger log = LoggerFactory.getLogger(SlotCacheServiceImpl.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final SlotMapper slotMapper;
    private final SlotCacheProperties properties;
    private final SlotCodeBloomFilterManager bloomFilterManager;
    private final SlotBloomFilterMetrics bloomFilterMetrics;
    private final SlotMysqlCircuitBreaker mysqlCircuitBreaker;

    @Override
    public Optional<Long> getEnabledSlotIdByCode(String slotCode) {
        if (!StringUtils.hasText(slotCode)) {//防御性校验，防止绕过controller层传入非法参数
            return Optional.empty();
        }
        if (bloomFilterManager.definitelyNotContains(slotCode)) {
            bloomFilterMetrics.recordDefiniteMiss();
            return Optional.empty();
        }

        Optional<Long> cachedSlotId = getSlotIdFromRedis(slotCode);
        if (cachedSlotId.isPresent()) {
            return cachedSlotId;
        }

        SlotEntity slot;
        try {
            slot = mysqlCircuitBreaker.execute(() -> selectEnabledSlotByCode(slotCode));
        } catch (CallNotPermittedException ex) {
            throw new BusinessException(
                    ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE,
                    "广告位查询服务已熔断，请稍后重试");
        } catch (RuntimeException ex) {
            log.warn("广告位缓存回源 MySQL 失败，slotCode={}", slotCode, ex);
            throw new BusinessException(
                    ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE,
                    "广告位查询服务暂时不可用，请稍后重试");
        }

        if (slot == null) {
            if (bloomFilterManager.isReady()) {
                bloomFilterMetrics.recordFalsePositive();
            }
            return Optional.empty();
        }
        cacheSlot(slot);
        return Optional.of(slot.getId());
    }

    @Override
    public void cacheSlot(SlotEntity slot) {
        if (slot == null || !StringUtils.hasText(slot.getSlotCode())) {
            return;
        }
        if (!Objects.equals(slot.getStatus(), CommonStatus.ENABLED)) {
            evictSlotCode(slot.getSlotCode());
            return;
        }

        bloomFilterManager.put(slot.getSlotCode());
        try {
            stringRedisTemplate.opsForValue().set(
                    RedisKeyConstants.slotCodeToId(slot.getSlotCode()),
                    String.valueOf(slot.getId()),
                    properties.getRedisTtl());
        } catch (RuntimeException ex) {
            log.warn("写入广告位缓存失败，slotCode={}，后续请求将回源 MySQL：{}", slot.getSlotCode(), ex.getMessage());
        }
    }

    @Override
    public void refreshSlot(SlotEntity slot, String oldSlotCode) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    refreshSlotCache(slot, oldSlotCode);
                }
            });
            return;
        }
        refreshSlotCache(slot, oldSlotCode);
    }

    private void refreshSlotCache(SlotEntity slot, String oldSlotCode) {
        if (StringUtils.hasText(oldSlotCode) && (slot == null || !oldSlotCode.equals(slot.getSlotCode()))) {
            evictSlotCode(oldSlotCode);
        }
        cacheSlot(slot);
    }

    @Override
    public void warmUp() {
        Optional<List<SlotEntity>> enabledSlots = rebuildBloomFilterAndReturnSlots();
        if (enabledSlots.isEmpty()) {
            return;
        }

        int successCount = 0;
        for (SlotEntity slot : enabledSlots.get()) {
            try {
                stringRedisTemplate.opsForValue().set(
                        RedisKeyConstants.slotCodeToId(slot.getSlotCode()),
                        String.valueOf(slot.getId()),
                        properties.getRedisTtl());
                successCount++;
            } catch (RuntimeException ex) {
                log.warn("广告位缓存预热写入 Redis 失败，已停止本次 Redis 预热：{}", ex.getMessage());
                break;
            }
        }
        bloomFilterMetrics.reset();
        log.info("广告位 Redis 缓存预热完成，数量={}", successCount);
    }

    @Override
    public boolean rebuildBloomFilter() {
        Optional<List<SlotEntity>> enabledSlots = rebuildBloomFilterAndReturnSlots();
        enabledSlots.ifPresent(slots -> {
            bloomFilterMetrics.reset();
            log.info("广告位布隆过滤器重建完成，启用广告位数量={}", slots.size());
        });
        return enabledSlots.isPresent();
    }

    @Override
    public boolean expandAndRebuildBloomFilter() {
        try {
            Optional<List<SlotEntity>> enabledSlots = bloomFilterManager.expandAndRebuild(
                    this::selectAllEnabledSlots,
                    slots -> slots.stream().map(SlotEntity::getSlotCode).toList());
            enabledSlots.ifPresent(slots -> bloomFilterMetrics.reset());
            return enabledSlots.isPresent();
        } catch (RuntimeException ex) {
            log.warn("广告位布隆过滤器扩容重建失败，继续使用当前过滤器", ex);
            return false;
        }
    }

    private Optional<List<SlotEntity>> rebuildBloomFilterAndReturnSlots() {
        try {
            return bloomFilterManager.rebuild(
                    this::selectAllEnabledSlots,
                    slots -> slots.stream().map(SlotEntity::getSlotCode).toList());
        } catch (RuntimeException ex) {
            log.warn("查询启用广告位失败，保留当前布隆过滤器", ex);
            return Optional.empty();
        }
    }

    private SlotEntity selectEnabledSlotByCode(String slotCode) {
        return slotMapper.selectOne(new LambdaQueryWrapper<SlotEntity>()
                .eq(SlotEntity::getSlotCode, slotCode)
                .eq(SlotEntity::getStatus, CommonStatus.ENABLED));
    }

    private List<SlotEntity> selectAllEnabledSlots() {
        return slotMapper.selectList(new LambdaQueryWrapper<SlotEntity>()
                .eq(SlotEntity::getStatus, CommonStatus.ENABLED));
    }

    private Optional<Long> getSlotIdFromRedis(String slotCode) {
        String value;
        try {
            value = stringRedisTemplate.opsForValue().get(RedisKeyConstants.slotCodeToId(slotCode));
        } catch (RuntimeException ex) {
            log.warn("读取广告位缓存失败，slotCode={}，本次请求回源 MySQL：{}", slotCode, ex.getMessage());
            return Optional.empty();
        }
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.valueOf(value));
        } catch (NumberFormatException ex) {
            evictSlotCode(slotCode);
            return Optional.empty();
        }
    }

    private void evictSlotCode(String slotCode) {
        if (!StringUtils.hasText(slotCode)) {
            return;
        }
        try {
            stringRedisTemplate.delete(RedisKeyConstants.slotCodeToId(slotCode));
        } catch (RuntimeException ex) {
            log.warn("删除广告位缓存失败，slotCode={}：{}", slotCode, ex.getMessage());
        }
    }
}
