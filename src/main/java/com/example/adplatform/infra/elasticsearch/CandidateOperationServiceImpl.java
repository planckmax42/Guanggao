package com.example.adplatform.infra.elasticsearch;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeRelation;
import co.elastic.clients.json.JsonData;
import com.example.adplatform.delivery.portMQ.DeliveryElasticSearchPort;
import com.example.adplatform.infra.redis.delivery.search.CandidateIndexRebuildGuard;
import com.example.adplatform.infra.resilience.delivery.search.CandidateElasticsearchCircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.ByQueryResponse;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.data.elasticsearch.core.query.ScriptType;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CandidateOperationServiceImpl implements DeliveryElasticSearchPort {

    private final ElasticSearchProperties properties;
    private final ElasticsearchOperations operations;
    private final CandidateIndexRebuildGuard rebuildGuard;
    private final CandidateElasticsearchCircuitBreaker circuitBreaker;
    private final CandidateConverter candidateConverter;

    private static final String UPDATE_SCRIPT = """
            def current = ctx._source[params.field];
            if (current == params.oldValue) {
                if (current == params.newValue) {
                    ctx.op = 'noop';
                } else {
                    ctx._source[params.field] = params.newValue;
                }
            } else {
                ctx.op = 'noop';
            }
            """;
    @Override
    public String create(CreateCandidateRequest request) {
        Assert.notNull(request, "创建候选请求不能为空");
        if (rebuildGuard.isRebuilding()) {
            throw new IllegalStateException("ES正在重建，请稍后重试");
        }
        CandidateDocument document = candidateConverter.toDocument(request);
        IndexQuery query = new IndexQueryBuilder()
                .withObject(document)
                .withOpType(IndexQuery.OpType.CREATE)
                .build();
        IndexCoordinates index = IndexCoordinates.of(properties.getCandidate().getWriteAlias());
        String id = operations.index(query, index);
        operations.indexOps(index).refresh();
        return id;
    }

    @Override
    public List<QueryCandidateResponse> query(QueryCandidateRequest request) {
        List<Query> filters = List.of(
                term("slotId", request.slotId().toString()),
                Query.of(q -> q.range(r -> r.field("bidPrice").gt(JsonData.of(request.bidPrice())))),
                term("billingType", request.billingType()),
                term("deviceTypes", request.deviceType()),
                Query.of(q -> q.bool(b -> b
                        .should(term("gender", request.gender()), term("gender", "ALL"))
                        .minimumShouldMatch("1"))),
                Query.of(q -> q.range(r -> r.field("ageRange")
                        .gte(JsonData.of(request.age()))
                        .lte(JsonData.of(request.age()))
                        .relation(RangeRelation.Contains))),
                term("regions", request.region())
        );

        ElasticSearchProperties.Candidate config = properties.getCandidate();
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b.filter(filters)))
                .withSourceFilter(new FetchSourceFilter(
                        new String[]{"materialId", "slotId", "planId", "bidPrice", "billingType"},
                        new String[0]))
                .withSort(s -> s.field(f -> f.field("bidPrice").order(SortOrder.Desc)))
                .withMaxResults(config.getRecallSize())
                .withTimeout(config.getQueryTimeout())
                .withTrackTotalHits(false)
                .build();

        return circuitBreaker.execute(() -> operations.search(
                        query, CandidateDocument.class, IndexCoordinates.of(config.getReadAlias()))
                .stream()
                .map(SearchHit::getContent)
                .map(candidateConverter::toQueryResponse)
                .toList());
    }

    public ByQueryResponse deleteByPlanId(Long planId) {
        return delete("planId", planId);
    }

    public ByQueryResponse deleteByMaterialId(Long materialId) {
        return delete("materialId", materialId);
    }

    public ByQueryResponse deleteBySlotId(Long slotId) {
        return delete("slotId", slotId);
    }

    private ByQueryResponse delete(String idField, Long id) {
        Objects.requireNonNull(id, idField + " 不能为空");
        if (rebuildGuard.isRebuilding()) {
            throw new IllegalStateException("ES正在重建，请稍后重试");
        }
        NativeQuery query = NativeQuery.builder()
                .withQuery(term(idField, id.toString()))
                .build();
        IndexCoordinates index = IndexCoordinates.of(properties.getCandidate().getWriteAlias());
        ByQueryResponse response = operations.delete(query, CandidateDocument.class, index);
        operations.indexOps(index).refresh();
        return response;
    }

    private Query term(String field, String value) {
        return Query.of(q -> q.term(t -> t.field(field).value(value)));
    }

    public ByQueryResponse updateSlotId(Long materialId, String field, Long oldValue, Long newValue) {
        return update("materialId", materialId, "slotId", field, oldValue, newValue);
    }

    public ByQueryResponse updateBudgetTotal(Long planId, String field, Long oldValue, Long newValue) {
        return update("planId", planId, "budget_total", field, oldValue, newValue);
    }

    public ByQueryResponse updateBudgetDaily(Long planId, String field, Long oldValue, Long newValue) {
        return update("planId", planId, "budget_daily", field, oldValue, newValue);
    }

    public ByQueryResponse updateBidPrice(Long planId, String field, Long oldValue, Long newValue) {
        return update("planId", planId, "bidPrice", field, oldValue, newValue);
    }

    public ByQueryResponse updateBillingType(Long planId, String field, String oldValue, String newValue) {
        return update("planId", planId, "billingType", field, oldValue, newValue);
    }

    public ByQueryResponse updateDeviceTypes(Long planId, String field, List<String> oldValue,
                                             List<String> newValue) {
        return update("planId", planId, "deviceTypes", field, oldValue, newValue);
    }

    public ByQueryResponse updateGender(Long planId, String field, String oldValue, String newValue) {
        return update("planId", planId, "gender", field, oldValue, newValue);
    }

    public ByQueryResponse updateAgeRange(Long planId, String field, Range<Integer> oldValue,
                                          Range<Integer> newValue) {
        return update("planId", planId, "ageRange", field, toRangeValue(oldValue), toRangeValue(newValue));
    }

    public ByQueryResponse updateRegions(Long planId, String field, List<String> oldValue,
                                         List<String> newValue) {
        return update("planId", planId, "regions", field, oldValue, newValue);
    }

    private ByQueryResponse update(String idField, Long id, String expectedField, String field,
                                   Object oldValue, Object newValue) {
        Objects.requireNonNull(id, idField + " 不能为空");
        if (!expectedField.equals(field)) {
            throw new IllegalArgumentException("该方法只允许修改字段：" + expectedField);
        }
        if (rebuildGuard.isRebuilding()) {
            throw new IllegalStateException("ES正在重建，请稍后重试");//todo:后续加入补偿机制，过一段时间重试？
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.term(t -> t.field(idField).value(id.toString())))
                .build();
        Map<String, Object> params = new HashMap<>();
        params.put("field", field);
        params.put("oldValue", oldValue);
        params.put("newValue", newValue);
        UpdateQuery updateQuery = UpdateQuery.builder(query)
                .withScriptType(ScriptType.INLINE)
                .withLang("painless")
                .withScript(UPDATE_SCRIPT)
                .withParams(params)
                .withAbortOnVersionConflict(true)
                .build();
        IndexCoordinates index = IndexCoordinates.of(properties.getCandidate().getWriteAlias());
        ByQueryResponse response = operations.updateByQuery(updateQuery, index);
        operations.indexOps(index).refresh();
        return response;
    }

    private Map<String, Object> toRangeValue(Range<Integer> range) {
        if (range == null) {
            return null;
        }
        Map<String, Object> value = new HashMap<>();
        Range.Bound<Integer> lower = range.getLowerBound();
        Range.Bound<Integer> upper = range.getUpperBound();
        lower.getValue().ifPresent(bound -> value.put(lower.isInclusive() ? "gte" : "gt", bound));
        upper.getValue().ifPresent(bound -> value.put(upper.isInclusive() ? "lte" : "lt", bound));
        return value;
    }
}
