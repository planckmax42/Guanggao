package com.example.adplatform.infra.redis.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventMetadataBloomMaintenanceScheduler {

    private final EventMetadataCacheService cacheService;
    private final MaterialIdBloomFilterManager bloomFilterManager;
    private final EventMetadataCacheProperties properties;
    private volatile Instant lastExpansionTime = Instant.EPOCH;

    @Scheduled(
            fixedDelayString = "${app.event-metadata-cache.bloom.rebuild-delay-ms}",
            initialDelayString = "${app.event-metadata-cache.bloom.rebuild-initial-delay-ms}")
    public void rebuild() {
        cacheService.rebuildBloomFilter();
    }

    @Scheduled(
            fixedDelayString = "${app.event-metadata-cache.bloom.expansion-check-delay-ms}",
            initialDelayString = "${app.event-metadata-cache.bloom.expansion-check-initial-delay-ms}")
    public void expandWhenNeeded() {
        MaterialIdBloomFilterManager.Status status = bloomFilterManager.status();
        EventMetadataCacheProperties.Bloom config = properties.getBloom();
        if (!status.ready()
                || status.expectedInsertions() >= config.getMaxExpectedInsertions()
                || status.expectedFalsePositiveProbability() < config.getFalsePositiveProbability()
                || lastExpansionTime.plus(config.getExpansionCooldown()).isAfter(Instant.now())) {
            return;
        }
        long oldCapacity = status.expectedInsertions();
        if (cacheService.expandAndRebuildBloomFilter()) {
            lastExpansionTime = Instant.now();
            log.warn("事件元数据布隆过滤器已扩容，容量 {} -> {}",
                    oldCapacity, bloomFilterManager.status().expectedInsertions());
        }
    }
}
