package com.example.adplatform.delivery.port;

import com.example.adplatform.delivery.request.DeliveryRequest;
import com.example.adplatform.infra.elasticsearch.CandidateDocument;

import java.util.List;

public interface CandidateRecallPort {
    boolean isEnabled();

    List<CandidateDocument> recall(DeliveryRequest request);
}
