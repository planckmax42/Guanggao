package com.example.adplatform.infra.redis.slot;

import com.example.adplatform.admin.entity.SlotEntity;

import java.util.Optional;

/**
 * 广告位编码缓存门面，协调布隆过滤器、Redis 缓存和 MySQL 权威数据。
 */
public interface SlotCacheService {

    /**
     * 根据广告位编码获取启用广告位 ID。先用布隆过滤器拦截明显不存在的编码，再读 Redis，未命中或 Redis 异常时回源 MySQL。
     *
     * @param slotCode 对外广告位编码
     * @return 启用广告位 ID；编码无效或不存在时返回 empty
     */
    Optional<Long> getEnabledSlotIdByCode(String slotCode);

    /**
     * 将启用广告位写入 Redis；如果广告位已停用，则删除对应缓存。
     *
     * @param slot 待同步的广告位，为 {@code null} 时忽略
     */
    void cacheSlot(SlotEntity slot);

    /**
     * 启用编码立即加入布隆过滤器；数据库事务提交后再删除旧 Redis 缓存并刷新当前缓存。
     * 事务回滚时可能留下安全的布隆假阳性，由后续全量重建清理。
     *
     * @param slot 更新后的广告位
     * @param oldSlotCode 更新前的广告位编码
     */
    void refreshSlot(SlotEntity slot, String oldSlotCode);

    /**
     * 启动时预热启用广告位缓存和广告位编码布隆过滤器。
     */
    void warmUp();

    /**
     * 从 MySQL 全量加载启用广告位并重建布隆过滤器，清理已失效编码。
     *
     * @return 重建成功时返回 {@code true}；已有任务执行或加载失败时返回 {@code false}
     */
    boolean rebuildBloomFilter();

    /**
     * 扩大布隆过滤器容量并从 MySQL 重建；达到容量上限或已有重建任务时返回 false。
     *
     * @return 扩容重建成功时返回 {@code true}
     */
    boolean expandAndRebuildBloomFilter();
}
