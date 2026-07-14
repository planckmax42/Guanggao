package com.example.adplatform.infra.redis;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 统计广告位布隆过滤器对不存在编码的拦截效果。
 */
@Component
public class SlotBloomFilterMetrics {

    private final AtomicLong rejectedAbsentCount = new AtomicLong();
    private final AtomicLong falsePositiveCount = new AtomicLong();

    /**
     * 布隆过滤器明确判断编码不存在，没有继续访问 Redis 和 MySQL。
     */
    public void recordRejectedAbsent() {
        rejectedAbsentCount.incrementAndGet();
    }

    /**
     * 布隆过滤器判断可能存在，但 Redis 未命中且 MySQL 最终确认不存在。
     */
    public void recordFalsePositive() {
        falsePositiveCount.incrementAndGet();
    }

    public Snapshot snapshot() {
        return new Snapshot(rejectedAbsentCount.get(), falsePositiveCount.get());
    }

    /**
     * 过滤器成功重建后开启新的统计窗口。
     */
    public void reset() {
        rejectedAbsentCount.set(0L);
        falsePositiveCount.set(0L);
    }

    public record Snapshot(long rejectedAbsentCount, long falsePositiveCount) {

        public long absentSampleCount() {
            return rejectedAbsentCount + falsePositiveCount;
        }

        public double actualFalsePositiveRate() {
            long samples = absentSampleCount();
            return samples == 0L ? 0D : (double) falsePositiveCount / samples;
        }
    }
}
