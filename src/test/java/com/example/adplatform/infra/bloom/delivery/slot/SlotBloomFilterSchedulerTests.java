package com.example.adplatform.infra.bloom.delivery.slot;

import com.example.adplatform.admin.entity.SlotEntity;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlotBloomFilterSchedulerTests {

    @Test
    void shouldExpandWhenActualAndExpectedRatesReachThreshold() {
        SlotBloomFilterProperties properties = createProperties();
        SlotBloomFilterManager manager = new SlotBloomFilterManager(properties);
        List<SlotEntity> saturatedSlots = IntStream.range(0, 100)
                .mapToObj(index -> slot("SLOT_" + index))
                .toList();
        manager.rebuild(() -> saturatedSlots);
        SlotBloomFilterTracker metrics = new SlotBloomFilterTracker();
        metrics.recordDefiniteMiss();
        metrics.recordFalsePositive();
        RecordingSlotCacheService slotCacheService = new RecordingSlotCacheService(() ->
                manager.expandAndRebuild(() -> saturatedSlots));
        SlotBloomFilterScheduler slotBloomFilterScheduler = new SlotBloomFilterScheduler(
                metrics, manager, properties, slotCacheService);

        slotBloomFilterScheduler.checkAndExpand();

        assertEquals(1, slotCacheService.expansionCount);
        assertEquals(2L, manager.status().expectedInsertions());
    }

    @Test
    void shouldNotExpandBeforeEnoughSamplesAreCollected() {
        SlotBloomFilterProperties properties = createProperties();
        properties.setMinimumAbsentSamples(100L);
        SlotBloomFilterManager manager = new SlotBloomFilterManager(properties);
        manager.rebuild(() -> List.of(slot("SLOT_1"), slot("SLOT_2"), slot("SLOT_3")));
        SlotBloomFilterTracker metrics = new SlotBloomFilterTracker();
        metrics.recordFalsePositive();
        RecordingSlotCacheService slotCacheService = new RecordingSlotCacheService(() -> Optional.empty());
        SlotBloomFilterScheduler slotBloomFilterScheduler = new SlotBloomFilterScheduler(
                metrics, manager, properties, slotCacheService);

        slotBloomFilterScheduler.checkAndExpand();

        assertEquals(0, slotCacheService.expansionCount);
    }

    private SlotBloomFilterProperties createProperties() {
        SlotBloomFilterProperties properties = new SlotBloomFilterProperties();
        properties.setExpectedInsertions(1);
        properties.setFalsePositiveProbability(0.01D);
        properties.setMinimumAbsentSamples(2L);
        properties.setExpansionFactor(2D);
        properties.setExpansionCooldown(Duration.ZERO);
        properties.setMaxExpectedInsertions(100L);
        return properties;
    }

    private static SlotEntity slot(String slotCode) {
        SlotEntity slot = new SlotEntity();
        slot.setSlotCode(slotCode);
        return slot;
    }

    private static class RecordingSlotCacheService implements SlotBloomFilterRebuilder {

        private final Supplier<Optional<List<SlotEntity>>> expansionAction;
        private int expansionCount;

        private RecordingSlotCacheService(
                Supplier<Optional<List<SlotEntity>>> expansionAction) {
            this.expansionAction = expansionAction;
        }

        @Override
        public boolean rebuild() {
            return false;
        }

        @Override
        public boolean expandAndRebuild() {
            expansionCount++;
            return expansionAction.get().isPresent();
        }
    }
}
