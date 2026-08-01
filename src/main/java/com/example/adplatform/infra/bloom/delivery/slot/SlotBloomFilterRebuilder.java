package com.example.adplatform.infra.bloom.delivery.slot;

/** 广告位布隆过滤器维护接口。 */
public interface SlotBloomFilterRebuilder {

    /**
     * 从 MySQL 全量加载启用广告位并重建布隆过滤器，清理已失效编码。
     *
     * @return 重建成功时返回 {@code true}；已有任务执行或加载失败时返回 {@code false}
     */
    boolean rebuild();

    /**
     * 扩大布隆过滤器容量并从 MySQL 重建；达到容量上限或已有重建任务时返回 false。
     *
     * @return 扩容重建成功时返回 {@code true}
     */
    boolean expandAndRebuild();
}
