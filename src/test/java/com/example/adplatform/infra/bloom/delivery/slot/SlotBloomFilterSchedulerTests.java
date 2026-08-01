package com.example.adplatform.infra.bloom.delivery.slot;

import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SlotBloomFilterSchedulerTests {

    @Test
    void shouldExpandWhenActualAndExpectedRatesReachThreshold() {
        SlotBloomFilterProperties properties = createProperties();
        List<SlotEntity> saturatedSlots = IntStream.range(0, 100)
                .mapToObj(index -> slot("SLOT_" + index))
                .toList();
        SlotMapper slotMapper = mock(SlotMapper.class);
        when(slotMapper.selectList(any())).thenReturn(saturatedSlots);
        SlotBloomFilterTracker metrics = new SlotBloomFilterTracker();
        SlotBloomFilterService service =
                new SlotBloomFilterServiceImpl(slotMapper, properties, metrics);
        service.rebuild();
        metrics.recordDefiniteMiss();
        metrics.recordFalsePositive();
        SlotBloomFilterScheduler slotBloomFilterScheduler = new SlotBloomFilterScheduler(
                metrics, service, properties);

        slotBloomFilterScheduler.checkAndExpand();

        assertEquals(2L, service.status().expectedInsertions());
    }

    @Test
    void shouldNotExpandBeforeEnoughSamplesAreCollected() {
        SlotBloomFilterProperties properties = createProperties();
        properties.setMinimumAbsentSamples(100L);
        SlotMapper slotMapper = mock(SlotMapper.class);
        when(slotMapper.selectList(any())).thenReturn(List.of(
                slot("SLOT_1"), slot("SLOT_2"), slot("SLOT_3")));
        SlotBloomFilterTracker metrics = new SlotBloomFilterTracker();
        SlotBloomFilterService service =
                new SlotBloomFilterServiceImpl(slotMapper, properties, metrics);
        service.rebuild();
        metrics.recordFalsePositive();
        SlotBloomFilterScheduler slotBloomFilterScheduler = new SlotBloomFilterScheduler(
                metrics, service, properties);

        slotBloomFilterScheduler.checkAndExpand();

        assertEquals(1L, service.status().expectedInsertions());
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
}
