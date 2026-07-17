package com.example.adplatform.tracking.service.impl;

import com.example.adplatform.tracking.converter.EventConverter;
import com.example.adplatform.tracking.request.EventRequest;
import com.example.adplatform.tracking.entity.EventType;
import com.example.adplatform.tracking.port.EventPublisher;
import com.example.adplatform.tracking.service.EventService;
import com.example.adplatform.tracking.response.EventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class EventServiceImpl implements EventService {

    private final EventPublisher eventPublisher;
    private final EventConverter eventConverter;

    @Override
    public EventResponse collect(EventRequest request) {
        EventType eventType = EventType.parse(request.eventType());
        eventPublisher.publish(eventConverter.toMessage(request));

        return new EventResponse(
                request.eventId(),
                eventType.name(),
                false,
                false,
                0L,
                null);
    }
}
