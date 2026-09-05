package com.example.adplatform.delivery.port;

/**
 * 广告位编码缓存门面，协调布隆过滤器、Redis 缓存和 MySQL 权威数据。
 */
public interface SlotCacheDeliveryPort {

    /**
     * 根据广告位编码获取启用广告位 ID。先用布隆过滤器拦截明显不存在的编码，再读 Redis，未命中或 Redis 异常时回源 MySQL。
     *
     * @param slotCode 对外广告位编码
     * @return 显式区分启用、不存在与缓存不可用的查询结果
     */
    SlotIdResult getEnabledIdByCode(String slotCode);

    String getSlotIdFromCache(String slotCode);
}
