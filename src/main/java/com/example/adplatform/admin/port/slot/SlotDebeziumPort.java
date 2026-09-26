package com.example.adplatform.admin.port.slot;

public interface SlotDebeziumPort {

    void writeCacheWithRetry(Long slotId, String slotCode);

    void updateCacheWithRetry(Long slotId, String oldSlotCode, String newSlotCode);

    void syncElasticsearchWithRetry(String slotCode);
}
