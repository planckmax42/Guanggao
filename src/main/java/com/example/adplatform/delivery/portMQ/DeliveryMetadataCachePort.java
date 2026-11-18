package com.example.adplatform.delivery.portMQ;

import com.example.adplatform.infra.redis.delivery.materialMetadata.MaterialMetadata;

public interface DeliveryMetadataCachePort {
    void writeMetadataToRedis(MaterialMetadata materialMetadata);
}
