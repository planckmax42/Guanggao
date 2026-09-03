package com.example.adplatform.infra.redis.tracking.materialMetadata;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialMetadataRedisLockTests {

    @Test
    void shouldTimeOutReadLockWhileWriterOwnsStripe() throws Exception {
        MaterialMetadataRedisLock manager = manager(1_024, Duration.ofMillis(30));

        try (MaterialMetadataRedisLock.LockHandle ignored =
                     manager.acquireForWrite(List.of("mat_10"))) {
            CompletableFuture<Optional<MaterialMetadataRedisLock.LockHandle>> attempt =
                    CompletableFuture.supplyAsync(() -> manager.tryAcquireForRead("mat_10"));

            assertTrue(attempt.get(1, TimeUnit.SECONDS).isEmpty());
        }
    }

    @Test
    void shouldRestoreInterruptStatusWhenReadWaitIsInterrupted() throws Exception {
        MaterialMetadataRedisLock manager = manager(1_024, Duration.ofSeconds(1));

        try (MaterialMetadataRedisLock.LockHandle ignored =
                     manager.acquireForWrite(List.of("mat_10"))) {
            CompletableFuture<Boolean> interrupted = new CompletableFuture<>();
            Thread thread = new Thread(() -> {
                Thread.currentThread().interrupt();
                Optional<MaterialMetadataRedisLock.LockHandle> handle =
                        manager.tryAcquireForRead("mat_10");
                handle.ifPresent(MaterialMetadataRedisLock.LockHandle::close);
                interrupted.complete(Thread.currentThread().isInterrupted());
            });
            thread.start();

            assertTrue(interrupted.get(1, TimeUnit.SECONDS));
            assertFalse(thread.isAlive());
        }
    }

    @Test
    void shouldAcquireOverlappingBatchesInAnyInputOrderWithoutDeadlock() throws Exception {
        MaterialMetadataRedisLock manager = manager(2, Duration.ofMillis(100));
        CyclicBarrier start = new CyclicBarrier(2);

        CompletableFuture<Void> ascending = CompletableFuture.runAsync(
                () -> repeatedlyAcquire(manager, start, List.of("mat_0", "mat_1")));
        CompletableFuture<Void> descending = CompletableFuture.runAsync(
                () -> repeatedlyAcquire(manager, start, List.of("mat_1", "mat_0")));

        CompletableFuture.allOf(ascending, descending).get(2, TimeUnit.SECONDS);
    }

    private void repeatedlyAcquire(
            MaterialMetadataRedisLock manager,
            CyclicBarrier start,
            List<String> materialIds) {
        try {
            start.await(1, TimeUnit.SECONDS);
            for (int index = 0; index < 100; index++) {
                try (MaterialMetadataRedisLock.LockHandle ignored =
                             manager.acquireForWrite(materialIds)) {
                    Thread.yield();
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private MaterialMetadataRedisLock manager(int stripes, Duration timeout) {
        MaterialMetadataRedisProperties properties = new MaterialMetadataRedisProperties();
        properties.getLock().setStripes(stripes);
        properties.getLock().setReadWaitTimeout(timeout);
        return new MaterialMetadataRedisLock(properties);
    }
}
