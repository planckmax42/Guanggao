package com.example.adplatform.infra.bloom.tracking.material;

/** 素材元数据布隆过滤器维护接口。 */
public interface MaterialBloomMaintenance {

    boolean rebuildBloomFilter();

    boolean expandAndRebuildBloomFilter();
}
