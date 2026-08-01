package com.example.adplatform.infra.bloom.delivery.slot;

import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.infra.redis.delivery.slot.SlotCacheProperties;
import org.junit.jupiter.api.Test;

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
        List<SlotEntity> saturatedSlots = IntStream.range(0, 100)
                .mapToObj(index -> slot("SLOT_" + index))
                .toList();
        manager.rebuild(() -> saturatedSlots);
        SlotBloomFilterMetrics metrics = new SlotBloomFilterMetrics();
        metrics.recordDefiniteMiss();
        metrics.recordFalsePositive();
        RecordingSlotCacheService slotCacheService = new RecordingSlotCacheService(() ->
                manager.expandAndRebuild(() -> saturatedSlots));
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
        manager.rebuild(() -> List.of(slot("SLOT_1"), slot("SLOT_2"), slot("SLOT_3")));
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
        bloom.setExpansionFactor(2D);
        bloom.setExpansionCooldown(Duration.ZERO);
        bloom.setMaxExpectedInsertions(100L);
        return properties;
    }

    private static SlotEntity slot(String slotCode) {
        SlotEntity slot = new SlotEntity();
        slot.setSlotCode(slotCode);
        return slot;
    }

    private static class RecordingSlotCacheService implements SlotBloomMaintenance {

        private final Supplier<Optional<List<SlotEntity>>> expansionAction;
        private int expansionCount;

        private RecordingSlotCacheService(
                Supplier<Optional<List<SlotEntity>>> expansionAction) {
            this.expansionAction = expansionAction;
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
