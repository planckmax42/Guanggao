package com.example.adplatform.infra.warmup;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.admin.port.SlotCacheMaintenancePort;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.infra.bloom.delivery.slot.SlotBloomFilterManager;
import com.example.adplatform.infra.bloom.delivery.slot.SlotBloomFilterRebuilder;
import com.example.adplatform.infra.bloom.delivery.slot.SlotBloomFilterTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** 启动时重建广告位布隆过滤器，并使用同一份数据快照预热 Redis。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlotWarmUpTask implements SlotBloomFilterRebuilder {

    private final SlotMapper slotMapper;
    private final SlotBloomFilterManager bloomFilterManager;
    private final SlotBloomFilterTracker bloomFilterTracker;
    private final SlotCacheMaintenancePort slotCacheMaintenancePort;

    /** 重建布隆过滤器后，复用本次查询快照预热 Redis。 */
    public void warmUp() {
        Optional<List<SlotEntity>> enabledSlots = rebuildWithSnapshot();
        if (enabledSlots.isEmpty()) {
            return;
        }

        int processedCount = 0;
        for (SlotEntity snapshot : enabledSlots.get()) {
            try {
                slotCacheMaintenancePort.refreshSlotByCode(snapshot.getSlotCode());
                processedCount++;
            } catch (RuntimeException ex) {
                log.warn("广告位 Redis 预热失败，已停止本次预热，slotCode={}", snapshot.getSlotCode(), ex);
                return;
            }
        }
        log.info("广告位 Redis 预热完成，处理数量={}", processedCount);
    }

    /** {@inheritDoc} */
    @Override
    public boolean rebuild() {
        return rebuildWithSnapshot().isPresent();
    }

    /** {@inheritDoc} */
    @Override
    public boolean expandAndRebuild() {
        try {
            Optional<List<SlotEntity>> enabledSlots =
                    bloomFilterManager.expandAndRebuild(this::selectAllEnabledSlots);
            if (enabledSlots.isEmpty()) {
                return false;
            }
            bloomFilterTracker.reset();
            return true;
        } catch (RuntimeException ex) {
            log.warn("广告位布隆过滤器扩容重建失败，继续使用当前过滤器", ex);
            return false;
        }
    }

    private Optional<List<SlotEntity>> rebuildWithSnapshot() {
        try {
            Optional<List<SlotEntity>> enabledSlots = bloomFilterManager.rebuild(this::selectAllEnabledSlots);
            enabledSlots.ifPresent(slots -> {
                bloomFilterTracker.reset();
                log.info("广告位布隆过滤器重建完成，启用广告位数量={}", slots.size());
            });
            return enabledSlots;
        } catch (RuntimeException ex) {
            log.warn("广告位布隆过滤器重建失败，保留当前过滤器", ex);
            return Optional.empty();
        }
    }

    private List<SlotEntity> selectAllEnabledSlots() {
        return slotMapper.selectList(new LambdaQueryWrapper<SlotEntity>()
                .eq(SlotEntity::getStatus, CommonStatus.ENABLED));
    }
}
