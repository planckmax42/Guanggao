package com.example.adplatform.admin.port.slot;

public interface SlotDebeziumPort {

    void writeRedisWithRetry(Long slotId,String slotCode);

    void updateRedisWithRetry(Long slotId,String oldSlotCode,String newSlotCode);
}
