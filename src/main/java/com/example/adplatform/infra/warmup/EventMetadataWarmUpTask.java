package com.example.adplatform.infra.warmup;

import com.example.adplatform.infra.redis.tracking.metadata.EventMetadataCacheServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 启动时重建事件元数据的本地布隆过滤器。 */
@Component
@RequiredArgsConstructor
public class EventMetadataWarmUpTask {

    private final EventMetadataCacheServiceImpl cacheService;

    public void warmUp() {
        cacheService.rebuildBloomFilter();
    }
}
