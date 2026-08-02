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
import java.util.concurrent.atomic.AtomicReference;

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
    private final SlotBloomFilterProperties slotBloomFilterProperties;
    private final SlotBloomFilterTracker slotBloomFilterTracker;
    private final AtomicReference<FilterState> activeState;
    private RebuildContext rebuildingContext;
    private final Object filterLock = new Object();

    SlotBloomFilterServiceImpl(
            SlotMapper slotMapper,
            SlotBloomFilterProperties slotBloomFilterProperties,
            SlotBloomFilterTracker slotBloomFilterTracker) {
        this.slotMapper = slotMapper;
        this.slotBloomFilterProperties = slotBloomFilterProperties;
        this.slotBloomFilterTracker = slotBloomFilterTracker;
        long expectedInsertions = slotBloomFilterProperties.getExpectedInsertions();
        this.activeState = new AtomicReference<>(new FilterState(
                createBloomFilter(expectedInsertions),
                expectedInsertions,
                false));
    }

    /** {@inheritDoc} */
    @Override
    public boolean definitelyNotContains(String slotCode) {
        FilterState state = activeState.get();
        return state.ready()
                && StringUtils.hasText(slotCode)
                && !state.filter().mightContain(slotCode);
    }

    /** {@inheritDoc} */
    @Override
    public void put(String slotCode) {
        if (!StringUtils.hasText(slotCode)) {
            return;
        }
        synchronized (filterLock) {
            activeState.get().filter().put(slotCode);
            if (rebuildingContext != null) {
                rebuildingContext.standbyFilter().put(slotCode);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public Optional<List<SlotEntity>> regularRebuild() {
        return rebuildExecutor(RebuildMode.REGULAR);
    }

    /** {@inheritDoc} */
    @Override
    public boolean expandAndRebuild() {
        return rebuildExecutor(RebuildMode.EXPANSION).isPresent();
    }

    /** {@inheritDoc} */
    @Override
    public Status status() {
        FilterState state = activeState.get();
        return new Status(
                state.expectedInsertions(),
                approximateElementCount(state.filter()),
                state.filter().expectedFpp(),
                state.ready());
    }

    private Optional<List<SlotEntity>> rebuildExecutor(RebuildMode mode) {
        RebuildContext context = null;
        List<SlotEntity> enabledSlots;
        try {
            synchronized (filterLock) {
                if (rebuildingContext != null) {
                    return Optional.empty();
                }
                FilterState currentState = activeState.get();
                long targetCapacity = calculateTargetCapacity(mode, currentState.expectedInsertions());
                if (mode == RebuildMode.EXPANSION
                        && targetCapacity <= currentState.expectedInsertions()) {
                    return Optional.empty();
                }
                context = new RebuildContext(
                        createBloomFilter(targetCapacity),
                        targetCapacity);
                rebuildingContext = context;
            }
            enabledSlots = Objects.requireNonNull(
                    slotMapper.selectList(new LambdaQueryWrapper<SlotEntity>()
                            .eq(SlotEntity::getStatus, CommonStatus.ENABLED)),
                    "SlotMapper不能返回null");
            enabledSlots.stream()
                    .map(SlotEntity::getSlotCode)
                    .filter(StringUtils::hasText)
                    .forEach(context.standbyFilter()::put);
            publishStandbyFilter(context);
        } catch (RuntimeException ex) {
            log.warn("广告位布隆过滤器{}失败，保留当前过滤器", mode.operation(), ex);
            return Optional.empty();
        } finally {
            cancel(context);
        }
        slotBloomFilterTracker.reset();
        log.info("广告位布隆过滤器{}完成，启用广告位数量={}", mode.operation(), enabledSlots.size());
        return Optional.of(enabledSlots);
    }

    private void publishStandbyFilter(RebuildContext context) {
        synchronized (filterLock) {
            if (rebuildingContext != context) {
                throw new IllegalStateException("广告位布隆过滤器重建上下文已失效");
            }
            activeState.set(new FilterState(
                    context.standbyFilter(),
                    context.expectedInsertions(),
                    true));
            rebuildingContext = null;
        }
    }

    private void cancel(RebuildContext context) {
        if (context == null) {
            return;
        }
        synchronized (filterLock) {
            if (rebuildingContext == context) {
                rebuildingContext = null;
            }
        }
    }

    private long calculateTargetCapacity(RebuildMode mode, long currentCapacity) {
        return switch (mode) {
            case REGULAR -> currentCapacity;
            case EXPANSION -> {
                long maxCapacity = Math.max(
                        currentCapacity,
                        slotBloomFilterProperties.getMaxExpectedInsertions());
                yield Math.min(
                        maxCapacity,
                        Math.max(currentCapacity + 1L,
                                (long) Math.ceil(currentCapacity
                                        * slotBloomFilterProperties.getExpansionFactor())));
            }
        };
    }

    private BloomFilter<CharSequence> createBloomFilter(long expectedInsertions) {
        return BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                expectedInsertions,
                slotBloomFilterProperties.getFalsePositiveProbability());
    }

    private long approximateElementCount(BloomFilter<CharSequence> filter) {
        try {
            return filter.approximateElementCount();
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }

    private record FilterState(
            BloomFilter<CharSequence> filter,
            long expectedInsertions,
            boolean ready) {
    }

    private record RebuildContext(
            BloomFilter<CharSequence> standbyFilter,
            long expectedInsertions) {
    }

    private enum RebuildMode {
        REGULAR("重建"),
        EXPANSION("扩容重建");

        private final String operation;

        RebuildMode(String operation) {
            this.operation = operation;
        }

        private String operation() {
            return operation;
        }
    }
}
