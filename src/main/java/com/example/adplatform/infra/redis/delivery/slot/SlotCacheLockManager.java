package com.example.adplatform.infra.redis.delivery.slot;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** 使用固定数量的本地条带锁协调 Slot 缓存回填与提交后刷新。 */
@Component
public class SlotCacheLockManager {

    private final ReentrantLock[] stripes;
    private final Duration readWaitTimeout;

    public SlotCacheLockManager(SlotCacheProperties properties) {
        SlotCacheProperties.Lock config = properties.getLock();
        int stripeCount = Math.max(1, config.getStripes());
        this.stripes = new ReentrantLock[stripeCount];
        Arrays.setAll(this.stripes, ignored -> new ReentrantLock());
        this.readWaitTimeout = config.getReadWaitTimeout();
    }

    /** 读侧限时获取单个编码对应的条带锁；中断时恢复中断标记并返回 empty。 */
    public Optional<LockHandle> tryAcquireForRead(String slotCode) {
        ReentrantLock lock = lockFor(slotCode);
        try {
            if (!lock.tryLock(readWaitTimeout.toNanos(), TimeUnit.NANOSECONDS)) {
                return Optional.empty();
            }
            return Optional.of(new LockHandle(List.of(lock)));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /** 写侧按条带索引升序获取全部相关锁，确保提交后的缓存刷新一定完成。 */
    public LockHandle acquireForWrite(String... slotCodes) {
        List<ReentrantLock> locks = Arrays.stream(slotCodes)
                .filter(StringUtils::hasText)
                .mapToInt(this::stripeIndex)
                .distinct()
                .sorted()
                .mapToObj(index -> stripes[index])
                .toList();
        locks.forEach(ReentrantLock::lock);
        return new LockHandle(locks);
    }

    private ReentrantLock lockFor(String slotCode) {
        return stripes[stripeIndex(slotCode)];
    }

    private int stripeIndex(String slotCode) {
        return Math.floorMod(slotCode.hashCode(), stripes.length);
    }

    /** 反向释放一组已经获得的条带锁，可用于 try-with-resources。 */
    public static final class LockHandle implements AutoCloseable {//继承AutoCloseable类实现离开try代码块自动释放锁

        private final List<ReentrantLock> locks;
        private boolean closed;

        private LockHandle(List<ReentrantLock> locks) {
            this.locks = new ArrayList<>(locks);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            for (int index = locks.size() - 1; index >= 0; index--) {
                locks.get(index).unlock();
            }
            closed = true;
        }
    }
}
