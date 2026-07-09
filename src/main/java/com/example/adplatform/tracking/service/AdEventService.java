package com.example.adplatform.tracking.service;

import com.example.adplatform.tracking.dto.AdEventRequest;
import com.example.adplatform.tracking.vo.AdEventResponse;

public interface AdEventService {

    AdEventResponse collect(AdEventRequest request);
}
