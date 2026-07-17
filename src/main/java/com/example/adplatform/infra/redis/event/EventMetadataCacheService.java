package com.example.adplatform.infra.redis.event;

import com.example.adplatform.tracking.service.EventMaterialMetadata;

public interface EventMetadataCacheService {

    EventMaterialMetadata get(Long materialId);

    void refreshAfterCommit(Long materialId, EventMaterialMetadata metadata);

    void evictPlanAfterCommit(Long planId);

    boolean rebuildBloomFilter();

    boolean expandAndRebuildBloomFilter();
}
