package com.example.adplatform.infra.redis.delivery.slot;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotCacheLockManagerTests {

    @Test
    void shouldTimeOutReadLockWhileWriterOwnsStripe() throws Exception {
        SlotCacheLockManager manager = new SlotCacheLockManager(properties(Duration.ofMillis(30)));

        try (LockAcquireAttempt ignored = manager.acquireForWrite("HOME_BANNER")) {
            CompletableFuture<Optional<LockAcquireAttempt>> attempt =
                    CompletableFuture.supplyAsync(() -> tryAcquire(manager, "HOME_BANNER"));

            assertTrue(attempt.get(1, TimeUnit.SECONDS).isEmpty());
        }
    }

    @Test
    void shouldRestoreInterruptStatusWhenReadWaitIsInterrupted() throws Exception {
        SlotCacheLockManager manager = new SlotCacheLockManager(properties(Duration.ofSeconds(1)));

        try (LockAcquireAttempt ignored = manager.acquireForWrite("HOME_BANNER")) {
            CompletableFuture<Boolean> interrupted = new CompletableFuture<>();
            Thread thread = new Thread(() -> {
                Thread.currentThread().interrupt();
                Optional<LockAcquireAttempt> handle = manager.tryAcquireForRead("HOME_BANNER");
                handle.ifPresent(LockAcquireAttempt::close);
                interrupted.complete(Thread.currentThread().isInterrupted());
            });
            thread.start();

            assertTrue(interrupted.get(1, TimeUnit.SECONDS));
            assertFalse(thread.isAlive());
        }
    }

    private Optional<LockAcquireAttempt> tryAcquire(
            SlotCacheLockManager manager,
            String slotCode) {
        return manager.tryAcquireForRead(slotCode);
    }

    private SlotCacheProperties properties(Duration timeout) {
        SlotCacheProperties properties = new SlotCacheProperties();
        properties.getLock().setStripes(1_024);
        properties.getLock().setReadWaitTimeout(timeout);
        return properties;
    }
}
