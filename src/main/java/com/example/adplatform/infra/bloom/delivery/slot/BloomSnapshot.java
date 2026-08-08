package com.example.adplatform.infra.bloom.delivery.slot;

public record BloomSnapshot(
        boolean bloomFilterReady,
        long currentCapacity,
        long totalCount,
        double estimatedFalsePositiveRate,
        double actualFalsePositiveRate) {
}
