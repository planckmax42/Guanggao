package com.example.adplatform.admin.port;

import com.example.adplatform.admin.entity.SlotEntity;

/** 广告位缓存维护端口，由基础设施层提供具体实现。 */
public interface SlotCacheMaintenancePort {

    /**
     * 启用编码立即加入布隆过滤器；数据库事务提交后再删除旧 Redis 缓存并刷新当前缓存。
     * 事务回滚时可能留下安全的布隆假阳性，由后续全量重建清理。
     *
     * @param slot 更新后的广告位
     * @param oldSlotCode 更新前的广告位编码
     */
    void refreshSlot(SlotEntity slot, String oldSlotCode);

    /**
     * 根据广告位编码重新查询权威数据并刷新 Redis 缓存。
     *
     * @param slotCode 待刷新的广告位编码
     */
    void refreshSlotByCode(String slotCode);
}
