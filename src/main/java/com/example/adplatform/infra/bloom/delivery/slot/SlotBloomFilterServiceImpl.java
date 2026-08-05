package com.example.adplatform.infra.bloom.delivery.slot;

import com.example.adplatform.admin.mapper.SlotMapper;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 广告位布隆过滤器实现。
 */
@Slf4j
@Service
@SuppressWarnings("UnstableApiUsage")
public class SlotBloomFilterServiceImpl implements SlotBloomFilterService {

    private final SlotMapper slotMapper;
    private final SlotBloomFilterProperties slotBloomFilterProperties;
    private final SlotBloomFilterTracker slotBloomFilterTracker;
    private final AtomicReference<BloomFilter<CharSequence>> currentBloomFilter;//todo:我感觉这里一个violate保证可见性就可以了 毕竟已经有锁保证只有一个去更新了 或者像普通变量一样对待 毕竟平时也没考虑过可见性
    private BloomFilter<CharSequence> standbyBloomFilter;
    private long currentCapacity;
    private final AtomicInteger capacityExhausted = new AtomicInteger(0);
    private final ReentrantLock rebuildLock = new ReentrantLock();
    private final Object updateLock = new Object();
    private volatile boolean bloomFilterReady;

    SlotBloomFilterServiceImpl(
            SlotMapper slotMapper,
            SlotBloomFilterProperties slotBloomFilterProperties,
            SlotBloomFilterTracker slotBloomFilterTracker,
            MeterRegistry meterRegistry) {
        this.slotMapper = slotMapper;
        this.slotBloomFilterProperties = slotBloomFilterProperties;
        this.slotBloomFilterTracker = slotBloomFilterTracker;
        long initialCapacity = slotBloomFilterProperties.getInitialCapacity();
        this.currentBloomFilter = new AtomicReference<>(createBloomFilter(initialCapacity));
        this.currentCapacity = initialCapacity;
        capacityExhausted.set(0);
        Gauge.builder("slot.bloom.capacity.exhausted", capacityExhausted, AtomicInteger::get)
                .description("广告位布隆过滤器是否已达最大容量")
                .register(meterRegistry);
    }

    @Override
    public boolean definitelyNotContains(String slotCode) {
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
    public BloomFilterSnapshot GetBloomFilterSnapshot() {
        BloomFilter<CharSequence> currentBloomFilter = this.currentBloomFilter.get();
        return new BloomFilterSnapshot(
                currentCapacity,
                approximateElementCount(currentBloomFilter),
                currentBloomFilter.expectedFpp(),
                bloomFilterReady);
    }

    @Override
    public Optional<List<String>> regularRebuild() {
        return rebuildExecutor(RebuildMode.REGULAR_REBUILD);
    }

    @Override
    public boolean expandRebuild() {
        return rebuildExecutor(RebuildMode.EXPAND_REBUILD).isPresent();
    }

    private Optional<List<String>> rebuildExecutor(RebuildMode rebuildMode) {
        if (!rebuildLock.tryLock()) {
            return Optional.empty();
        }
        try {
            List<String> enabledSlotCodes;
            try {
                long targetCapacity;
                synchronized (updateLock) {
                    targetCapacity = calculateTargetCapacity(rebuildMode, currentCapacity);
                    if (rebuildMode == RebuildMode.EXPAND_REBUILD && targetCapacity <= currentCapacity) {
                        return Optional.empty();
                    }
                    standbyBloomFilter = createBloomFilter(targetCapacity);
                }
                try {
                    enabledSlotCodes = slotMapper.selectEnabledSlotCodes();
                    enabledSlotCodes.stream()
                            .filter(StringUtils::hasText)
                            .forEach(standbyBloomFilter::put);
                    publishStandbyFilter(targetCapacity);
                } finally {
                    synchronized (updateLock) {
                        standbyBloomFilter = null;//保险操作，防止异常中断未发布
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("广告位布隆过滤器{}失败，保留当前过滤器", rebuildMode, ex);
                return Optional.empty();
            }
            slotBloomFilterTracker.reset();
            log.info("广告位布隆过滤器{}完成，启用广告位数量={}", rebuildMode, enabledSlotCodes.size());
            return Optional.of(enabledSlotCodes);
        } finally {
            rebuildLock.unlock();
        }
    }

    private long calculateTargetCapacity(RebuildMode mode, long currentCapacity) {
        return switch (mode) {
            case REGULAR_REBUILD -> currentCapacity;
            case EXPAND_REBUILD -> Math.min(
                    slotBloomFilterProperties.getMaxExpectedCapacity(),
                    (long) Math.ceil(currentCapacity * slotBloomFilterProperties.getExpansionFactor()));
        };
    }

    private void publishStandbyFilter(long targetCapacity) {
        synchronized (updateLock) {//隔离发布操作和addSlotBloomFilter操作
            currentBloomFilter.set(standbyBloomFilter);
            currentCapacity = targetCapacity;
            if(currentCapacity == slotBloomFilterProperties.getMaxExpectedCapacity()) capacityExhausted.set(1);
            standbyBloomFilter = null;
            bloomFilterReady = true;
        }
    }

    private BloomFilter<CharSequence> createBloomFilter(long expectedCapacity) {
        return BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                expectedCapacity,
                slotBloomFilterProperties.getFalsePositiveProbability());
    }

    private long approximateElementCount(BloomFilter<CharSequence> bloomFilter) {
        try {
            return bloomFilter.approximateElementCount();
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }

    private enum RebuildMode {
        REGULAR_REBUILD,
        EXPAND_REBUILD;
    }
}
