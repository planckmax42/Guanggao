package com.example.adplatform.delivery.port;

/** 广告位查询结果，显式区分不存在与缓存不可用。 */
public record SlotIdResult(Status status, Long slotId) {
    public static SlotIdResult enabled(Long slotId) {
        return new SlotIdResult(Status.ENABLED, slotId);
    }

    public static SlotIdResult notFound() {
        return new SlotIdResult(Status.NOT_FOUND, null);
    }

    public static SlotIdResult cacheUnavailable() {
        return new SlotIdResult(Status.CACHE_UNAVAILABLE, null);
    }

    public enum Status {
        ENABLED,
        NOT_FOUND,
        CACHE_UNAVAILABLE
    }
}
