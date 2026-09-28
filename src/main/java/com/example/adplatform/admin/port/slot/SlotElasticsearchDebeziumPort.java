package com.example.adplatform.admin.port.slot;

public interface SlotElasticsearchDebeziumPort {
    void syncElasticsearchWithRetry(String slotCode);
}
