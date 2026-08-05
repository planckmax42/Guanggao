package com.example.adplatform.infra.bloom.tracking.materialMetadata;

/** 素材元数据布隆过滤器维护接口。 */
public interface MaterialMetadataBloomFilterService {

    void regularRebuild();

    void expandRebuild();
}
