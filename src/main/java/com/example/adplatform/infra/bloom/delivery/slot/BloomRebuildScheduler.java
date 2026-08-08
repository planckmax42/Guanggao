package com.example.adplatform.infra.bloom.delivery.slot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.example.adplatform.infra.bloom.delivery.slot.BloomRebuildResult.RebuildStatus;

/**
 * 定期维护广告位布隆过滤器，并在误判率达到阈值时扩容重建。
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class BloomRebuildScheduler {

    private final SlotBloomService slotBloomService;
    private final BloomProperties bloomProperties;
    /**
     * 按固定间隔全量重建布隆过滤器，清除无法单独删除的旧编码。
     *///todo:后续处理这个配置前缀过长问题
    @Scheduled(fixedDelayString = "${app.slot-cache.bloom.rebuild-delay}",
            initialDelayString = "${app.slot-cache.bloom.rebuild-initial-delay}")
    public void regularRebuild() {
        try {
            BloomRebuildResult rebuildResult = slotBloomService.regularRebuild();
            if(rebuildResult.rebuildStatus() == RebuildStatus.LOCK_BUSY){
                log.warn("广告位布隆过滤器定时重建失败,重建锁被占用，将在下个周期重试");
            }
        }catch (RuntimeException exception){
            log.error("广告位布隆过滤器定期重建失败，异常类型：{},将在下个周期重试", exception.getClass());
        }
    }

    /**
     * 按配置周期检查实际误判率和位图理论误判率，达到阈值后触发扩容重建。
     *样本数不足、仍在冷却期、过滤器未就绪或已达容量上限时不执行扩容。
     */
    @Scheduled(fixedDelayString = "${app.slot-cache.bloom.expansion-check-delay}",
            initialDelayString = "${app.slot-cache.bloom.expansion-check-initial-delay}")
    public void expandRebuildIfNeed() {
        BloomSnapshot bloomSnapshot = slotBloomService.getBloomFilterSnapshot();
        double targetRate = bloomProperties.getFalsePositiveProbability();
        if (bloomSnapshot.actualFalsePositiveRate() < targetRate || bloomSnapshot.estimatedFalsePositiveRate() < targetRate) {
            return;
        }
        try {
            BloomRebuildResult rebuildResult = slotBloomService.expandRebuild();
            if(rebuildResult.rebuildStatus() == RebuildStatus.LOCK_BUSY){
                log.warn("重建锁被占用，广告位布隆过滤器扩容重建失败");
            }
        }catch (RuntimeException exception){
            log.error("广告位布隆过滤器扩容重建失败，异常类型：{}", exception.getClass());
        }//todo:这里是否需要日志 还是说应该只输出错误日志 由Grafana负责
    }
}
