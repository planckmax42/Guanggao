package com.example.adplatform.infra.bloom.tracking.materialMetadata;

public record BloomSnapshot(
        long currentCapacity,
        double expectedFalsePositiveProbability) {
}
