package com.example.adplatform.infra.redis.delivery.slot;

/** Redis 广告位缓存访问失败，保留原始异常供重试和诊断。 */
public class SlotCacheAccessException extends RuntimeException {

    private final String operation;
    private final String slotCode;

    public SlotCacheAccessException(String operation, String slotCode, Throwable cause) {
        super("Slot cache operation failed: " + operation, cause);
        this.operation = operation;
        this.slotCode = slotCode;
    }

    public String getOperation() {
        return operation;
    }

    public String getSlotCode() {
        return slotCode;
    }
}
