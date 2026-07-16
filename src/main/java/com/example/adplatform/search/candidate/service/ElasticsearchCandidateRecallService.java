package com.example.adplatform.search.candidate.service;

import com.example.adplatform.delivery.dto.AdDeliveryRequest;
import com.example.adplatform.search.candidate.model.AdCandidateDocument;
import com.example.adplatform.search.config.AdElasticsearchProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ElasticsearchCandidateRecallService {

    private final ElasticsearchOperations operations;
    private final CandidateQueryFactory queryFactory;
    private final AdElasticsearchProperties properties;

    public List<AdCandidateDocument> recall(AdDeliveryRequest request) {
        return operations.search(
                        queryFactory.build(request),
                        AdCandidateDocument.class,
                        IndexCoordinates.of(properties.getCandidate().getReadAlias()))
                .stream()
                .map(SearchHit::getContent)
                .toList();
    }
}
