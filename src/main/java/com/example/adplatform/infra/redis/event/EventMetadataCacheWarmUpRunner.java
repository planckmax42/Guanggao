package com.example.adplatform.infra.redis.event;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 启动时只重建有界的本地布隆过滤器，Redis 元数据按需加载。 */
@Component
@RequiredArgsConstructor
public class EventMetadataCacheWarmUpRunner implements ApplicationRunner {

    private final EventMetadataCacheService cacheService;

    @Override
    public void run(ApplicationArguments args) {
        cacheService.rebuildBloomFilter();
    }//todo:后续换一下分包的位置
}
