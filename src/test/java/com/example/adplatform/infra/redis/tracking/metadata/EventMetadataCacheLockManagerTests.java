package com.example.adplatform.infra.redis.tracking.metadata;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventMetadataCacheLockManagerTests {

    @Test
    void shouldTimeOutReadLockWhileWriterOwnsStripe() throws Exception {
        EventMetadataCacheLockManager manager = manager(1_024, Duration.ofMillis(30));

        try (EventMetadataCacheLockManager.LockHandle ignored =
                     manager.acquireForWrite(List.of(10L))) {
            CompletableFuture<Optional<EventMetadataCacheLockManager.LockHandle>> attempt =
                    CompletableFuture.supplyAsync(() -> manager.tryAcquireForRead(10L));

            assertTrue(attempt.get(1, TimeUnit.SECONDS).isEmpty());
        }
    }

    @Test
    void shouldRestoreInterruptStatusWhenReadWaitIsInterrupted() throws Exception {
        EventMetadataCacheLockManager manager = manager(1_024, Duration.ofSeconds(1));

        try (EventMetadataCacheLockManager.LockHandle ignored =
                     manager.acquireForWrite(List.of(10L))) {
            CompletableFuture<Boolean> interrupted = new CompletableFuture<>();
            Thread thread = new Thread(() -> {
                Thread.currentThread().interrupt();
                Optional<EventMetadataCacheLockManager.LockHandle> handle =
                        manager.tryAcquireForRead(10L);
                handle.ifPresent(EventMetadataCacheLockManager.LockHandle::close);
                interrupted.complete(Thread.currentThread().isInterrupted());
            });
            thread.start();

            assertTrue(interrupted.get(1, TimeUnit.SECONDS));
            assertFalse(thread.isAlive());
        }
    }

    @Test
    void shouldAcquireOverlappingBatchesInAnyInputOrderWithoutDeadlock() throws Exception {
        EventMetadataCacheLockManager manager = manager(2, Duration.ofMillis(100));
        CyclicBarrier start = new CyclicBarrier(2);

        CompletableFuture<Void> ascending = CompletableFuture.runAsync(
                () -> repeatedlyAcquire(manager, start, List.of(0L, 1L)));
        CompletableFuture<Void> descending = CompletableFuture.runAsync(
                () -> repeatedlyAcquire(manager, start, List.of(1L, 0L)));

        CompletableFuture.allOf(ascending, descending).get(2, TimeUnit.SECONDS);
    }

    private void repeatedlyAcquire(
            EventMetadataCacheLockManager manager,
            CyclicBarrier start,
            List<Long> materialIds) {
        try {
            start.await(1, TimeUnit.SECONDS);
            for (int index = 0; index < 100; index++) {
                try (EventMetadataCacheLockManager.LockHandle ignored =
                             manager.acquireForWrite(materialIds)) {
                    Thread.yield();
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private EventMetadataCacheLockManager manager(int stripes, Duration timeout) {
        EventMetadataCacheProperties properties = new EventMetadataCacheProperties();
        properties.getLock().setStripes(stripes);
        properties.getLock().setReadWaitTimeout(timeout);
        return new EventMetadataCacheLockManager(properties);
    }
}
