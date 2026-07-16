package com.example.adplatform.search.candidate.event;

import com.example.adplatform.search.candidate.service.DeliveryStopGuardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ConfigStopGuardListener {

    private final DeliveryStopGuardService stopGuardService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(ConfigStopGuardEvent event) {
        stopGuardService.mark(event.aggregateType(), event.aggregateId(), event.stopped());
    }
}
