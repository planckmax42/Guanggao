package com.example.adplatform.infra.bloom.delivery.slot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 定期维护广告位布隆过滤器，并在误判率达到阈值时扩容重建。
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class SlotBloomFilterScheduler {

    private final SlotBloomFilterTracker slotBloomFilterTracker;
    private final SlotBloomFilterService slotBloomFilterService;
    private final SlotBloomFilterProperties slotBloomFilterProperties;

    private volatile Instant lastExpansionTime = Instant.EPOCH;

    /**
     * 按固定间隔全量重建布隆过滤器，清除无法单独删除的旧编码。
     */
    @Scheduled(
            fixedDelayString = "${app.slot-cache.bloom.rebuild-delay}",
            initialDelayString = "${app.slot-cache.bloom.rebuild-initial-delay}")
    public void rebuild() {
        slotBloomFilterService.rebuild();
    }

    /**
     * 按配置周期检查实际误判率和位图理论误判率，达到阈值后触发扩容重建。
     *
     * <p>样本数不足、仍在冷却期、过滤器未就绪或已达容量上限时不执行扩容。</p>
     */
    @Scheduled(
            fixedDelayString = "${app.slot-cache.bloom.expansion-check-delay}",
            initialDelayString = "${app.slot-cache.bloom.expansion-check-initial-delay}")
    public void checkAndExpand() {
        SlotBloomFilterTracker.Snapshot snapshot = slotBloomFilterTracker.snapshot();
        SlotBloomFilterService.Status status = slotBloomFilterService.status();
        if (!status.ready() || snapshot.confirmedAbsentCount() < slotBloomFilterProperties.getMinimumAbsentSamples()) {
            return;
        }

        double targetRate = slotBloomFilterProperties.getFalsePositiveProbability();
        if (snapshot.actualFalsePositiveRate() < targetRate
                || status.expectedFalsePositiveProbability() < targetRate) {
            return;
        }
        if (status.expectedInsertions() >= slotBloomFilterProperties.getMaxExpectedInsertions()) {
            log.warn("广告位布隆过滤器已达到自动扩容上限，当前容量={}，实际误判率={}",
                    status.expectedInsertions(), snapshot.actualFalsePositiveRate());
            return;
        }

        Duration cooldown = slotBloomFilterProperties.getExpansionCooldown();
        if (lastExpansionTime.plus(cooldown).isAfter(Instant.now())) {
            return;
        }

        long oldCapacity = status.expectedInsertions();
        if (slotBloomFilterService.expandAndRebuild()) {
            lastExpansionTime = Instant.now();
            SlotBloomFilterService.Status expandedStatus = slotBloomFilterService.status();
            log.warn("广告位布隆过滤器因误判率升高完成扩容，容量 {} -> {}，扩容前实际误判率={}，理论误判率={}",
                    oldCapacity,
                    expandedStatus.expectedInsertions(),
                    snapshot.actualFalsePositiveRate(),
                    status.expectedFalsePositiveProbability());
        }
    }
}
