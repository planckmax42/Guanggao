package com.example.adplatform.infra.bloom.delivery.slot;

import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.infra.redis.delivery.slot.SlotCacheProperties;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.Getter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 使用双缓冲管理广告位编码布隆过滤器。
 *
 * <p>activeFilter 始终服务线上查询；rebuildingFilter 在后台根据 MySQL 全量数据构建。
 * 重建期间新增广告位会同时写入两份过滤器，构建成功后再原子替换 activeFilter。</p>
 */
@Component
public class SlotCodeBloomFilterManager {

    /** 布隆过滤器容量、误判率和扩容上限配置。 */
    private final SlotCacheProperties properties;

    /** 当前为线上查询服务的布隆过滤器。 */
    private final AtomicReference<BloomFilter<CharSequence>> activeFilter;

    /**
     * 正在后台构建的备用过滤器；无重建任务时为 {@code null}。
     *
     * <p>该字段只能在 {@link #filterLock} 保护下访问，同时兼作“当前正在重建”的状态标记。</p>
     */
    private BloomFilter<CharSequence> rebuildingFilter;

    /** 当前过滤器的预期插入量，扩容成功后更新。 */
    private final AtomicLong currentExpectedInsertions;

    /** 保证备用过滤器发布、在线双写和新旧过滤器切换不会互相穿插。 */
    private final Object filterLock = new Object();

    /** 当前在线过滤器是否已成功加载过权威数据。 */
    @Getter
    private volatile boolean ready;

    /**
     * 使用配置的预期插入量创建初始空过滤器。
     *
     * <p>初始过滤器尚未加载 MySQL 数据，因此 {@code ready} 保持 {@code false}，
     * 不会用空过滤器拦截请求。</p>
     *
     * @param properties 广告位缓存与布隆过滤器配置
     */
    public SlotCodeBloomFilterManager(SlotCacheProperties properties) {
        this.properties = properties;
        long expectedInsertions = Math.max(1L, properties.getBloom().getExpectedInsertions());
        this.currentExpectedInsertions = new AtomicLong(expectedInsertions);
        this.activeFilter = new AtomicReference<>(createBloomFilter(expectedInsertions));
    }

    /**
     * 返回 true 表示编码一定不存在；返回 false 表示编码可能存在，需要继续查询缓存。
     *
     * <p>过滤器未就绪或编码为空时始终返回 {@code false}，避免产生错误拦截。</p>
     *
     * @param slotCode 待检查的广告位编码
     * @return 过滤器已就绪且明确不包含该编码时返回 {@code true}
     */
    public boolean definitelyNotContains(String slotCode) {
        return ready && StringUtils.hasText(slotCode) && !activeFilter.get().mightContain(slotCode);//同样防御性校验
    }

    /**
     * 新增或重新启用广告位时，同时写入当前过滤器和正在构建的过滤器。
     *
     * <p>使用 {@code filterLock} 将双写与过滤器切换串行化，确保重建期间新增的编码不会丢失。</p>
     *
     * @param slotCode 待写入的广告位编码；空白编码会被忽略
     */
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

    /**
     * 从权威数据源加载全部启用广告位并重建过滤器。重建失败时继续保留旧过滤器。
     *
     * @param slotLoader 无参数的启用广告位加载操作；由 Manager 在公布备用过滤器后调用
     * @return 本次从数据源加载的广告位；已有重建任务运行时返回 empty
     * @throws RuntimeException 数据加载或过滤器构建失败时向上抛出
     */
    public Optional<List<SlotEntity>> rebuild(Supplier<List<SlotEntity>> slotLoader) {
        return rebuildWithCapacity(currentExpectedInsertions.get(), slotLoader);
    }

    /**
     * 按配置的扩容倍数创建更大的过滤器，并从权威数据源重新加载数据。
     *
     * @param slotLoader 无参数的启用广告位加载操作
     * @return 重建使用的广告位列表；已达容量上限或有任务正在重建时返回 empty
     * @throws IllegalArgumentException 扩容倍数不大于 {@code 1} 时抛出
     */
    public Optional<List<SlotEntity>> expandAndRebuild(Supplier<List<SlotEntity>> slotLoader) {
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
        return rebuildWithCapacity(expandedCapacity, slotLoader);
    }

    /**
     * 获取当前在线过滤器的容量、近似元素数、理论误判率和就绪状态。
     *
     * @return 当前过滤器状态快照
     */
    public Status status() {
        BloomFilter<CharSequence> filter = activeFilter.get();
        return new Status(
                currentExpectedInsertions.get(),
                approximateElementCount(filter),
                filter.expectedFpp(),
                ready);
    }

    /**
     * 使用指定预期插入量执行一次双缓冲重建。
     *
     * <p>方法先在短锁内检查并公布备用过滤器；非空的 {@code rebuildingFilter} 同时表示已有重建任务。
     * 加载期间的新增编码由 {@link #put(String)} 双写；构建成功后在同一把短锁内替换在线过滤器。
     * 如果加载抛出异常，外层 {@code finally} 会清理备用引用，旧过滤器保持不变。</p>
     *
     * @param expectedInsertions 新过滤器的预期插入量
     * @param slotLoader 启用广告位加载操作
     * @return 加载到的广告位；已有重建任务时返回 empty
     */
    private Optional<List<SlotEntity>> rebuildWithCapacity(
            long expectedInsertions,
            Supplier<List<SlotEntity>> slotLoader) {
        BloomFilter<CharSequence> standbyFilter;
        synchronized (filterLock) {
            if (rebuildingFilter != null) {
                return Optional.empty();
            }
            standbyFilter = createBloomFilter(expectedInsertions);
            rebuildingFilter = standbyFilter;
        }

        try {
            // 先公布 standbyFilter 再查数据库，保证查询期间新增的广告位也会被 put() 写入新过滤器。
            List<SlotEntity> slots = slotLoader.get();
            if (slots != null) {
                slots.stream()
                        .map(SlotEntity::getSlotCode)
                        .filter(StringUtils::hasText)
                        .forEach(standbyFilter::put);
            }

            synchronized (filterLock) {
                activeFilter.set(standbyFilter);
                currentExpectedInsertions.set(expectedInsertions);
                rebuildingFilter = null;
                ready = true;
            }
            return Optional.ofNullable(slots);
        } finally {
            synchronized (filterLock) {
                // 仅撤销本次重建发布的过滤器，不能清除随后开始的新重建任务。
                if (rebuildingFilter == standbyFilter) {
                    rebuildingFilter = null;
                }
            }
        }
    }

    /**
     * 按 UTF-8 广告位编码、预期插入量和配置误判率创建空布隆过滤器。
     *
     * @param expectedInsertions 预期插入的广告位编码数
     * @return 新创建的空布隆过滤器
     * @throws IllegalArgumentException 配置误判率不在 {@code (0, 1)} 区间时抛出
     */
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

    /**
     * 使用 Guava 位图占用情况估算已插入元素数。
     *
     * @param filter 待估算的布隆过滤器
     * @return 近似元素数；位图完全饱和无法计算时返回 {@link Long#MAX_VALUE}
     */
    private long approximateElementCount(BloomFilter<CharSequence> filter) {
        try {
            return filter.approximateElementCount();
        } catch (ArithmeticException ex) {
            // 位图完全饱和时数学估算结果为无穷大，用最大值表示容量已严重不足。
            return Long.MAX_VALUE;
        }
    }

    /**
     * 布隆过滤器运行状态快照。
     *
     * @param expectedInsertions 当前过滤器预期插入量
     * @param approximateElementCount 近似已插入元素数
     * @param expectedFalsePositiveProbability 当前位图状态下的理论误判率
     * @param ready 过滤器是否已加载权威数据并可用于拦截
     */
    public record Status(
            long expectedInsertions,
            long approximateElementCount,
            double expectedFalsePositiveProbability,
            boolean ready) {
    }
}
