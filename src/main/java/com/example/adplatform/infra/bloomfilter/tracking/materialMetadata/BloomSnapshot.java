package com.example.adplatform.infra.bloomfilter.tracking.materialMetadata;

public record BloomSnapshot(
        boolean bloomFilterReady,
        long currentCapacity,
        long totalCount,
        double estimatedFalsePositiveRate,
        double actualFalsePositiveRate) {
}
