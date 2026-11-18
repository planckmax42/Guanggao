package com.example.adplatform.infra.redis.delivery.materialMetadata;

import com.example.adplatform.delivery.portMQ.DeliveryMetadataCachePort;
import org.springframework.stereotype.Service;

@Service
public class MaterialMetadataService implements DeliveryMetadataCachePort {
    public void writeMetadataToRedis(MaterialMetadata materialMetadata){
        //此函数向redis写入元数据缓存 key（Long）是materialID value（JSON）是其他字段
    }
}
