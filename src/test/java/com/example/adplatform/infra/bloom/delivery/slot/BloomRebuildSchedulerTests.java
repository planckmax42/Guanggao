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

class BloomRebuildSchedulerTests {

    @Test
    void shouldExpandWhenActualAndExpectedRatesReachThreshold() {
        BloomProperties properties = createProperties();
        List<String> saturatedSlots = IntStream.range(0, 100)
                .mapToObj(index -> "SLOT_" + index)
                .toList();
        SlotMapper slotMapper = mock(SlotMapper.class);
        when(slotMapper.selectEnabledSlotCodes()).thenReturn(saturatedSlots);
        SlotBloomService service =
                new BloomServiceImpl(
                        slotMapper, properties, new SimpleMeterRegistry());
        service.regularRebuild();
        service.recordDefiniteNotContain();
        service.recordFalsePositive();
        BloomRebuildScheduler bloomRebuildScheduler = new BloomRebuildScheduler(
                service, properties);

        bloomRebuildScheduler.expandRebuildIfNeed();

        assertEquals(2L, service.getBloomFilterSnapshot().currentCapacity());
    }

    @Test
    void shouldNotExpandWhenActualFalsePositiveRateIsBelowThreshold() {
        BloomProperties properties = createProperties();
        SlotMapper slotMapper = mock(SlotMapper.class);
        when(slotMapper.selectEnabledSlotCodes()).thenReturn(List.of(
                "SLOT_1", "SLOT_2", "SLOT_3"));
        SlotBloomService service =
                new BloomServiceImpl(
                        slotMapper, properties, new SimpleMeterRegistry());
        service.regularRebuild();
        service.recordDefiniteNotContain();
        BloomRebuildScheduler bloomRebuildScheduler = new BloomRebuildScheduler(
                service, properties);

        bloomRebuildScheduler.expandRebuildIfNeed();

        assertEquals(1L, service.getBloomFilterSnapshot().currentCapacity());
    }

    @Test
    void shouldExposeGaugeWhenExpansionReachesMaximumCapacity() {
        BloomProperties properties = createProperties();
        properties.setMaxCapacity(2L);
        List<String> saturatedSlots = IntStream.range(0, 100)
                .mapToObj(index -> "SLOT_" + index)
                .toList();
        SlotMapper slotMapper = mock(SlotMapper.class);
        when(slotMapper.selectEnabledSlotCodes()).thenReturn(saturatedSlots);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SlotBloomService service =
                new BloomServiceImpl(
                        slotMapper, properties, meterRegistry);
        service.regularRebuild();
        service.recordDefiniteNotContain();
        service.recordFalsePositive();
        BloomRebuildScheduler bloomRebuildScheduler = new BloomRebuildScheduler(
                service, properties);

        bloomRebuildScheduler.expandRebuildIfNeed();

        assertEquals(2L, service.getBloomFilterSnapshot().currentCapacity());
        assertEquals(
                1D,
                meterRegistry.get("slot.bloom.capacity.exhausted").gauge().value());
    }

    private BloomProperties createProperties() {
        BloomProperties properties = new BloomProperties();
        properties.setInitialCapacity(1);
        properties.setFalsePositiveProbability(0.01D);
        properties.setMinimumAbsentSamples(2L);
        properties.setExpansionFactor(2D);
        properties.setExpansionCooldown(Duration.ZERO);
        properties.setMaxCapacity(100L);
        return properties;
    }
}
