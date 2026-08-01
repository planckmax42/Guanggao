package com.example.adplatform.infra.redis.tracking;

/** Redis 事件追踪链路 Key 生成工具。 */
public final class TrackingRedisKeys {

    /** 工具类不允许实例化。 */
    private TrackingRedisKeys() {
    }

    /**
     * 标记实时事件计数已经处理，避免 Consumer 重试重复累计。
     */
    public static String eventStatisticsProcessed(String eventId) {
        return "stats:event:%s".formatted(eventId);
    }

    /**
     * 标记事件计费金额已经写入实时统计。
     */
    public static String eventCostStatisticsProcessed(String eventId) {
        return "stats:cost:event:%s".formatted(eventId);
    }

    /** 生成事件消费者共享的素材元数据缓存 Key。 */
    public static String eventMaterialMetadata(Long materialId) {
        return "event:metadata:material:%d".formatted(materialId);
    }
}
