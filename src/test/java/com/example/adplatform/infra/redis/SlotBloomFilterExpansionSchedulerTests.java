package com.example.adplatform.infra.redis;

import org.junit.jupiter.api.Test;

import com.example.adplatform.admin.entity.SlotEntity;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlotBloomFilterExpansionSchedulerTests {

    @Test
    void shouldExpandWhenActualAndExpectedRatesReachThreshold() {
        SlotCacheProperties properties = createProperties();
        SlotCodeBloomFilterManager manager = new SlotCodeBloomFilterManager(properties);
        List<String> saturatedSlotCodes = IntStream.range(0, 100)
                .mapToObj(index -> "SLOT_" + index)
                .toList();
        manager.rebuild(() -> saturatedSlotCodes, slots -> slots);
        SlotBloomFilterMetrics metrics = new SlotBloomFilterMetrics();
        metrics.recordRejectedAbsent();
        metrics.recordFalsePositive();
        RecordingSlotCacheService slotCacheService = new RecordingSlotCacheService(() ->
                manager.expandAndRebuild(() -> saturatedSlotCodes, slots -> slots));
        SlotBloomFilterExpansionScheduler scheduler = new SlotBloomFilterExpansionScheduler(
                metrics, manager, properties, slotCacheService);

        scheduler.checkAndExpand();

        assertEquals(1, slotCacheService.expansionCount);
        assertEquals(2L, manager.status().expectedInsertions());
    }

    @Test
    void shouldNotExpandBeforeEnoughSamplesAreCollected() {
        SlotCacheProperties properties = createProperties();
        properties.getBloom().setMinimumAbsentSamples(100L);
        SlotCodeBloomFilterManager manager = new SlotCodeBloomFilterManager(properties);
        manager.rebuild(() -> List.of("SLOT_1", "SLOT_2", "SLOT_3"), slots -> slots);
        SlotBloomFilterMetrics metrics = new SlotBloomFilterMetrics();
        metrics.recordFalsePositive();
        RecordingSlotCacheService slotCacheService = new RecordingSlotCacheService(() -> Optional.empty());
        SlotBloomFilterExpansionScheduler scheduler = new SlotBloomFilterExpansionScheduler(
                metrics, manager, properties, slotCacheService);

        scheduler.checkAndExpand();

        assertEquals(0, slotCacheService.expansionCount);
    }

    private SlotCacheProperties createProperties() {
        SlotCacheProperties properties = new SlotCacheProperties();
        SlotCacheProperties.Bloom bloom = properties.getBloom();
        bloom.setExpectedInsertions(1);
        bloom.setFalsePositiveProbability(0.01D);
        bloom.setMinimumAbsentSamples(2L);
        bloom.setExpansionCooldown(Duration.ZERO);
        bloom.setMaxExpectedInsertions(100L);
        return properties;
    }

    private static class RecordingSlotCacheService implements SlotCacheService {

        private final Supplier<Optional<List<String>>> expansionAction;
        private int expansionCount;

        private RecordingSlotCacheService(
                Supplier<Optional<List<String>>> expansionAction) {
            this.expansionAction = expansionAction;
        }

        @Override
        public Optional<Long> getEnabledSlotIdByCode(String slotCode) {
            return Optional.empty();
        }

        @Override
        public void cacheSlot(SlotEntity slot) {
        }

        @Override
        public void refreshSlot(SlotEntity slot, String oldSlotCode) {
        }

        @Override
        public void warmUp() {
        }

        @Override
        public boolean rebuildBloomFilter() {
            return false;
        }

        @Override
        public boolean expandAndRebuildBloomFilter() {
            expansionCount++;
            return expansionAction.get().isPresent();
        }
    }
}
