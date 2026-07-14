package com.example.adplatform.infra.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定期从 MySQL 重建广告位布隆过滤器，清理已停用、删除或改名编码留下的旧位图。
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class SlotBloomFilterRebuildScheduler {

    private final SlotCacheService slotCacheService;

    @Scheduled(
            fixedDelayString = "${app.slot-cache.bloom.rebuild-delay-ms:300000}",
            initialDelayString = "${app.slot-cache.bloom.rebuild-initial-delay-ms:300000}")
    public void rebuild() {
        try {
            slotCacheService.rebuildBloomFilter();
        } catch (RuntimeException ex) {
            log.warn("广告位布隆过滤器定时重建失败，将保留旧过滤器并在下次任务重试", ex);
        }
    }
}
