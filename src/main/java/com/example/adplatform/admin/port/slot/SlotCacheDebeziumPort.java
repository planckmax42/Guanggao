package com.example.adplatform.admin.port.slot;

public interface SlotCacheDebeziumPort {

    void writeCacheWithRetry(Long slotId, String slotCode);

    void updateCacheWithRetry(Long slotId, String oldSlotCode, String newSlotCode);

    void evictCacheWithRetry(String slotCode);
}
