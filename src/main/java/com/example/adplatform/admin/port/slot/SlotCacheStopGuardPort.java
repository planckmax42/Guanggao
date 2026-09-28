package com.example.adplatform.admin.port.slot;

public interface SlotCacheStopGuardPort {
    void writeToStopGuardCache(String slotCode);
    void evictFromStopGuardCache(String slotCode);
    boolean isMemberStopGuardCache(String slotCode);
}
