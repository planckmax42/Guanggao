package com.example.adplatform.infra.redis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlotBloomFilterMetricsTests {

    @Test
    void shouldCalculateActualFalsePositiveRateFromAbsentRequests() {
        SlotBloomFilterMetrics metrics = new SlotBloomFilterMetrics();
        for (int i = 0; i < 990; i++) {
            metrics.recordRejectedAbsent();
        }
        for (int i = 0; i < 10; i++) {
            metrics.recordFalsePositive();
        }

        SlotBloomFilterMetrics.Snapshot snapshot = metrics.snapshot();

        assertEquals(1_000L, snapshot.absentSampleCount());
        assertEquals(0.01D, snapshot.actualFalsePositiveRate(), 0.000001D);
    }

    @Test
    void shouldStartNewMeasurementWindowAfterReset() {
        SlotBloomFilterMetrics metrics = new SlotBloomFilterMetrics();
        metrics.recordRejectedAbsent();
        metrics.recordFalsePositive();

        metrics.reset();

        assertEquals(0L, metrics.snapshot().absentSampleCount());
        assertEquals(0D, metrics.snapshot().actualFalsePositiveRate());
    }
}
