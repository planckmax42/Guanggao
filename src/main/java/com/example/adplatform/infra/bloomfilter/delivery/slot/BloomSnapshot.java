package com.example.adplatform.infra.bloomfilter.delivery.slot;

public record BloomSnapshot(
        boolean bloomFilterReady,
        long currentCapacity,
        long totalCount,
        double estimatedFalsePositiveRate,
        double actualFalsePositiveRate) {
}
