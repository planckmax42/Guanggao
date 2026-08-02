package com.example.adplatform.infra.bloom.delivery.slot;

import com.example.adplatform.admin.mapper.SlotMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SlotBloomFilterSchedulerTests {

    @Test
    void shouldExpandWhenActualAndExpectedRatesReachThreshold() {
        SlotBloomFilterProperties properties = createProperties();
        List<String> saturatedSlots = IntStream.range(0, 100)
                .mapToObj(index -> "SLOT_" + index)
                .toList();
        SlotMapper slotMapper = mock(SlotMapper.class);
        when(slotMapper.selectEnabledSlotCodes()).thenReturn(saturatedSlots);
        SlotBloomFilterTracker metrics = new SlotBloomFilterTracker();
        SlotBloomFilterService service =
                new SlotBloomFilterServiceImpl(
                        slotMapper, properties, metrics, new SimpleMeterRegistry());
        service.regularRebuild();
        metrics.recordDefiniteMiss();
        metrics.recordFalsePositive();
        SlotBloomFilterScheduler slotBloomFilterScheduler = new SlotBloomFilterScheduler(
                metrics, service, properties);

        slotBloomFilterScheduler.checkAndExpand();

        assertEquals(2L, service.GetSlotBloomFilterSnapshot().currentCapacity());
    }

    @Test
    void shouldNotExpandBeforeEnoughSamplesAreCollected() {
        SlotBloomFilterProperties properties = createProperties();
        properties.setMinimumAbsentSamples(100L);
        SlotMapper slotMapper = mock(SlotMapper.class);
        when(slotMapper.selectEnabledSlotCodes()).thenReturn(List.of(
                "SLOT_1", "SLOT_2", "SLOT_3"));
        SlotBloomFilterTracker metrics = new SlotBloomFilterTracker();
        SlotBloomFilterService service =
                new SlotBloomFilterServiceImpl(
                        slotMapper, properties, metrics, new SimpleMeterRegistry());
        service.regularRebuild();
        metrics.recordFalsePositive();
        SlotBloomFilterScheduler slotBloomFilterScheduler = new SlotBloomFilterScheduler(
                metrics, service, properties);

        slotBloomFilterScheduler.checkAndExpand();

        assertEquals(1L, service.GetSlotBloomFilterSnapshot().currentCapacity());
    }

    @Test
    void shouldExposeGaugeWhenExpansionReachesMaximumCapacity() {
        SlotBloomFilterProperties properties = createProperties();
        properties.setMaxExpectedCapacity(2L);
        List<String> saturatedSlots = IntStream.range(0, 100)
                .mapToObj(index -> "SLOT_" + index)
                .toList();
        SlotMapper slotMapper = mock(SlotMapper.class);
        when(slotMapper.selectEnabledSlotCodes()).thenReturn(saturatedSlots);
        SlotBloomFilterTracker metrics = new SlotBloomFilterTracker();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SlotBloomFilterService service =
                new SlotBloomFilterServiceImpl(slotMapper, properties, metrics, meterRegistry);
        service.regularRebuild();
        metrics.recordDefiniteMiss();
        metrics.recordFalsePositive();
        SlotBloomFilterScheduler slotBloomFilterScheduler = new SlotBloomFilterScheduler(
                metrics, service, properties);

        slotBloomFilterScheduler.checkAndExpand();

        assertEquals(2L, service.GetSlotBloomFilterSnapshot().currentCapacity());
        assertEquals(
                1D,
                meterRegistry.get("slot.bloom.capacity.exhausted").gauge().value());
    }

    private SlotBloomFilterProperties createProperties() {
        SlotBloomFilterProperties properties = new SlotBloomFilterProperties();
        properties.setInitialCapacity(1);
        properties.setFalsePositiveProbability(0.01D);
        properties.setMinimumAbsentSamples(2L);
        properties.setExpansionFactor(2D);
        properties.setExpansionCooldown(Duration.ZERO);
        properties.setMaxExpectedCapacity(100L);
        return properties;
    }
}
