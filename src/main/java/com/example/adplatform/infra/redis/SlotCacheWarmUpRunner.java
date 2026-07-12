package com.example.adplatform.infra.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class SlotCacheWarmUpRunner implements ApplicationRunner {

    private final SlotCacheService slotCacheService;

    @Override
    public void run(ApplicationArguments args) {
        slotCacheService.warmUp();
    }
}
