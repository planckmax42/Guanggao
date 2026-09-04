package com.example.adplatform.infra.bloomfilter.delivery.slot;

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

import static com.example.adplatform.infra.bloomfilter.delivery.slot.BloomRebuildResult.RebuildStatus.LOCK_BUSY;
import static com.example.adplatform.infra.bloomfilter.delivery.slot.BloomRebuildResult.RebuildStatus.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlotBloomOperationsServiceTests {

    @Test
    void shouldLoadMysqlSnapshotAndRemoveDisabledCodeAfterRebuild() {
        SlotMapper slotMapper = mock(SlotMapper.class);
        when(slotMapper.selectEnabledSlotCodes())
                .thenReturn(slots("HOME_BANNER", "OLD_SLOT"))
                .thenReturn(slots("HOME_BANNER"));
        SlotBloomOperationsService service = createService(slotMapper);

        Optional<List<String>> firstSnapshot = service.regularRebuild().slotNames();
        assertEquals(2, firstSnapshot.orElseThrow().size());
        assertFalse(service.definiteNotContain("OLD_SLOT"));

        assertEquals(SUCCESS, service.regularRebuild().rebuildStatus());

        assertTrue(service.definiteNotContain("OLD_SLOT"));
        assertFalse(service.definiteNotContain("HOME_BANNER"));
        verify(slotMapper, times(2)).selectEnabledSlotCodes();
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
        SlotBloomOperationsService service = createService(slotMapper);
        service.regularRebuild();

        CompletableFuture<BloomRebuildResult> rebuildFuture =
                CompletableFuture.supplyAsync(service::regularRebuild);

        assertTrue(rebuildStarted.await(1, TimeUnit.SECONDS));
        service.addSlotFilter("NEW_SLOT");
        continueRebuild.countDown();

        assertEquals(SUCCESS, rebuildFuture.get(1, TimeUnit.SECONDS).rebuildStatus());
        assertFalse(service.definiteNotContain("NEW_SLOT"));
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
        SlotBloomOperationsService service = createService(slotMapper);

        CompletableFuture<BloomRebuildResult> rebuildFuture =
                CompletableFuture.supplyAsync(service::regularRebuild);

        assertTrue(rebuildStarted.await(1, TimeUnit.SECONDS));
        assertEquals(LOCK_BUSY, service.regularRebuild().rebuildStatus());
        continueRebuild.countDown();

        assertEquals(SUCCESS, rebuildFuture.get(1, TimeUnit.SECONDS).rebuildStatus());
        verify(slotMapper).selectEnabledSlotCodes();
    }

    @Test
    void shouldKeepActiveFilterAndCountersWhenMysqlQueryFails() {
        SlotMapper slotMapper = mock(SlotMapper.class);
        when(slotMapper.selectEnabledSlotCodes()).thenReturn(slots("HOME_BANNER"));
        SlotBloomOperationsService service = createService(slotMapper);
        assertEquals(SUCCESS, service.regularRebuild().rebuildStatus());
        service.recordDefiniteNotContain();
        service.recordFalsePositive();
        BloomSnapshot bloomSnapshotBeforeFailure = service.getBloomSnapshot();
        doThrow(new IllegalStateException("mysql unavailable"))
                .when(slotMapper).selectEnabledSlotCodes();

        assertThrows(IllegalStateException.class, service::regularRebuild);

        assertEquals(bloomSnapshotBeforeFailure.currentCapacity(), service.getBloomSnapshot().currentCapacity());
        assertEquals(bloomSnapshotBeforeFailure.totalCount(), service.getBloomSnapshot().totalCount());
        assertEquals(
                bloomSnapshotBeforeFailure.actualFalsePositiveRate(),
                service.getBloomSnapshot().actualFalsePositiveRate());
        assertFalse(service.definiteNotContain("HOME_BANNER"));
        assertTrue(service.definiteNotContain("UNKNOWN_SLOT"));
    }

    @Test
    void shouldExpandCapacityAndResetMetrics() {
        SlotMapper slotMapper = mock(SlotMapper.class);
        when(slotMapper.selectEnabledSlotCodes())
                .thenReturn(slots("HOME_BANNER", "OLD_SLOT"))
                .thenReturn(slots("HOME_BANNER", "NEW_SLOT"));
        BloomProperties properties = properties();
        properties.setInitialCapacity(100);
        SlotBloomOperationsService service =
                new BloomServiceImpl(
                        slotMapper, properties, new SimpleMeterRegistry());
        service.regularRebuild();
        service.recordDefiniteNotContain();
        service.recordFalsePositive();

        assertEquals(200L, service.expandRebuild().newCapacity());

        assertEquals(200L, service.getBloomSnapshot().currentCapacity());
        assertEquals(0L, service.getBloomSnapshot().totalCount());
        assertFalse(service.definiteNotContain("NEW_SLOT"));
        assertTrue(service.definiteNotContain("OLD_SLOT"));
    }

    @Test
    void shouldNotQueryMysqlOrResetMetricsAtExpansionLimit() {
        SlotMapper slotMapper = mock(SlotMapper.class);
        when(slotMapper.selectEnabledSlotCodes()).thenReturn(slots("HOME_BANNER"));
        BloomProperties properties = properties();
        properties.setInitialCapacity(100);
        properties.setMaxCapacity(100L);
        SlotBloomOperationsService service =
                new BloomServiceImpl(
                        slotMapper, properties, new SimpleMeterRegistry());
        assertEquals(SUCCESS, service.regularRebuild().rebuildStatus());
        service.recordDefiniteNotContain();
        service.recordFalsePositive();

        assertEquals(100L, service.expandRebuild().newCapacity());

        assertEquals(100L, service.getBloomSnapshot().currentCapacity());
        assertEquals(2L, service.getBloomSnapshot().totalCount());
        verify(slotMapper).selectEnabledSlotCodes();
    }

    @Test
    void shouldPublishEmptySnapshotAsSuccessfulFilter() {
        SlotMapper slotMapper = mock(SlotMapper.class);
        when(slotMapper.selectEnabledSlotCodes()).thenReturn(List.of());
        SlotBloomOperationsService service = createService(slotMapper);

        Optional<List<String>> result = service.regularRebuild().slotNames();

        assertTrue(result.isPresent());
        assertTrue(result.orElseThrow().isEmpty());
        assertTrue(service.definiteNotContain("UNKNOWN_SLOT"));
    }

    @Test
    void shouldCalculateActualFalsePositiveRateFromAbsentRequests() {
        SlotBloomOperationsService service = createService(mock(SlotMapper.class));
        for (int index = 0; index < 990; index++) {
            service.recordDefiniteNotContain();
        }
        for (int index = 0; index < 10; index++) {
            service.recordFalsePositive();
        }

        BloomSnapshot snapshot = service.getBloomSnapshot();

        assertEquals(1_000L, snapshot.totalCount());
        assertEquals(0.01D, snapshot.actualFalsePositiveRate(), 0.000001D);
    }

    @Test
    void shouldExposeOnlyServiceContract() {
        SlotBloomOperationsService service = createService(mock(SlotMapper.class));

        assertInstanceOf(BloomServiceImpl.class, service);
        Set<String> publicMethods = Arrays.stream(SlotBloomOperationsService.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(method -> method.getName())
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "definiteNotContain",
                "addSlotFilter",
                "recordDefiniteNotContain",
                "recordFalsePositive",
                "regularRebuild",
                "expandRebuild",
                "getBloomSnapshot"), publicMethods);
    }

    private SlotBloomOperationsService createService(SlotMapper slotMapper) {
        return new BloomServiceImpl(
                slotMapper, properties(), new SimpleMeterRegistry());
    }

    private BloomProperties properties() {
        BloomProperties properties = new BloomProperties();
        properties.setInitialCapacity(100);
        properties.setFalsePositiveProbability(0.000001D);
        properties.setExpansionFactor(2D);
        properties.setMaxCapacity(1_000L);
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
