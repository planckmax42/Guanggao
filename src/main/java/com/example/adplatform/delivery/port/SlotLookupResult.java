package com.example.adplatform.delivery.port;

import java.util.Objects;

/** 广告位查询结果，显式区分不存在与缓存不可用。 */
public record SlotLookupResult(Status status, Long slotId) {

    public SlotLookupResult {
        Objects.requireNonNull(status, "status");
        if (status == Status.ENABLED) {
            Objects.requireNonNull(slotId, "slotId");
        }
    }

    public static SlotLookupResult enabled(Long slotId) {
        return new SlotLookupResult(Status.ENABLED, slotId);
    }

    public static SlotLookupResult notFound() {
        return new SlotLookupResult(Status.NOT_FOUND, null);
    }

    public static SlotLookupResult cacheUnavailable() {
        return new SlotLookupResult(Status.CACHE_UNAVAILABLE, null);
    }

    public enum Status {
        ENABLED,
        NOT_FOUND,
        CACHE_UNAVAILABLE
    }
}
