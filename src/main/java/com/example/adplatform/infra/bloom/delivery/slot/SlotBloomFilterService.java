package com.example.adplatform.infra.bloom.delivery.slot;

import java.util.List;
import java.util.Optional;

/** 广告位布隆过滤器的统一访问与维护接口。 */
public interface SlotBloomFilterService {

    /**
     * 判断广告位编码是否一定不存在；过滤器未就绪时不拦截请求。
     *
     * @param slotCode 广告位编码
     * @return 一定不存在时返回 {@code true}
     */
    boolean definitelyNotContains(String slotCode);

    /**
     * 将广告位编码写入布隆过滤器。
     *
     * @param slotCode 广告位编码
     */
    void addSlotBloomFilter(String slotCode);

    /**
     * 查询全部启用广告位并重建过滤器，同时返回本次数据快照。
     *
     * @return 重建快照；正在重建或发生异常时返回 empty
     */
    Optional<List<String>> regularRebuild();

    /**
     * 扩容并全量重建过滤器。
     *
     * @return 扩容重建成功时返回 {@code true}
     */
    boolean expandRebuild();

    /**
     * 获取过滤器运行状态。
     *
     * @return 状态快照
     */
    BloomFilterSnapshot GetBloomFilterSnapshot();

    /** 布隆过滤器运行状态快照。 */
    record BloomFilterSnapshot(
            long currentCapacity,
            long approximateElementCount,
            double expectedFpp,
            boolean bloomFilterReady) {
    }
}
