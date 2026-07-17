package com.example.adplatform.tracking.service;

/** Redis 中事件实时统计的幂等写入端口。 */
public interface EventStatisticsStore {

    boolean recordEventOnce(
            String eventId,
            Long viewerId,
            EventProcessingContext context);

    boolean recordCostOnce(
            String eventId,
            EventProcessingContext context,
            long costAmount);
}
