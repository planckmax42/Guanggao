package com.example.adplatform.tracking.port;

import com.example.adplatform.tracking.service.EventMaterialMetadata;

/** 事件消费链路读取素材计费元数据的端口。 */
public interface EventMetadataReaderPort {

    EventMaterialMetadata get(Long materialId);
}
