package com.example.adplatform.infra.warmup;

import com.example.adplatform.infra.bloomfilter.tracking.materialMetadata.MaterialMetadataBloomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 启动时重建事件元数据的本地布隆过滤器。 */
@Component
@RequiredArgsConstructor
public class EventMetadataWarmUpTask {

    private final MaterialMetadataBloomService bloomFilterService;

    public void warmUp() {
        bloomFilterService.regularRebuild();
    }
}
