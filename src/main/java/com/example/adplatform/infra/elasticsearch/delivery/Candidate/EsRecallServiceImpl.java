package com.example.adplatform.infra.elasticsearch.delivery.Candidate;

import com.example.adplatform.delivery.port.CandidateRecallPort;
import com.example.adplatform.delivery.request.AdDeliveryRequest;
import com.example.adplatform.search.candidate.model.AdCandidateDocument;
import com.example.adplatform.search.candidate.service.CandidateRecallService;
import com.example.adplatform.infra.resilience.delivery.search.CandidateElasticsearchCircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ES 候选查询适配器。查询构造与降级策略分别由
 * {@link EsQueryFactory} 和 {@link CandidateRecallService} 负责。
 */
@Service
@RequiredArgsConstructor
public class EsRecallServiceImpl implements CandidateRecallPort {

    private final ElasticsearchOperations elasticsearchOperations;
    private final EsQueryFactory queryFactory;
    private final EsProperties properties;
    private final CandidateElasticsearchCircuitBreaker circuitBreaker;

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /** 通过读别名执行静态候选粗召回。 */
    @Override
    public List<AdCandidateDocument> recall(AdDeliveryRequest request) {
        return circuitBreaker.execute(() -> elasticsearchOperations.search(
                        queryFactory.build(request),//构建查询条件
                        AdCandidateDocument.class,//指定结果要映射成的Java对象类型
                        IndexCoordinates.of(properties.getCandidate().getReadAlias()))
                .stream()
                .map(SearchHit::getContent)
                .toList());
    }
}
