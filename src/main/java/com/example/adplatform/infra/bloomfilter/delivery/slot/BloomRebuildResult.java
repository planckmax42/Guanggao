package com.example.adplatform.infra.bloomfilter.delivery.slot;

import java.util.List;
import java.util.Optional;

public record BloomRebuildResult(
        RebuildStatus rebuildStatus,
        Optional<List<String>> slotNames,
        long newCapacity
) {
    public enum RebuildStatus {
        SUCCESS,
        LOCK_BUSY
    }
}

