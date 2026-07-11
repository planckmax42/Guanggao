package com.example.adplatform.tracking.service.impl;

import com.example.adplatform.tracking.dto.EventRequest;
import com.example.adplatform.tracking.entity.EventType;
import com.example.adplatform.tracking.producer.EventProducer;
import com.example.adplatform.tracking.service.EventService;
import com.example.adplatform.tracking.vo.EventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class EventServiceImpl implements EventService {

    private final EventProducer eventProducer;

    @Override
    public EventResponse collect(EventRequest request) {
        EventType eventType = EventType.parse(request.eventType());
        eventProducer.send(request);

        return new EventResponse(
                request.eventId(),
                eventType.name(),
                false,
                false,
                0L,
                null);
    }
}
