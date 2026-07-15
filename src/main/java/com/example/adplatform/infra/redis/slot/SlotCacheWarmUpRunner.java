package com.example.adplatform.infra.redis.slot;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Spring Boot 启动完成后触发广告位 Redis 缓存和布隆过滤器预热。 */
@RequiredArgsConstructor
@Component
public class SlotCacheWarmUpRunner implements ApplicationRunner {

    private final SlotCacheService slotCacheService;

    /**
     * 执行广告位缓存预热。
     *
     * @param args 应用启动参数，本任务不使用
     */
    @Override
    public void run(ApplicationArguments args) {
        slotCacheService.warmUp();
    }
}
