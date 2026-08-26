package com.example.adplatform.delivery.port;

import com.example.adplatform.delivery.request.AdDeliveryRequest;
import com.example.adplatform.search.candidate.model.AdCandidateDocument;

import java.util.List;

public interface CandidateRecallPort {
    boolean isEnabled();

    List<AdCandidateDocument> recall(AdDeliveryRequest request);
}
