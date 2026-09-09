package com.example.adplatform.infra.redis.delivery.slot;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** 使用固定数量的本地条带锁协调 Slot 缓存回填与提交后刷新。 */
@Component
public class SlotCacheLockManager {

    private final ReentrantLock[] stripeLocks;
    private final Duration waitTimeout;

    public SlotCacheLockManager(SlotCacheProperties properties) {
        this.stripeLocks = new ReentrantLock[properties.getStripes()];
        Arrays.setAll(this.stripeLocks, ignored -> new ReentrantLock());
        this.waitTimeout = properties.getReadWaitTimeout();
    }

    /** 读侧限时获取单个编码对应的条带锁；中断时恢复中断标记并返回 empty。 */
    public Optional<LockAcquireAttempt> tryAcquireForRead(String slotCode) {
        ReentrantLock lock = stripeLocks[stripeIndex(slotCode)];
        try {
            if (!lock.tryLock(waitTimeout.toNanos(), TimeUnit.NANOSECONDS)) {
                return Optional.empty();
            }
            return Optional.of(new LockAcquireAttempt(List.of(lock), LockAcquireResult.SUCCESS));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
    public LockAcquireAttempt acquireStripLock (String slotCode){
        ReentrantLock reentrantLock = stripeLocks[stripeIndex(slotCode)];
        try {
            if (!reentrantLock.tryLock(waitTimeout.toNanos(),TimeUnit.NANOSECONDS))
                return new LockAcquireAttempt(List.of(),LockAcquireResult.TIME_OUT);
            return new LockAcquireAttempt(List.of(reentrantLock),LockAcquireResult.SUCCESS);
        } catch (InterruptedException e) {
            return new LockAcquireAttempt(List.of(),LockAcquireResult.INTERRUPTED);
        }
    }
    /** 写侧按条带索引升序获取全部相关锁，确保提交后的缓存刷新一定完成。 */
    public LockAcquireAttempt acquireForWrite(String... slotCodes) {
        List<ReentrantLock> locks = Arrays.stream(slotCodes)
                .filter(StringUtils::hasText)
                .mapToInt(this::stripeIndex)
                .distinct()
                .sorted()
                .mapToObj(index -> stripeLocks[index])
                .toList();
        locks.forEach(ReentrantLock::lock);
        return new LockAcquireAttempt(locks,LockAcquireResult.SUCCESS);
    }

    private int stripeIndex(String slotCode) {
        return Math.floorMod(slotCode.hashCode(), stripeLocks.length);
    }

    public enum LockAcquireResult {
        SUCCESS,
        INTERRUPTED,
        TIME_OUT}
}
