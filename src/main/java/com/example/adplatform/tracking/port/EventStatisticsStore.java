package com.example.adplatform.tracking.port;

import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.tracking.service.EventMaterialMetadata;

/** Redis 中事件实时统计的幂等写入端口。 */
public interface EventStatisticsStore {

    boolean recordEventOnce(
            EventMessage message,
            EventMaterialMetadata metadata);

    boolean recordCostOnce(
            EventMessage message,
            EventMaterialMetadata metadata,
            long costAmount);
}
