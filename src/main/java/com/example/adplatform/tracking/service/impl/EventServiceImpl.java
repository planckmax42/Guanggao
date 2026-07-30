package com.example.adplatform.tracking.service.impl;

import com.example.adplatform.tracking.converter.EventConverter;
import com.example.adplatform.tracking.request.EventRequest;
import com.example.adplatform.tracking.entity.EventType;
import com.example.adplatform.tracking.port.EventPublisher;
import com.example.adplatform.tracking.service.EventService;
import com.example.adplatform.tracking.response.EventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletionStage;

@RequiredArgsConstructor
@Service
public class EventServiceImpl implements EventService {

    private final EventPublisher eventPublisher;
    private final EventConverter eventConverter;

    @Override
    public CompletionStage<EventResponse> collect(EventRequest request) {
        EventType eventType = EventType.parse(request.eventType());
        return eventPublisher.publish(eventConverter.toMessage(request, eventType))
                .thenApply(ignored -> new EventResponse(//仅回调轻量代码逻辑，若复杂处理逻辑则指定线程池执行
                        request.eventId(),
                        eventType.name(),
                        false,
                        null));
    }
}
