package com.example.adplatform.search.candidate.event;

import com.example.adplatform.search.candidate.service.DeliveryStopGuardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 在管理事务成功提交后更新 Redis 紧急停投集合。
 *
 * <p>使用 AFTER_COMMIT 可避免数据库回滚但 Redis 已改变的跨存储不一致。</p>
 */
@Component
@RequiredArgsConstructor
public class ConfigStopGuardListener {

    private final DeliveryStopGuardService stopGuardService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(ConfigStopGuardEvent event) {
        stopGuardService.mark(event.aggregateType(), event.aggregateId(), event.stopped());
    }
}
