package com.example.adplatform.tracking.service;

import com.example.adplatform.tracking.message.EventMessage;

/** Redis 中事件实时统计的幂等写入端口。 */
public interface EventStatisticsStore {

    boolean recordEventOnce(
            EventMessage message,
            EventProcessingContext context);

    boolean recordCostOnce(
            String eventId,
            EventProcessingContext context,
            long costAmount);
}
