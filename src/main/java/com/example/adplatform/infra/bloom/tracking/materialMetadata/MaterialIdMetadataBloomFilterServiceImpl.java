package com.example.adplatform.infra.bloom.tracking.materialMetadata;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.example.adplatform.admin.mapper.MaterialMapper;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/** 使用 active/standby 双缓冲安全重建 materialId 布隆过滤器。 */
@Slf4j
@Component
@SuppressWarnings("UnstableApiUsage")
public class MaterialIdMetadataBloomFilterServiceImpl implements  MaterialMetadataBloomFilterService {

    private final MaterialMapper materialMapper;
    private final MaterialMetadataBloomFilterProperties materialMetadataBloomFilterProperties;
    private final AtomicReference<BloomFilter<Long>> currentBloomFilter;
    private BloomFilter<Long> standbyBloomFilter;
    private Long currentCapacity;//todo：是否有必要原子 final
    private final AtomicLong maximumLoadedId = new AtomicLong();//todo：上界水位保证防误杀
    private final Object updateLock = new Object();
    private final ReentrantLock rebuildLock = new ReentrantLock();//todo为什么锁是final 这里要是不new会怎样

    public MaterialIdMetadataBloomFilterServiceImpl(MaterialMetadataBloomFilterProperties materialMetadataBloomFilterProperties, MaterialMapper materialMapper) {
        this.materialMetadataBloomFilterProperties = materialMetadataBloomFilterProperties;
        long initialCapacity = materialMetadataBloomFilterProperties.getExpectedInsertions();
        this.currentCapacity = initialCapacity;
        this.currentBloomFilter = new AtomicReference<>(createBloomFilter(initialCapacity));
        this.materialMapper = materialMapper;
    }

    /**
     * 只对重建时已覆盖的自增 ID 区间做确定不存在判断。更大的新 ID
     * 会继续查 MySQL，避免多实例本地过滤器尚未同步时误杀新素材。
     */
    public boolean definitelyNotContains(Long materialId) {
        return materialId != null//todo:太多&&看看是不是有问题
                && materialId > 0
                && materialId <= maximumLoadedId.get()
                && !currentBloomFilter.get().mightContain(materialId);
    }

    public void addBloomFilter(Long materialId) {
        if (materialId == null || materialId <= 0) {
            return;
        }
        synchronized (updateLock) {
            currentBloomFilter.get().put(materialId);
            if (standbyBloomFilter != null) {
                standbyBloomFilter.put(materialId);
            }
            maximumLoadedId.accumulateAndGet(materialId, Math::max);
        }
    }
    public BloomFilterSnapshot GetBloomFilterSnapshot() {
        BloomFilter<Long> filter = currentBloomFilter.get();
        return new BloomFilterSnapshot(
                currentCapacity,
                filter.approximateElementCount(),
                filter.expectedFpp(),
                maximumLoadedId.get());
    }
    @Override
    public void regularRebuild() {
        rebuildExecutor(RebuildMode.REGULAR_REBUILD);
    }

    @Override
    public void expandRebuild() {
        rebuildExecutor(RebuildMode.EXPAND_REBUILD);
    }
    private void rebuildExecutor(RebuildMode rebuildMode) {
        if(!rebuildLock.tryLock()) {
            return;
        }
        Long targetCapacity = switch (rebuildMode) {
            case REGULAR_REBUILD -> currentCapacity;
            case EXPAND_REBUILD -> (long)Math.ceil(currentCapacity * materialMetadataBloomFilterProperties.getExpansionFactor());
        };
        synchronized (updateLock){
            standbyBloomFilter = createBloomFilter(targetCapacity);
        }
        List<Long> materialIds;
        try {
            materialIds = materialMapper.selectAllMaterialIds();
            materialIds.forEach(standbyBloomFilter::put);
            synchronized (updateLock){
                currentBloomFilter.set(standbyBloomFilter);
                standbyBloomFilter=null;
            }
        }finally {
            synchronized (updateLock) {
                standbyBloomFilter = null;
            }
        }
        currentCapacity = targetCapacity;
    }

    private BloomFilter<Long> createBloomFilter(long targetCapacity) {
        return BloomFilter.create(
                Funnels.longFunnel(),
                targetCapacity,
                materialMetadataBloomFilterProperties.getFalsePositiveProbability());
    }

    public record BloomFilterSnapshot(
            long expectedInsertions,
            long approximateElementCount,
            double expectedFalsePositiveProbability,
            long maximumLoadedId) {
    }

    private enum RebuildMode{
        REGULAR_REBUILD,
        EXPAND_REBUILD;
    }
}
