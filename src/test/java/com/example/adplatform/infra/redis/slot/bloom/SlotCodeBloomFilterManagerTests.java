package com.example.adplatform.infra.redis.slot.bloom;

import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.infra.redis.slot.SlotCacheProperties;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotCodeBloomFilterManagerTests {

    @Test
    void shouldRemoveDisabledCodeAfterRebuild() {
        SlotCodeBloomFilterManager manager = createManager();
        manager.rebuild(() -> slots("HOME_BANNER", "OLD_SLOT"));

        assertFalse(manager.definitelyNotContains("OLD_SLOT"));

        manager.rebuild(() -> slots("HOME_BANNER"));

        assertTrue(manager.definitelyNotContains("OLD_SLOT"));
        assertFalse(manager.definitelyNotContains("HOME_BANNER"));
    }

    @Test
    void shouldWriteNewCodeToStandbyFilterDuringRebuild() throws Exception {
        SlotCodeBloomFilterManager manager = createManager();
        manager.rebuild(() -> slots("HOME_BANNER"));
        CountDownLatch rebuildStarted = new CountDownLatch(1);
        CountDownLatch continueRebuild = new CountDownLatch(1);

        CompletableFuture<Void> rebuildFuture = CompletableFuture.runAsync(() -> manager.rebuild(() -> {
            rebuildStarted.countDown();
            await(continueRebuild);
            return slots("HOME_BANNER");
        }));

        assertTrue(rebuildStarted.await(1, TimeUnit.SECONDS));
        manager.put("NEW_SLOT");
        continueRebuild.countDown();
        rebuildFuture.get(1, TimeUnit.SECONDS);

        assertFalse(manager.definitelyNotContains("NEW_SLOT"));
    }

    @Test
    void shouldRejectConcurrentRebuild() throws Exception {
        SlotCodeBloomFilterManager manager = createManager();
        CountDownLatch rebuildStarted = new CountDownLatch(1);
        CountDownLatch continueRebuild = new CountDownLatch(1);

        CompletableFuture<Void> rebuildFuture = CompletableFuture.runAsync(() -> manager.rebuild(() -> {
            rebuildStarted.countDown();
            await(continueRebuild);
            return slots("HOME_BANNER");
        }));

        assertTrue(rebuildStarted.await(1, TimeUnit.SECONDS));
        try {
            assertTrue(manager.rebuild(() -> slots("SHOULD_NOT_LOAD")).isEmpty());
        } finally {
            continueRebuild.countDown();
        }
        rebuildFuture.get(1, TimeUnit.SECONDS);

        assertFalse(manager.definitelyNotContains("HOME_BANNER"));
        assertTrue(manager.definitelyNotContains("SHOULD_NOT_LOAD"));
    }

    @Test
    void shouldKeepActiveFilterWhenRebuildFails() {
        SlotCodeBloomFilterManager manager = createManager();
        manager.rebuild(() -> slots("HOME_BANNER"));

        assertThrows(IllegalStateException.class, () -> manager.rebuild(() -> {
            throw new IllegalStateException("模拟 MySQL 查询失败");
        }));

        assertFalse(manager.definitelyNotContains("HOME_BANNER"));
        assertTrue(manager.definitelyNotContains("UNKNOWN_SLOT"));
    }

    @Test
    void shouldDoubleCapacityAndRebuildWhenExpanding() {
        SlotCacheProperties properties = new SlotCacheProperties();
        properties.getBloom().setExpectedInsertions(100);
        properties.getBloom().setFalsePositiveProbability(0.01D);
        properties.getBloom().setExpansionFactor(2D);
        properties.getBloom().setMaxExpectedInsertions(1_000L);
        SlotCodeBloomFilterManager manager = new SlotCodeBloomFilterManager(properties);
        manager.rebuild(() -> slots("HOME_BANNER", "OLD_SLOT"));

        manager.expandAndRebuild(() -> slots("HOME_BANNER", "NEW_SLOT"));

        assertEquals(200L, manager.status().expectedInsertions());
        assertFalse(manager.definitelyNotContains("HOME_BANNER"));
        assertFalse(manager.definitelyNotContains("NEW_SLOT"));
        assertTrue(manager.definitelyNotContains("OLD_SLOT"));
    }

    private SlotCodeBloomFilterManager createManager() {
        SlotCacheProperties properties = new SlotCacheProperties();
        properties.getBloom().setExpectedInsertions(100);
        properties.getBloom().setFalsePositiveProbability(0.000001D);
        return new SlotCodeBloomFilterManager(properties);
    }

    private List<SlotEntity> slots(String... slotCodes) {
        return Arrays.stream(slotCodes)
                .map(this::slot)
                .toList();
    }

    private SlotEntity slot(String slotCode) {
        SlotEntity slot = new SlotEntity();
        slot.setSlotCode(slotCode);
        return slot;
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
