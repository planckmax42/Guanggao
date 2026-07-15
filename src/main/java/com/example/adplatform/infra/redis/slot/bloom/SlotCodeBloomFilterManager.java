package com.example.adplatform.infra.redis.slot.bloom;

import com.example.adplatform.infra.redis.slot.SlotCacheProperties;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.Getter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 使用双缓冲管理广告位编码布隆过滤器。
 *
 * <p>activeFilter 始终服务线上查询；rebuildingFilter 在后台根据 MySQL 全量数据构建。
 * 重建期间新增广告位会同时写入两份过滤器，构建成功后再原子替换 activeFilter。</p>
 */
@Component
public class SlotCodeBloomFilterManager {

    private final SlotCacheProperties properties;
    private final AtomicReference<BloomFilter<CharSequence>> activeFilter;
    private final AtomicReference<BloomFilter<CharSequence>> rebuildingFilter = new AtomicReference<>();
    private final AtomicLong currentExpectedInsertions;
    private final ReentrantLock rebuildLock = new ReentrantLock();
    private final ReentrantLock filterSwitchLock = new ReentrantLock();

    @Getter
    private volatile boolean ready;

    public SlotCodeBloomFilterManager(SlotCacheProperties properties) {
        this.properties = properties;
        this.currentExpectedInsertions = new AtomicLong(initialExpectedInsertions());
        this.activeFilter = new AtomicReference<>(createBloomFilter(currentExpectedInsertions.get()));
    }

    /**
     * 返回 true 表示编码一定不存在；返回 false 表示编码可能存在，需要继续查询缓存。
     */
    public boolean definitelyNotContains(String slotCode) {
        return ready && StringUtils.hasText(slotCode) && !activeFilter.get().mightContain(slotCode);//同样防御性校验
    }

    /**
     * 新增或重新启用广告位时，同时写入当前过滤器和正在构建的过滤器。
     */
    public void put(String slotCode) {
        if (!StringUtils.hasText(slotCode)) {
            return;
        }
        filterSwitchLock.lock();
        try {
            activeFilter.get().put(slotCode);
            BloomFilter<CharSequence> standbyFilter = rebuildingFilter.get();
            if (standbyFilter != null) {
                standbyFilter.put(slotCode);
            }
        } finally {
            filterSwitchLock.unlock();
        }
    }

    /**
     * 从权威数据源加载全部启用广告位并重建过滤器。重建失败时继续保留旧过滤器。
     *
     * @return 本次从数据源加载的数据；已有重建任务运行时返回 empty
     */
    public <T> Optional<T> rebuild(
            Supplier<T> dataLoader,
            Function<T, ? extends Collection<String>> slotCodeExtractor) {
        return rebuildWithCapacity(currentExpectedInsertions.get(), dataLoader, slotCodeExtractor);
    }

    /**
     * 按配置的扩容倍数创建更大的过滤器，并从权威数据源重新加载数据。
     */
    public <T> Optional<T> expandAndRebuild(
            Supplier<T> dataLoader,
            Function<T, ? extends Collection<String>> slotCodeExtractor) {
        long currentCapacity = currentExpectedInsertions.get();
        SlotCacheProperties.Bloom bloom = properties.getBloom();
        if (bloom.getExpansionFactor() <= 1D) {
            throw new IllegalArgumentException("布隆过滤器扩容倍数必须大于 1");
        }
        long maxCapacity = Math.max(currentCapacity, bloom.getMaxExpectedInsertions());
        long expandedCapacity = Math.min(
                maxCapacity,
                Math.max(currentCapacity + 1L, (long) Math.ceil(currentCapacity * bloom.getExpansionFactor())));
        if (expandedCapacity <= currentCapacity) {
            return Optional.empty();
        }
        return rebuildWithCapacity(expandedCapacity, dataLoader, slotCodeExtractor);
    }

    public Status status() {
        BloomFilter<CharSequence> filter = activeFilter.get();
        return new Status(
                currentExpectedInsertions.get(),
                approximateElementCount(filter),
                filter.expectedFpp(),
                ready);
    }

    private <T> Optional<T> rebuildWithCapacity(
            long expectedInsertions,
            Supplier<T> dataLoader,
            Function<T, ? extends Collection<String>> slotCodeExtractor) {
        if (!rebuildLock.tryLock()) {
            return Optional.empty();
        }

        BloomFilter<CharSequence> standbyFilter = createBloomFilter(expectedInsertions);
        filterSwitchLock.lock();
        try {
            rebuildingFilter.set(standbyFilter);
        } finally {
            filterSwitchLock.unlock();
        }
        try {
            T sourceData = dataLoader.get();
            Collection<String> slotCodes = slotCodeExtractor.apply(sourceData);
            if (slotCodes != null) {
                slotCodes.stream()
                        .filter(StringUtils::hasText)
                        .forEach(standbyFilter::put);
            }
            filterSwitchLock.lock();
            try {
                activeFilter.set(standbyFilter);
                currentExpectedInsertions.set(expectedInsertions);
                rebuildingFilter.compareAndSet(standbyFilter, null);
                ready = true;
            } finally {
                filterSwitchLock.unlock();
            }
            return Optional.ofNullable(sourceData);
        } finally {
            filterSwitchLock.lock();
            try {
                rebuildingFilter.compareAndSet(standbyFilter, null);
            } finally {
                filterSwitchLock.unlock();
            }
            rebuildLock.unlock();
        }
    }

    private long initialExpectedInsertions() {
        return Math.max(1L, properties.getBloom().getExpectedInsertions());
    }

    private BloomFilter<CharSequence> createBloomFilter(long expectedInsertions) {
        SlotCacheProperties.Bloom bloom = properties.getBloom();
        double falsePositiveProbability = bloom.getFalsePositiveProbability();
        if (falsePositiveProbability <= 0D || falsePositiveProbability >= 1D) {
            throw new IllegalArgumentException("布隆过滤器误判率必须大于 0 且小于 1");
        }
        return BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                expectedInsertions,
                falsePositiveProbability);
    }

    private long approximateElementCount(BloomFilter<CharSequence> filter) {
        try {
            return filter.approximateElementCount();
        } catch (ArithmeticException ex) {
            // 位图完全饱和时数学估算结果为无穷大，用最大值表示容量已严重不足。
            return Long.MAX_VALUE;
        }
    }

    public record Status(
            long expectedInsertions,
            long approximateElementCount,
            double expectedFalsePositiveProbability,
            boolean ready) {
    }
}
