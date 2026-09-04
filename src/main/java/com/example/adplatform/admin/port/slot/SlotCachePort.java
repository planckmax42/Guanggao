package com.example.adplatform.admin.port.slot;


/** 广告位缓存维护端口，由基础设施层提供具体实现。 */
public interface SlotCachePort {



    void writeSlotToRedis(String slotCode,Long slotId);

    void evictSlotCodeFromRedis(String slotCode);

    /**
     * 根据广告位编码重新查询权威数据并刷新 Redis 缓存。
     *
     * @param slotCode 待刷新的广告位编码
     */
    void refreshSlotByCode(String slotCode);
}
