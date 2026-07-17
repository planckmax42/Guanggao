package com.example.adplatform.tracking.service.impl;

import com.example.adplatform.tracking.converter.EventConverter;
import com.example.adplatform.tracking.entity.EventEntity;
import com.example.adplatform.tracking.mapper.EventMapper;
import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.tracking.service.EventArchiveProcessor;
import com.example.adplatform.tracking.service.EventContextResolver;
import com.example.adplatform.tracking.service.EventProcessingContext;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 事件归档链路：只负责幂等写入 event 明细。 */
@RequiredArgsConstructor
@Service
public class EventArchiveProcessorImpl implements EventArchiveProcessor {

    private final EventContextResolver contextResolver;
    private final EventConverter eventConverter;
    private final EventMapper eventMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(EventMessage message) {
        EventProcessingContext context = contextResolver.resolve(message);
        EventEntity event = eventConverter.toEntity(message, context);
        try {
            eventMapper.insert(event);
        } catch (DuplicateKeyException ignored) {
            // event_id 唯一键使 Kafka 重放保持幂等。
        }
    }
}
