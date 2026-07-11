package com.example.adplatform.tracking.service;

import com.example.adplatform.tracking.dto.EventRequest;
import com.example.adplatform.tracking.vo.EventResponse;

public interface EventService {

    EventResponse collect(EventRequest request);
}
