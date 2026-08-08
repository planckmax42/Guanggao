package com.example.adplatform.infra.bloom.tracking.materialMetadata;

/** 素材元数据布隆过滤器维护接口。 */
public interface MaterialMetadataBloomService {

    boolean definitelyNotContains(Long materialId);

    void addBloomFilter(Long materialId);

    BloomSnapshot GetBloomFilterSnapshot();

    BloomRebuildResult regularRebuild();

    BloomRebuildResult expandRebuild();

}
