package com.example.adplatform.tracking.service;

import com.example.adplatform.tracking.request.EventRequest;
import com.example.adplatform.tracking.response.EventResponse;

import java.util.concurrent.CompletionStage;

public interface EventService {

    CompletionStage<EventResponse> collect(EventRequest request);
}
