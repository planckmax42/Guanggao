package com.example.adplatform.infra.bloomfilter.tracking.materialMetadata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BloomRebuildScheduler {

    private final MaterialMetadataBloomService materialMetadataBloomService;
    private final BloomProperties bloomProperties;

    @Scheduled(
            fixedDelayString = "${app.event-metadata-cache.bloom.rebuild-delay-ms}",
            initialDelayString = "${app.event-metadata-cache.bloom.rebuild-initial-delay-ms}")
    public void rebuild() {
        try{
            BloomRebuildResult bloomRebuildResult = materialMetadataBloomService.regularRebuild();
            if (bloomRebuildResult.rebuildStatus()== BloomRebuildResult.RebuildStatus.LOCK_BUSY){
                log.warn("素材元数据布隆过滤器定时重建失败，重建锁被占用,将在下个周期重试");
            }
        }catch (RuntimeException exception){
            log.error("素材元数据布隆过滤器定期重建失败，异常类型：{},将在下个周期重试", exception.getClass());
        }
    }//todo:由简单的定时策略改为按需更新

    @Scheduled(
            fixedDelayString = "${app.event-metadata-cache.bloom.expansion-check-delay-ms}",
            initialDelayString = "${app.event-metadata-cache.bloom.expansion-check-initial-delay-ms}")
    public void expandWhenNeeded() {//todo:扩容冷静期是否需要
        BloomSnapshot bloomSnapshot = materialMetadataBloomService.GetBloomFilterSnapshot();
        double targetRate = bloomProperties.getFalsePositiveProbability();
        if (!bloomSnapshot.bloomFilterReady()
                || bloomSnapshot.actualFalsePositiveRate() < targetRate
                || bloomSnapshot.estimatedFalsePositiveRate() < targetRate) {
            return;
        }
        try{
            BloomRebuildResult bloomRebuildResult = materialMetadataBloomService.expandRebuild();
            if (bloomRebuildResult.rebuildStatus()== BloomRebuildResult.RebuildStatus.LOCK_BUSY) {
                log.warn("素材元数据布隆过滤器扩容重建失败，重建锁被占用,将在下个周期重试");
            }
        }catch (RuntimeException exception){
            log.error("素材元数据布隆过滤器扩容重建失败，异常类型：{}", exception.getClass());
        }
    }
}
