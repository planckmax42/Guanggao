package com.example.adplatform.infra.redis.delivery.slot;

import com.example.adplatform.infra.redis.delivery.slot.SlotCacheLockManager.LockAcquireResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/** 反向释放一组已经获得的条带锁，可用于 try-with-resources。 */
public final class LockAcquireAttempt implements AutoCloseable {
    private final List<ReentrantLock> stripeLocks;
    private boolean released = false;
    private final LockAcquireResult lockAcquireResult;

    LockAcquireAttempt(List<ReentrantLock> stripeLocks, LockAcquireResult lockAcquireResult) {
        this.stripeLocks = new ArrayList<>(stripeLocks);
        this.lockAcquireResult = lockAcquireResult;
    }

    @Override
    public void close() {
        if (released) return;
        for (int index = stripeLocks.size() - 1; index >= 0; index--) {
            stripeLocks.get(index).unlock();
        }
        released = true;
    }

    public boolean isTimeOut() {
        return lockAcquireResult == LockAcquireResult.TIME_OUT;
    }

    public boolean isInterrupted() {
        return lockAcquireResult == LockAcquireResult.INTERRUPTED;
    }
}
