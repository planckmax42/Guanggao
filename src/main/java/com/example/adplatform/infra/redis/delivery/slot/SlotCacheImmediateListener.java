package com.example.adplatform.infra.redis.delivery.slot;

import com.example.adplatform.admin.event.SlotCacheImmediateEvent;
import com.example.adplatform.admin.port.slot.SlotCachePort;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 在业务事务提交后执行一次 Redis 同步，失败交给已持久化的 Outbox 补偿。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlotCacheImmediateListener {

    private final SlotCachePort slotCachePort;
    private final SlotCacheLockManager lockManager;
    private final MeterRegistry meterRegistry;
    private final SlotCacheFailureLogLimiter failureLogLimiter;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(SlotCacheImmediateEvent event) {
        try (SlotCacheLockManager.LockHandle ignored = lockManager.acquireForWrite(event.slotCode())) {
            if (event.action() == SlotCacheImmediateEvent.Action.WRITE) {
                slotCachePort.writeSlotToRedis(event.slotCode(), event.slotId());
            } else {
                slotCachePort.evictSlotCodeFromRedis(event.slotCode());
            }
            meterRegistry.counter("ad.slot.cache.sync", "stage", "immediate", "result", "success")
                    .increment();
        } catch (SlotCacheAccessException ex) {
            meterRegistry.counter("ad.slot.cache.sync", "stage", "immediate", "result", "failure")
                    .increment();
            if (failureLogLimiter.shouldLog("immediate_" + ex.getOperation())) {
                log.error("广告位事务已提交，即时 Redis 同步失败，将由 Outbox 补偿，operation={}，slotCode={}",
                        ex.getOperation(), ex.getSlotCode(), ex);
            }
        }
    }
}
