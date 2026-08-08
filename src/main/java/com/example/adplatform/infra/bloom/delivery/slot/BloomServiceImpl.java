package com.example.adplatform.infra.bloom.delivery.slot;

import com.example.adplatform.admin.mapper.SlotMapper;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.example.adplatform.infra.bloom.delivery.slot.BloomRebuildResult.RebuildStatus;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 广告位布隆过滤器实现。
 */
@Slf4j
@Service
@SuppressWarnings("UnstableApiUsage")
public class BloomServiceImpl implements SlotBloomService {

    private final SlotMapper slotMapper;
    private final BloomProperties bloomProperties;
    private final AtomicReference<BloomFilter<CharSequence>> currentBloomFilter;//todo:我感觉这里一个violate保证可见性就可以了 毕竟已经有锁保证只有一个去更新了 或者像普通变量一样对待 毕竟平时也没考虑过可见性
    private BloomFilter<CharSequence> standbyBloomFilter;
    private long currentCapacity;
    private final AtomicInteger capacityExhausted = new AtomicInteger(0);
    private final ReentrantLock rebuildLock = new ReentrantLock();
    private final Object updateLock = new Object();
    private volatile boolean bloomFilterReady;
    private final AtomicLong definiteNotContain = new AtomicLong();
    private final AtomicLong falsePositiveCount = new AtomicLong();

    BloomServiceImpl(
            SlotMapper slotMapper,
            BloomProperties bloomProperties,
            MeterRegistry meterRegistry) {
        this.slotMapper = slotMapper;
        this.bloomProperties = bloomProperties;
        long initialCapacity = bloomProperties.getInitialCapacity();
        this.currentBloomFilter = new AtomicReference<>(createBloomFilter(initialCapacity));
        this.currentCapacity = initialCapacity;
        capacityExhausted.set(0);
        Gauge.builder("slot.bloom.capacity.exhausted", capacityExhausted, AtomicInteger::get)
                .description("广告位布隆过滤器是否已达最大容量")
                .register(meterRegistry);
    }

    @Override
    public boolean definiteNotContain(String slotCode) {
        BloomFilter<CharSequence> currentBloomFilter = this.currentBloomFilter.get();
        return bloomFilterReady
                && StringUtils.hasText(slotCode)
                && !currentBloomFilter.mightContain(slotCode);
    }

    @Override
    public void addSlotBloomFilter(String slotCode) {
        if (!StringUtils.hasText(slotCode)) {
            return;
        }
        synchronized (updateLock) {
            currentBloomFilter.get().put(slotCode);
            if (standbyBloomFilter != null) {
                standbyBloomFilter.put(slotCode);
            }
        }
    }

    @Override
    public BloomSnapshot getBloomFilterSnapshot() {
        BloomFilter<CharSequence> currentBloomFilter = this.currentBloomFilter.get();
        double actualFalsePositiveRate = (double) falsePositiveCount.get() / (falsePositiveCount.get()+ definiteNotContain.get());
        long totalCount = definiteNotContain.get()+falsePositiveCount.get();
        return new BloomSnapshot(
                bloomFilterReady,
                currentCapacity,
                totalCount,
                currentBloomFilter.expectedFpp(),
                actualFalsePositiveRate);
    }

    @Override
    public void recordDefiniteNotContain() {
        definiteNotContain.incrementAndGet();
    }

    @Override
    public void recordFalsePositive() {
        falsePositiveCount.incrementAndGet();
    }

    @Override
    public BloomRebuildResult regularRebuild() {
        return rebuildExecutor(RebuildMode.REGULAR_REBUILD);
    }

    @Override
    public BloomRebuildResult expandRebuild() {
       return rebuildExecutor(RebuildMode.EXPAND_REBUILD);
    }
//todo：异常捕获+这个optional
    private BloomRebuildResult rebuildExecutor(RebuildMode rebuildMode) {
        if (!rebuildLock.tryLock()) {
            return new BloomRebuildResult(RebuildStatus.LOCK_BUSY,Optional.empty(),currentCapacity);
        }
        try {
            long targetCapacity = switch (rebuildMode) {
                case REGULAR_REBUILD -> currentCapacity;
                case EXPAND_REBUILD -> Math.min(
                        bloomProperties.getMaxCapacity(),
                        (long) Math.ceil(currentCapacity * bloomProperties.getExpansionFactor()));
            };
            synchronized (updateLock) {
                standbyBloomFilter = createBloomFilter(targetCapacity);
            }
            List<String> enabledSlotNames;
            try {
                enabledSlotNames = slotMapper.selectEnabledSlotCodes();
                enabledSlotNames.stream()
                        .filter(StringUtils::hasText)
                        .forEach(standbyBloomFilter::put);
                synchronized (updateLock) {//隔离发布操作和addSlotBloomFilter操作
                    if(currentCapacity == bloomProperties.getMaxCapacity()) capacityExhausted.set(1);
                    currentBloomFilter.set(standbyBloomFilter);
                    currentCapacity = targetCapacity;
                    standbyBloomFilter = null;
                    bloomFilterReady = true;
                }
            } finally {
                synchronized (updateLock) {
                    standbyBloomFilter = null;//保险操作，防止异常中断未发布
                }
            }
            resetCount();
            return new BloomRebuildResult(RebuildStatus.SUCCESS,Optional.of(enabledSlotNames),targetCapacity);
        } finally {
            rebuildLock.unlock();
        }
    }

    private BloomFilter<CharSequence> createBloomFilter(long expectedCapacity) {
        return BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                expectedCapacity,
                bloomProperties.getFalsePositiveProbability());
    }

    public void resetCount() {
        definiteNotContain.set(0);
        falsePositiveCount.set(0);
    }

    private enum RebuildMode {
        REGULAR_REBUILD,
        EXPAND_REBUILD;
    }
}
