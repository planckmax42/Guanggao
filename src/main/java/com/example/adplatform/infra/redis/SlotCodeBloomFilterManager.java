package com.example.adplatform.infra.redis;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;
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
    private final ReentrantLock rebuildLock = new ReentrantLock();
    private final ReentrantLock filterSwitchLock = new ReentrantLock();

    private volatile boolean ready;

    public SlotCodeBloomFilterManager(SlotCacheProperties properties) {
        this.properties = properties;
        this.activeFilter = new AtomicReference<>(createBloomFilter());
    }

    /**
     * 返回 true 表示编码一定不存在；返回 false 表示编码可能存在，需要继续查询缓存。
     */
    public boolean definitelyNotContains(String slotCode) {
        return ready && StringUtils.hasText(slotCode) && !activeFilter.get().mightContain(slotCode);
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
        if (!rebuildLock.tryLock()) {
            return Optional.empty();
        }

        BloomFilter<CharSequence> standbyFilter = createBloomFilter();
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

    public boolean isReady() {
        return ready;
    }

    private BloomFilter<CharSequence> createBloomFilter() {
        SlotCacheProperties.Bloom bloom = properties.getBloom();
        int expectedInsertions = Math.max(1, bloom.getExpectedInsertions());
        double falsePositiveProbability = bloom.getFalsePositiveProbability();
        if (falsePositiveProbability <= 0D || falsePositiveProbability >= 1D) {
            throw new IllegalArgumentException("布隆过滤器误判率必须大于 0 且小于 1");
        }
        return BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                expectedInsertions,
                falsePositiveProbability);
    }
}
