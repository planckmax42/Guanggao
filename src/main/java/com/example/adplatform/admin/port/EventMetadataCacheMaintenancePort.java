package com.example.adplatform.admin.port;

import com.example.adplatform.tracking.service.EventMaterialMetadata;

/** 事件元数据缓存维护端口，由基础设施层提供具体实现。 */
public interface EventMetadataCacheMaintenancePort {

    void refreshAfterCommit(Long materialId, EventMaterialMetadata metadata);

    void evictPlanAfterCommit(Long planId);
}
