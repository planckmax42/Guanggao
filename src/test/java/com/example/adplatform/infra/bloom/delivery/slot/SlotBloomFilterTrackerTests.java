package com.example.adplatform.infra.bloom.delivery.slot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlotBloomFilterTrackerTests {

    @Test
    void shouldCalculateActualFalsePositiveRateFromAbsentRequests() {
        SlotBloomFilterTracker metrics = new SlotBloomFilterTracker();
        for (int i = 0; i < 990; i++) {
            metrics.recordDefiniteMiss();
        }
        for (int i = 0; i < 10; i++) {
            metrics.recordFalsePositive();
        }

        SlotBloomFilterTracker.Snapshot snapshot = metrics.snapshot();

        assertEquals(1_000L, snapshot.confirmedAbsentCount());
        assertEquals(0.01D, snapshot.actualFalsePositiveRate(), 0.000001D);
    }

    @Test
    void shouldStartNewMeasurementWindowAfterReset() {
        SlotBloomFilterTracker metrics = new SlotBloomFilterTracker();
        metrics.recordDefiniteMiss();
        metrics.recordFalsePositive();

        metrics.reset();

        assertEquals(0L, metrics.snapshot().confirmedAbsentCount());
        assertEquals(0D, metrics.snapshot().actualFalsePositiveRate());
    }
}
