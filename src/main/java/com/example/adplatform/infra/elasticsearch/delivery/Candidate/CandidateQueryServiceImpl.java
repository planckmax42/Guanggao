package com.example.adplatform.infra.elasticsearch.delivery.Candidate;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeRelation;
import co.elastic.clients.json.JsonData;
import com.example.adplatform.infra.resilience.delivery.search.CandidateElasticsearchCircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import java.util.List;

/** 按广告位、出价和定向条件查询候选文档，各条件之间为 AND。 */
@Service
@RequiredArgsConstructor
public class CandidateQueryServiceImpl {

    private final ElasticsearchOperations operations;
    private final EsProperties properties;
    private final CandidateElasticsearchCircuitBreaker circuitBreaker;

    /**
     * 参数均为必填；keyword 字段按原值精确匹配，区分大小写。
     * bidPrice 是排除等值的出价下限，deviceType 和 region 分别匹配文档集合中的一个元素。
     */
    public List<CandidateDocument> query(Long slotId, Long bidPrice, String billingType,
                                         String deviceType, String gender, Integer age, String region) {
        List<Query> filters = List.of(
                term("slotId", slotId.toString()),
                Query.of(q -> q.range(r -> r.field("bidPrice").gt(JsonData.of(bidPrice)))),
                term("billingType", billingType),
                term("deviceTypes", deviceType),
                Query.of(q -> q.bool(b -> b
                        .should(term("gender", gender), term("gender", "ALL"))
                        .minimumShouldMatch("1"))),
                Query.of(q -> q.range(r -> r.field("ageRange")
                        .gte(JsonData.of(age))
                        .lte(JsonData.of(age))
                        .relation(RangeRelation.Contains))),
                term("regions", region)
        );

        EsProperties.Candidate config = properties.getCandidate();
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b.filter(filters)))
                .withSort(s -> s.field(f -> f.field("bidPrice").order(SortOrder.Desc)))
                .withMaxResults(config.getRecallSize())
                .withTimeout(config.getQueryTimeout())
                .withTrackTotalHits(false)
                .build();

        return circuitBreaker.execute(() -> operations.search(
                        query, CandidateDocument.class, IndexCoordinates.of(config.getReadAlias()))
                .stream()
                .map(SearchHit::getContent)
                .toList());
    }

    private Query term(String field, String value) {
        return Query.of(q -> q.term(t -> t.field(field).value(value)));
    }
}
