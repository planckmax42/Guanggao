package com.example.adplatform.infra.bloom.delivery.slot;

import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 统计广告位布隆过滤器的拦截结果。
 * 实际误判率 = 误判次数 / 最终确认不存在的请求数。
 */
@Component
public class SlotBloomFilterTracker {

    private final AtomicLong definiteMissCount = new AtomicLong();
    private final AtomicLong falsePositiveCount = new AtomicLong();

    public void recordDefiniteMiss() {
        definiteMissCount.incrementAndGet();
    }

    public void recordFalsePositive() {
        falsePositiveCount.incrementAndGet();
    }

    /**
     * 返回当前统计窗口的近似快照。
     * 两个计数独立读取，不保证严格的原子一致性。
     * 根据大量样本估算决定是否扩容，接受弱一致性方案todo：优化标记
     */
    public Snapshot snapshot() {
        return new Snapshot(
                definiteMissCount.get(),
                falsePositiveCount.get()
        );
    }

    /** 布隆过滤器重建成功后重置统计窗口。 */
    public void reset() {
        definiteMissCount.set(0);
        falsePositiveCount.set(0);
    }

    public record Snapshot(
            long definiteMissCount,
            long falsePositiveCount
    ) {

        public long confirmedAbsentCount() {
            return definiteMissCount + falsePositiveCount;
        }

        public double actualFalsePositiveRate() {
            long total = confirmedAbsentCount();
            return total == 0
                    ? 0.0
                    : (double) falsePositiveCount / total;
        }
    }
}