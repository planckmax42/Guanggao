package com.example.adplatform.infra.bloom.delivery.slot;

import com.example.adplatform.infra.redis.delivery.slot.SlotCacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 根据线上实际误判率和位图理论误判率判断是否需要扩容重建。
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class SlotBloomFilterExpansionScheduler {

    private final SlotBloomFilterMetrics metrics;
    private final SlotCodeBloomFilterManager bloomFilterManager;
    private final SlotCacheProperties properties;
    private final SlotBloomMaintenance slotCacheService;

    private volatile Instant lastExpansionTime = Instant.EPOCH;

    /**
     * 按配置周期检查实际误判率和位图理论误判率，达到阈值后触发扩容重建。
     *
     * <p>样本数不足、仍在冷却期、过滤器未就绪或已达容量上限时不执行扩容。</p>
     */
    @Scheduled(
            fixedDelayString = "${app.slot-cache.bloom.expansion-check-delay-ms}",
            initialDelayString = "${app.slot-cache.bloom.expansion-check-initial-delay-ms}")
    public void checkAndExpand() {
        SlotCacheProperties.Bloom config = properties.getBloom();
        SlotBloomFilterMetrics.Snapshot snapshot = metrics.snapshot();
        SlotCodeBloomFilterManager.Status status = bloomFilterManager.status();
        if (!status.ready() || snapshot.absentSampleCount() < config.getMinimumAbsentSamples()) {
            return;
        }

        double targetRate = config.getFalsePositiveProbability();
        if (snapshot.actualFalsePositiveRate() < targetRate
                || status.expectedFalsePositiveProbability() < targetRate) {
            return;
        }
        if (status.expectedInsertions() >= config.getMaxExpectedInsertions()) {
            log.warn("广告位布隆过滤器已达到自动扩容上限，当前容量={}，实际误判率={}",
                    status.expectedInsertions(), snapshot.actualFalsePositiveRate());
            return;
        }

        Duration cooldown = config.getExpansionCooldown();
        if (lastExpansionTime.plus(cooldown).isAfter(Instant.now())) {
            return;
        }

        long oldCapacity = status.expectedInsertions();
        if (slotCacheService.expandAndRebuildBloomFilter()) {
            lastExpansionTime = Instant.now();
            SlotCodeBloomFilterManager.Status expandedStatus = bloomFilterManager.status();
            log.warn("广告位布隆过滤器因误判率升高完成扩容，容量 {} -> {}，扩容前实际误判率={}，理论误判率={}",
                    oldCapacity,
                    expandedStatus.expectedInsertions(),
                    snapshot.actualFalsePositiveRate(),
                    status.expectedFalsePositiveProbability());
        }
    }
}
