package com.example.adplatform.infra.redis.delivery.slot;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** 对 Redis 故障堆栈按操作类型限频，被抑制日志改用指标统计。 */
@Component
@RequiredArgsConstructor
public class SlotCacheFailureLogLimiter {

    private final SlotCacheProperties properties;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, AtomicLong> lastLoggedAt = new ConcurrentHashMap<>();

    public boolean shouldLog(String operation) {
        long now = System.nanoTime();
        long interval = properties.getFailureLogInterval().toNanos();
        AtomicLong last = lastLoggedAt.computeIfAbsent(operation, ignored -> new AtomicLong(Long.MIN_VALUE));
        while (true) {
            long previous = last.get();
            if (previous != Long.MIN_VALUE && now - previous < interval) {
                meterRegistry.counter("ad.slot.cache.log.suppressed", "operation", operation).increment();
                return false;
            }
            if (last.compareAndSet(previous, now)) {
                return true;
            }
        }
    }
}
