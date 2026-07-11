package com.example.adplatform.tracking.service.impl;

import com.example.adplatform.tracking.dto.AdEventRequest;
import com.example.adplatform.tracking.entity.AdEventType;
import com.example.adplatform.tracking.producer.AdEventProducer;
import com.example.adplatform.tracking.service.AdEventService;
import com.example.adplatform.tracking.vo.AdEventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class AdEventServiceImpl implements AdEventService {

    private final AdEventProducer adEventProducer;

    @Override
    public AdEventResponse collect(AdEventRequest request) {
        AdEventType eventType = AdEventType.parse(request.eventType());
        adEventProducer.send(request);

        return new AdEventResponse(
                request.eventId(),
                eventType.name(),
                false,
                false,
                0L,
                null);
    }
}
