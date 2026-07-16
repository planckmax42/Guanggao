package com.example.adplatform.search.candidate.service;

import com.example.adplatform.delivery.dto.AdDeliveryRequest;
import com.example.adplatform.search.candidate.mapper.CandidateSourceMapper;
import com.example.adplatform.search.candidate.model.AdCandidateDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MysqlCandidateRecallService {

    private final CandidateSourceMapper sourceMapper;
    private final CandidateDocumentFactory documentFactory;

    public List<AdCandidateDocument> recall(AdDeliveryRequest request) {
        return sourceMapper.selectEligibleBySlotCode(request.slotCode()).stream()
                .map(documentFactory::from)
                .filter(candidate -> CandidateTargetingMatcher.matches(candidate, request))
                .toList();
    }
}
