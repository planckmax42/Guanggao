package com.example.adplatform.infra.bloom.delivery.slot;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 统计广告位布隆过滤器对不存在编码的拦截效果。
 */
@Component
public class SlotBloomFilterMetrics {

    /** 布隆过滤器明确拦截的不存在编码数量。 */
    private final AtomicLong definiteMissCount = new AtomicLong();

    /** 布隆过滤器误判为可能存在、但 MySQL 确认不存在的编码数量。 */
    private final AtomicLong falsePositiveCount = new AtomicLong();

    /**
     * 布隆过滤器明确判断编码不存在，没有继续访问 Redis 和 MySQL。
     */
    public void recordDefiniteMiss() {
        definiteMissCount.incrementAndGet();
    }

    /**
     * 布隆过滤器判断可能存在，但 Redis 未命中且 MySQL 最终确认不存在。
     */
    public void recordFalsePositive() {
        falsePositiveCount.incrementAndGet();
    }

    /**
     * 获取当前统计窗口的一致性要求较弱的计数快照。
     *
     * @return 明确未命中数和误判数快照
     */
    public Snapshot snapshot() {
        return new Snapshot(definiteMissCount.get(), falsePositiveCount.get());
    }

    /**
     * 过滤器成功重建后开启新的统计窗口。
     */
    public void reset() {
        definiteMissCount.set(0L);
        falsePositiveCount.set(0L);
    }

    /**
     * 布隆过滤器对“最终确认不存在”请求的统计快照。
     *
     * @param definiteMissCount 被布隆过滤器直接拦截的请求数
     * @param falsePositiveCount 布隆过滤器误判为可能存在的请求数
     */
    public record Snapshot(long definiteMissCount, long falsePositiveCount) {

        /**
         * @return 明确未命中数和误判数之和
         */
        public long absentSampleCount() {
            return definiteMissCount + falsePositiveCount;
        }

        /**
         * @return 当前窗口的实际误判率；无样本时返回 {@code 0}
         */
        public double actualFalsePositiveRate() {
            long samples = absentSampleCount();
            return samples == 0L ? 0D : (double) falsePositiveCount / samples;
        }
    }
}
