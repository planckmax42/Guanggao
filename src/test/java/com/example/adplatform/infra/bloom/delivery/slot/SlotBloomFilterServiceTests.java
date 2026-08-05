package com.example.adplatform.infra.bloom.delivery.slot;

import com.example.adplatform.admin.mapper.SlotMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlotBloomFilterServiceTests {

    @Test
    void shouldLoadMysqlSnapshotAndRemoveDisabledCodeAfterRebuild() {
        SlotMapper slotMapper = mock(SlotMapper.class);
        when(slotMapper.selectEnabledSlotCodes())
                .thenReturn(slots("HOME_BANNER", "OLD_SLOT"))
                .thenReturn(slots("HOME_BANNER"));
        SlotBloomFilterTracker tracker = mock(SlotBloomFilterTracker.class);
        SlotBloomFilterService service = createService(slotMapper, tracker);

        Optional<List<String>> firstSnapshot = service.regularRebuild();
        assertEquals(2, firstSnapshot.orElseThrow().size());
        assertFalse(service.definitelyNotContains("OLD_SLOT"));

        assertTrue(service.regularRebuild().isPresent());

        assertTrue(service.definitelyNotContains("OLD_SLOT"));
        assertFalse(service.definitelyNotContains("HOME_BANNER"));
        verify(slotMapper, times(2)).selectEnabledSlotCodes();
        verify(tracker, times(2)).reset();
    }

    @Test
    void shouldWriteNewCodeToStandbyFilterDuringRebuild() throws Exception {
        SlotMapper slotMapper = mock(SlotMapper.class);
        CountDownLatch rebuildStarted = new CountDownLatch(1);
        CountDownLatch continueRebuild = new CountDownLatch(1);
        when(slotMapper.selectEnabledSlotCodes())
                .thenReturn(slots("HOME_BANNER"))
                .thenAnswer(invocation -> {
                    rebuildStarted.countDown();
                    await(continueRebuild);
                    return slots("HOME_BANNER");
                });
        SlotBloomFilterService service = createService(slotMapper, mock(SlotBloomFilterTracker.class));
        service.regularRebuild();

        CompletableFuture<Optional<List<String>>> rebuildFuture =
                CompletableFuture.supplyAsync(service::regularRebuild);

        assertTrue(rebuildStarted.await(1, TimeUnit.SECONDS));
        service.addSlotBloomFilter("NEW_SLOT");
        continueRebuild.countDown();

        assertTrue(rebuildFuture.get(1, TimeUnit.SECONDS).isPresent());
        assertFalse(service.definitelyNotContains("NEW_SLOT"));
    }

    @Test
    void shouldRejectConcurrentRebuild() throws Exception {
        SlotMapper slotMapper = mock(SlotMapper.class);
        CountDownLatch rebuildStarted = new CountDownLatch(1);
        CountDownLatch continueRebuild = new CountDownLatch(1);
        when(slotMapper.selectEnabledSlotCodes()).thenAnswer(invocation -> {
            rebuildStarted.countDown();
            await(continueRebuild);
            return slots("HOME_BANNER");
        });
        SlotBloomFilterTracker tracker = mock(SlotBloomFilterTracker.class);
        SlotBloomFilterService service = createService(slotMapper, tracker);

        CompletableFuture<Optional<List<String>>> rebuildFuture =
                CompletableFuture.supplyAsync(service::regularRebuild);

        assertTrue(rebuildStarted.await(1, TimeUnit.SECONDS));
        assertTrue(service.regularRebuild().isEmpty());
        continueRebuild.countDown();

        assertTrue(rebuildFuture.get(1, TimeUnit.SECONDS).isPresent());
        verify(slotMapper).selectEnabledSlotCodes();
        verify(tracker).reset();
    }

    @Test
    void shouldKeepActiveFilterAndMetricsWhenMysqlQueryFails() {
        SlotMapper slotMapper = mock(SlotMapper.class);
        when(slotMapper.selectEnabledSlotCodes()).thenReturn(slots("HOME_BANNER"));
        SlotBloomFilterTracker tracker = mock(SlotBloomFilterTracker.class);
        SlotBloomFilterService service = createService(slotMapper, tracker);
        assertTrue(service.regularRebuild().isPresent());
        SlotBloomFilterService.BloomFilterSnapshot bloomFilterSnapshotBeforeFailure = service.GetBloomFilterSnapshot();
        doThrow(new IllegalStateException("mysql unavailable"))
                .when(slotMapper).selectEnabledSlotCodes();

        assertTrue(service.regularRebuild().isEmpty());

        assertEquals(bloomFilterSnapshotBeforeFailure.currentCapacity(), service.GetBloomFilterSnapshot().currentCapacity());
        assertFalse(service.definitelyNotContains("HOME_BANNER"));
        assertTrue(service.definitelyNotContains("UNKNOWN_SLOT"));
        verify(tracker).reset();
    }

    @Test
    void shouldExpandCapacityAndResetMetrics() {
        SlotMapper slotMapper = mock(SlotMapper.class);
        when(slotMapper.selectEnabledSlotCodes())
                .thenReturn(slots("HOME_BANNER", "OLD_SLOT"))
                .thenReturn(slots("HOME_BANNER", "NEW_SLOT"));
        SlotBloomFilterTracker tracker = mock(SlotBloomFilterTracker.class);
        SlotBloomFilterProperties properties = properties();
        properties.setInitialCapacity(100);
        SlotBloomFilterService service =
                new SlotBloomFilterServiceImpl(
                        slotMapper, properties, tracker, new SimpleMeterRegistry());
        service.regularRebuild();

        assertTrue(service.expandRebuild());

        assertEquals(200L, service.GetBloomFilterSnapshot().currentCapacity());
        assertFalse(service.definitelyNotContains("NEW_SLOT"));
        assertTrue(service.definitelyNotContains("OLD_SLOT"));
        verify(tracker, times(2)).reset();
    }

    @Test
    void shouldNotQueryMysqlOrResetMetricsAtExpansionLimit() {
        SlotMapper slotMapper = mock(SlotMapper.class);
        when(slotMapper.selectEnabledSlotCodes()).thenReturn(slots("HOME_BANNER"));
        SlotBloomFilterTracker tracker = mock(SlotBloomFilterTracker.class);
        SlotBloomFilterProperties properties = properties();
        properties.setInitialCapacity(100);
        properties.setMaxExpectedCapacity(100L);
        SlotBloomFilterService service =
                new SlotBloomFilterServiceImpl(
                        slotMapper, properties, tracker, new SimpleMeterRegistry());
        assertTrue(service.regularRebuild().isPresent());

        assertFalse(service.expandRebuild());

        assertEquals(100L, service.GetBloomFilterSnapshot().currentCapacity());
        verify(slotMapper).selectEnabledSlotCodes();
        verify(tracker).reset();
    }

    @Test
    void shouldPublishEmptySnapshotAsSuccessfulFilter() {
        SlotMapper slotMapper = mock(SlotMapper.class);
        when(slotMapper.selectEnabledSlotCodes()).thenReturn(List.of());
        SlotBloomFilterTracker tracker = mock(SlotBloomFilterTracker.class);
        SlotBloomFilterService service = createService(slotMapper, tracker);

        Optional<List<String>> result = service.regularRebuild();

        assertTrue(result.isPresent());
        assertTrue(result.orElseThrow().isEmpty());
        assertTrue(service.definitelyNotContains("UNKNOWN_SLOT"));
        verify(tracker).reset();
    }

    @Test
    void shouldExposeOnlyServiceContract() {
        SlotBloomFilterService service = createService(
                mock(SlotMapper.class),
                mock(SlotBloomFilterTracker.class));

        assertInstanceOf(SlotBloomFilterServiceImpl.class, service);
        Set<String> publicMethods = Arrays.stream(SlotBloomFilterService.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "definitelyNotContains",
                "addSlotBloomFilter",
                "regularRebuild",
                "expandRebuild",
                "GetSlotBloomFilterSnapshot"), publicMethods);
    }

    private SlotBloomFilterService createService(
            SlotMapper slotMapper,
            SlotBloomFilterTracker tracker) {
        return new SlotBloomFilterServiceImpl(
                slotMapper, properties(), tracker, new SimpleMeterRegistry());
    }

    private SlotBloomFilterProperties properties() {
        SlotBloomFilterProperties properties = new SlotBloomFilterProperties();
        properties.setInitialCapacity(100);
        properties.setFalsePositiveProbability(0.000001D);
        properties.setExpansionFactor(2D);
        properties.setMaxExpectedCapacity(1_000L);
        return properties;
    }

    private List<String> slots(String... slotCodes) {
        return List.of(slotCodes);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) {
                throw new IllegalStateException("等待重建继续执行超时");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待重建继续执行时被中断", ex);
        }
    }
}
