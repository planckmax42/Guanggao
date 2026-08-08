package com.example.adplatform.infra.bloom.tracking.materialMetadata;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.example.adplatform.admin.mapper.MaterialMapper;
import com.example.adplatform.infra.bloom.tracking.materialMetadata.BloomRebuildResult.RebuildStatus;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/** 使用 active/standby 双缓冲安全重建 materialId 布隆过滤器。 */
@Slf4j
@Component
@SuppressWarnings("UnstableApiUsage")//todo：删除上界水位保证防误杀，后续往分布式方向靠齐
public class BloomServiceImpl implements MaterialMetadataBloomService {

    private final MaterialMapper materialMapper;
    private final BloomProperties bloomProperties;
    private final AtomicReference<BloomFilter<Long>> currentBloomFilter;
    private BloomFilter<Long> standbyBloomFilter;
    private Long currentCapacity;//todo：是否有必要原子 final
    private final Object updateLock = new Object();
    private final ReentrantLock rebuildLock = new ReentrantLock();//todo为什么锁是final 这里要是不new会怎样

    public BloomServiceImpl(BloomProperties bloomProperties, MaterialMapper materialMapper) {
        this.bloomProperties = bloomProperties;
        long initialCapacity = bloomProperties.getInitialCapacity();
        this.currentCapacity = initialCapacity;
        this.currentBloomFilter = new AtomicReference<>(createBloomFilter(initialCapacity));
        this.materialMapper = materialMapper;
    }

    @Override
    public boolean definitelyNotContains(Long materialId) {
        return materialId != null//todo:太多&&看看是不是有问题
                && materialId > 0
                && !currentBloomFilter.get().mightContain(materialId);
    }

    @Override
    public void addBloomFilter(Long materialId) {
        if (materialId == null || materialId <= 0) {
            return;
        }
        synchronized (updateLock) {
            currentBloomFilter.get().put(materialId);
            if (standbyBloomFilter != null) {
                standbyBloomFilter.put(materialId);
            }
        }
    }

    @Override
    public BloomSnapshot GetBloomFilterSnapshot() {
        BloomFilter<Long> filter = currentBloomFilter.get();
        return new BloomSnapshot(
                currentCapacity,
                filter.expectedFpp());
    }
//todo：approximateElementCount()看看有什么用
    @Override
    public BloomRebuildResult regularRebuild() {
        return rebuildExecutor(RebuildMode.REGULAR_REBUILD);
    }

    @Override
    public BloomRebuildResult expandRebuild() {
        return rebuildExecutor(RebuildMode.EXPAND_REBUILD);
    }

    private BloomRebuildResult rebuildExecutor(RebuildMode rebuildMode) {
        if(!rebuildLock.tryLock()) {
            return new BloomRebuildResult(RebuildStatus.LOCK_BUSY,Optional.empty(),currentCapacity);
        }
        try {
            Long targetCapacity = switch (rebuildMode) {
                case REGULAR_REBUILD -> currentCapacity;
                case EXPAND_REBUILD -> Math.min(
                        bloomProperties.getMaxCapacity(),
                        (long) Math.ceil(currentCapacity * bloomProperties.getExpansionFactor()));
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
            return new BloomRebuildResult(RebuildStatus.SUCCESS,Optional.of(materialIds),targetCapacity);
        }finally {
            rebuildLock.unlock();
        }
    }

    private BloomFilter<Long> createBloomFilter(long targetCapacity) {
        return BloomFilter.create(
                Funnels.longFunnel(),
                targetCapacity,
                bloomProperties.getFalsePositiveProbability());
    }

    private enum RebuildMode{
        REGULAR_REBUILD,
        EXPAND_REBUILD
    }
}
