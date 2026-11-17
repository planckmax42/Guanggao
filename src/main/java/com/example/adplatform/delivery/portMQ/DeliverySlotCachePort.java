package com.example.adplatform.delivery.portMQ;


import com.example.adplatform.infra.redis.delivery.slot.MQ.RedisOperationResult;

public interface DeliverySlotCachePort {
    RedisOperationResult getSlotIdFromRedis(String slotCode);
    RedisOperationResult deleteSlotIdFromRedis(String slotCode);
    RedisOperationResult writeSlotIdFromRedis(String slotCode,Long slotId);
}
