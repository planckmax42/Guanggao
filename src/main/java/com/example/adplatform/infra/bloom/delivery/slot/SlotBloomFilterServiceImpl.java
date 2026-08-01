package com.example.adplatform.infra.bloom.delivery.slot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.common.enums.CommonStatus;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 广告位布隆过滤器默认实现。
 *
 * <p>在线过滤器使用双缓冲重建。重建期间新增的广告位会同时写入当前过滤器和备用过滤器，
 * 构建成功后原子切换；查询或构建失败时继续使用原过滤器。</p>
 */
@Slf4j
@Service
public class SlotBloomFilterServiceImpl implements SlotBloomFilterService {

    private final SlotMapper slotMapper;
    private final SlotBloomFilterProperties properties;
    private final SlotBloomFilterTracker tracker;
    private final AtomicReference<BloomFilter<CharSequence>> activeFilter;
    private final AtomicLong currentExpectedInsertions;
    private final Object filterLock = new Object();

    /** 只能在 {@link #filterLock} 保护下访问，同时表示当前是否正在重建。 */
    private BloomFilter<CharSequence> rebuildingFilter;

    /** 过滤器成功加载过 MySQL 权威数据后才允许拦截请求。 */
    private volatile boolean ready;

    SlotBloomFilterServiceImpl(
            SlotMapper slotMapper,
            SlotBloomFilterProperties properties,
            SlotBloomFilterTracker tracker) {
        this.slotMapper = slotMapper;
        this.properties = properties;
        this.tracker = tracker;
        long expectedInsertions = properties.getExpectedInsertions();
        this.currentExpectedInsertions = new AtomicLong(expectedInsertions);
        this.activeFilter = new AtomicReference<>(createBloomFilter(expectedInsertions));
    }

    /** {@inheritDoc} */
    @Override
    public boolean definitelyNotContains(String slotCode) {
        return ready && StringUtils.hasText(slotCode) && !activeFilter.get().mightContain(slotCode);
    }

    /** {@inheritDoc} */
    @Override
    public void put(String slotCode) {
        if (!StringUtils.hasText(slotCode)) {
            return;
        }
        synchronized (filterLock) {
            activeFilter.get().put(slotCode);
            if (rebuildingFilter != null) {
                rebuildingFilter.put(slotCode);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public Optional<List<SlotEntity>> rebuildWithSnapshot() {
        return executeRebuild(
                "重建",
                () -> rebuildWithCapacity(currentExpectedInsertions.get()));
    }

    /** {@inheritDoc} */
    @Override
    public boolean rebuild() {
        return rebuildWithSnapshot().isPresent();
    }

    /** {@inheritDoc} */
    @Override
    public boolean expandAndRebuild() {
        return executeRebuild("扩容重建", this::expandAndRebuildInternal).isPresent();
    }

    /** {@inheritDoc} */
    @Override
    public Status status() {
        BloomFilter<CharSequence> filter = activeFilter.get();
        return new Status(
                currentExpectedInsertions.get(),
                approximateElementCount(filter),
                filter.expectedFpp(),
                ready);
    }

    private Optional<List<SlotEntity>> executeRebuild(
            String operation,
            Supplier<Optional<List<SlotEntity>>> rebuildAction) {
        Optional<List<SlotEntity>> enabledSlots;
        try {
            enabledSlots = rebuildAction.get();
        } catch (RuntimeException ex) {
            log.warn("广告位布隆过滤器{}失败，保留当前过滤器", operation, ex);
            return Optional.empty();
        }

        enabledSlots.ifPresent(slots -> {
            tracker.reset();
            log.info("广告位布隆过滤器{}完成，启用广告位数量={}", operation, slots.size());
        });
        return enabledSlots;
    }

    private Optional<List<SlotEntity>> expandAndRebuildInternal() {
        long currentCapacity = currentExpectedInsertions.get();
        if (properties.getExpansionFactor() <= 1D) {
            throw new IllegalArgumentException("布隆过滤器扩容倍数必须大于 1");
        }
        long maxCapacity = Math.max(currentCapacity, properties.getMaxExpectedInsertions());
        long expandedCapacity = Math.min(
                maxCapacity,
                Math.max(currentCapacity + 1L,
                        (long) Math.ceil(currentCapacity * properties.getExpansionFactor())));
        if (expandedCapacity <= currentCapacity) {
            return Optional.empty();
        }
        return rebuildWithCapacity(expandedCapacity);
    }

    private Optional<List<SlotEntity>> rebuildWithCapacity(long expectedInsertions) {
        BloomFilter<CharSequence> standbyFilter;
        synchronized (filterLock) {
            if (rebuildingFilter != null) {
                return Optional.empty();
            }
            standbyFilter = createBloomFilter(expectedInsertions);
            rebuildingFilter = standbyFilter;
        }

        try {
            // 先发布备用过滤器再查询 MySQL，保证查询期间新增编码也会被 put() 写入新过滤器。
            List<SlotEntity> enabledSlots = Objects.requireNonNull(
                    selectAllEnabledSlots(),
                    "SlotMapper不能返回null");
            enabledSlots.stream()
                    .map(SlotEntity::getSlotCode)
                    .filter(StringUtils::hasText)
                    .forEach(standbyFilter::put);

            synchronized (filterLock) {
                activeFilter.set(standbyFilter);
                currentExpectedInsertions.set(expectedInsertions);
                rebuildingFilter = null;
                ready = true;
            }
            return Optional.of(enabledSlots);
        } finally {
            synchronized (filterLock) {
                if (rebuildingFilter == standbyFilter) {
                    rebuildingFilter = null;
                }
            }
        }
    }

    private List<SlotEntity> selectAllEnabledSlots() {
        return slotMapper.selectList(new LambdaQueryWrapper<SlotEntity>()
                .eq(SlotEntity::getStatus, CommonStatus.ENABLED));
    }

    private BloomFilter<CharSequence> createBloomFilter(long expectedInsertions) {
        return BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                expectedInsertions,
                properties.getFalsePositiveProbability());
    }

    private long approximateElementCount(BloomFilter<CharSequence> filter) {
        try {
            return filter.approximateElementCount();
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }
}
