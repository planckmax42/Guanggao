package com.example.adplatform.infra.redis.event;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** 使用固定数量的本地条带锁协调事件元数据回填与提交后失效。 */
@Component
public class EventMetadataCacheLockManager {

    private final ReentrantLock[] stripes;
    private final Duration readWaitTimeout;

    public EventMetadataCacheLockManager(EventMetadataCacheProperties properties) {
        EventMetadataCacheProperties.Lock config = properties.getLock();
        int stripeCount = Math.max(1, config.getStripes());
        this.stripes = new ReentrantLock[stripeCount];
        Arrays.setAll(this.stripes, ignored -> new ReentrantLock());
        this.readWaitTimeout = config.getReadWaitTimeout();
    }

    /** 读侧限时获取 materialId 对应的条带锁。 */
    public Optional<LockHandle> tryAcquireForRead(Long materialId) {
        ReentrantLock lock = stripes[stripeIndex(materialId)];
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

    /** 写侧按条带索引升序获取全部相关锁，避免批量失效产生死锁。 */
    public LockHandle acquireForWrite(Collection<Long> materialIds) {
        List<ReentrantLock> locks = materialIds.stream()
                .filter(Objects::nonNull)
                .mapToInt(this::stripeIndex)
                .distinct()
                .sorted()
                .mapToObj(index -> stripes[index])
                .toList();
        locks.forEach(ReentrantLock::lock);
        return new LockHandle(locks);
    }

    private int stripeIndex(Long materialId) {
        return Math.floorMod(Long.hashCode(materialId), stripes.length);
    }

    /** 反向释放一组已获取的条带锁，可用于 try-with-resources。 */
    public static final class LockHandle implements AutoCloseable {

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
