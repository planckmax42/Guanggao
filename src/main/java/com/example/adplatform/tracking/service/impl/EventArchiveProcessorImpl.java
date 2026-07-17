package com.example.adplatform.tracking.service.impl;

import com.example.adplatform.tracking.converter.EventConverter;
import com.example.adplatform.tracking.entity.ChargeRecordEntity;
import com.example.adplatform.tracking.entity.ChargeStatus;
import com.example.adplatform.tracking.entity.EventEntity;
import com.example.adplatform.tracking.mapper.ChargeRecordMapper;
import com.example.adplatform.tracking.mapper.EventMapper;
import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.tracking.service.EventArchiveProcessor;
import com.example.adplatform.tracking.service.EventContextResolver;
import com.example.adplatform.tracking.service.EventProcessingContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 事件归档链路：只维护 event 明细及计费结果投影。 */
@RequiredArgsConstructor
@Service
public class EventArchiveProcessorImpl implements EventArchiveProcessor {

    private final EventContextResolver contextResolver;
    private final EventConverter eventConverter;
    private final EventMapper eventMapper;
    private final ChargeRecordMapper chargeRecordMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(EventMessage message) {
        EventProcessingContext context = contextResolver.resolve(message);
        EventEntity event = eventConverter.toEntity(
                message,
                context.eventType(),
                context.material(),
                context.billingType(),
                false,
                0L,
                context.eventTime());
        try {
            eventMapper.insert(event);
        } catch (DuplicateKeyException ignored) {
            // event_id 唯一键使 Kafka 重放保持幂等，仍继续同步可能晚到的计费投影。
        }
        synchronizeChargeProjection(message.eventId());
    }

    private void synchronizeChargeProjection(String eventId) {
        ChargeRecordEntity charge = chargeRecordMapper.selectByEventId(eventId);
        if (charge == null) {
            return;
        }
        boolean charged = ChargeStatus.SUCCESS.name().equals(charge.getChargeStatus());
        eventMapper.updateChargeResult(eventId, charged ? 1 : 0, charged ? charge.getAmount() : 0L);
    }
}
