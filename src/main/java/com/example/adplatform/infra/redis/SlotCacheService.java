package com.example.adplatform.infra.redis;

import com.example.adplatform.admin.entity.SlotEntity;

import java.util.Optional;

public interface SlotCacheService {

    /**
     * 根据广告位编码获取启用广告位 ID。先用布隆过滤器拦截明显不存在的编码，再读 Redis，未命中或 Redis 异常时回源 MySQL。
     */
    Optional<Long> getEnabledSlotIdByCode(String slotCode);

    /**
     * 将启用广告位写入 Redis；如果广告位已停用，则删除对应缓存。
     */
    void cacheSlot(SlotEntity slot);

    /**
     * 广告位编码发生变化时，删除旧编码缓存，并刷新新编码缓存。
     */
    void refreshSlot(SlotEntity slot, String oldSlotCode);

    /**
     * 启动时预热启用广告位缓存和广告位编码布隆过滤器。
     */
    void warmUp();

    /**
     * 从 MySQL 全量加载启用广告位并重建布隆过滤器，清理已失效编码。
     */
    void rebuildBloomFilter();
}
