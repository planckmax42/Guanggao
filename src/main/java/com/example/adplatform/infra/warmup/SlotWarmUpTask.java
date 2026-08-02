package com.example.adplatform.infra.warmup;

import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.port.SlotCacheMaintenancePort;
import com.example.adplatform.infra.bloom.delivery.slot.SlotBloomFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** 启动时重建广告位布隆过滤器，并使用同一份数据快照预热 Redis。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlotWarmUpTask {

    private final SlotBloomFilterService bloomFilterService;
    private final SlotCacheMaintenancePort slotCacheMaintenancePort;

    /** 重建布隆过滤器后，复用本次查询快照预热 Redis。 */
    public void warmUp() {
        Optional<List<SlotEntity>> enabledSlots =
                bloomFilterService.regularRebuild();
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
}
