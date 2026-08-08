package com.example.adplatform.infra.bloom.delivery.slot;

/** 广告位布隆过滤器的统一访问与维护接口。 */
public interface SlotBloomService {

    /**
     * 判断广告位编码是否一定不存在；过滤器未就绪时不拦截请求。
     *
     * @param slotCode 广告位编码
     * @return 一定不存在时返回 {@code true}
     */
    boolean definiteNotContain(String slotCode);

    /**
     * 将广告位编码写入布隆过滤器。
     *
     * @param slotCode 广告位编码
     */
    void addSlotBloomFilter(String slotCode);
    /**
     * 获取过滤器运行状态。
     *
     * @return 状态快照
     */
    BloomSnapshot getBloomFilterSnapshot();

    void recordDefiniteNotContain();

    void recordFalsePositive();

    BloomRebuildResult regularRebuild();

    /**
     * 扩容并全量重建过滤器。
     *
     * @return 扩容重建成功时返回 {@code true}
     */
    BloomRebuildResult expandRebuild();

}
