package com.example.adplatform.infra.bloomfilter.tracking.materialMetadata;

import java.util.List;
import java.util.Optional;

public record BloomRebuildResult(
        RebuildStatus rebuildStatus,
        Optional<List<Long>> slotNames,
        long newCapacity
) {
    public enum RebuildStatus {
        SUCCESS,
        LOCK_BUSY
    }
}
