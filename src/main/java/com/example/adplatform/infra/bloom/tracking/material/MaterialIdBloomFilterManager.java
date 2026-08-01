package com.example.adplatform.infra.bloom.tracking.material;

import com.example.adplatform.infra.redis.tracking.metadata.EventMetadataCacheProperties;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** 使用 active/standby 双缓冲安全重建 materialId 布隆过滤器。 */
@Component
public class MaterialIdBloomFilterManager {

    private final EventMetadataCacheProperties properties;
    private final AtomicReference<BloomFilter<Long>> activeFilter;
    private final AtomicLong currentExpectedInsertions;
    private final AtomicLong maximumLoadedId = new AtomicLong();//todo：上界水位保证防误杀
    private final Object filterLock = new Object();
    private BloomFilter<Long> rebuildingFilter;
    private volatile boolean ready;

    public MaterialIdBloomFilterManager(EventMetadataCacheProperties properties) {
        this.properties = properties;
        long capacity = Math.max(1L, properties.getBloom().getExpectedInsertions());
        this.currentExpectedInsertions = new AtomicLong(capacity);
        this.activeFilter = new AtomicReference<>(create(capacity));
    }

    /**
     * 只对重建时已覆盖的自增 ID 区间做确定不存在判断。更大的新 ID
     * 会继续查 MySQL，避免多实例本地过滤器尚未同步时误杀新素材。
     */
    public boolean definitelyNotContains(Long materialId) {
        return ready//todo:太多&&看看是不是有问题
                && materialId != null
                && materialId > 0
                && materialId <= maximumLoadedId.get()
                && !activeFilter.get().mightContain(materialId);
    }

    public void put(Long materialId) {
        if (materialId == null || materialId <= 0) {
            return;
        }
        synchronized (filterLock) {
            activeFilter.get().put(materialId);
            if (rebuildingFilter != null) {
                rebuildingFilter.put(materialId);
            }
            maximumLoadedId.accumulateAndGet(materialId, Math::max);
        }
    }

    public Optional<List<Long>> rebuild(Supplier<List<Long>> loader) {
        return rebuildWithCapacity(currentExpectedInsertions.get(), loader);
    }

    public Optional<List<Long>> expandAndRebuild(Supplier<List<Long>> loader) {
        long current = currentExpectedInsertions.get();
        EventMetadataCacheProperties.Bloom config = properties.getBloom();
        long expanded = Math.min(
                Math.max(current, config.getMaxExpectedInsertions()),
                Math.max(current + 1, (long) Math.ceil(current * config.getExpansionFactor())));
        if (expanded <= current) {
            return Optional.empty();
        }
        return rebuildWithCapacity(expanded, loader);
    }

    public Status status() {
        BloomFilter<Long> filter = activeFilter.get();
        return new Status(
                currentExpectedInsertions.get(),
                filter.approximateElementCount(),
                filter.expectedFpp(),
                maximumLoadedId.get(),
                ready);
    }

    private Optional<List<Long>> rebuildWithCapacity(long capacity, Supplier<List<Long>> loader) {
        BloomFilter<Long> standby;
        synchronized (filterLock) {
            if (rebuildingFilter != null) {
                return Optional.empty();
            }
            standby = create(capacity);
            rebuildingFilter = standby;
        }

        try {
            List<Long> ids = loader.get();
            long loadedMaximum = 0L;
            if (ids != null) {
                for (Long id : ids) {
                    if (id != null && id > 0) {
                        standby.put(id);
                        loadedMaximum = Math.max(loadedMaximum, id);
                    }
                }
            }
            synchronized (filterLock) {
                // 重建期间 put() 的新 ID 已双写 standby，水位不能回退。
                loadedMaximum = Math.max(loadedMaximum, maximumLoadedId.get());
                activeFilter.set(standby);
                currentExpectedInsertions.set(capacity);
                maximumLoadedId.set(loadedMaximum);
                rebuildingFilter = null;
                ready = true;
            }
            return Optional.ofNullable(ids);
        } finally {
            synchronized (filterLock) {
                if (rebuildingFilter == standby) {
                    rebuildingFilter = null;
                }
            }
        }
    }

    private BloomFilter<Long> create(long capacity) {
        return BloomFilter.create(
                Funnels.longFunnel(),
                capacity,
                properties.getBloom().getFalsePositiveProbability());
    }

    public record Status(
            long expectedInsertions,
            long approximateElementCount,
            double expectedFalsePositiveProbability,
            long maximumLoadedId,
            boolean ready) {
    }
}
