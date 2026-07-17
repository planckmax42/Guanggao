package com.example.adplatform.tracking.service;

import com.example.adplatform.tracking.request.EventRequest;
import com.example.adplatform.tracking.response.EventResponse;

public interface EventService {

    EventResponse collect(EventRequest request);
}
