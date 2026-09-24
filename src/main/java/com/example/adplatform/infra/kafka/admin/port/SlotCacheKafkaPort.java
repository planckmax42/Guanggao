package com.example.adplatform.infra.kafka.admin.port;

public interface SlotCacheKafkaPort {
    void writeSlotToRedis(Long slotId, String slotCode);
    void evictSlotCodeFromRedis(String slotCode);
}
