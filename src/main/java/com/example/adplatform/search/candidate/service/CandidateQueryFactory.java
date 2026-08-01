package com.example.adplatform.search.candidate.service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.json.JsonData;
import com.example.adplatform.delivery.request.AdDeliveryRequest;
import com.example.adplatform.search.config.AdElasticsearchProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 将一次投放请求转换为 ES 候选粗召回查询。
 *
 * <p>所有业务条件放在 bool.filter 中，不参与 ES 相关性打分；ES 只负责过滤和按出价截断，
 * 最终分数由 Java 计算。索引中的 {@code *All} 字段表示该维度未配置限制。</p>
 */
@Component
public class CandidateQueryFactory {

    private final AdElasticsearchProperties properties;

    public CandidateQueryFactory(AdElasticsearchProperties properties) {
        this.properties = properties;
    }

    /**
     * 构造广告位、投放时间及人群定向过滤条件。
     *
     * @param request 投放请求
     * @return 限制了召回数量和查询超时的原生 ES 查询
     */
    public NativeQuery build(AdDeliveryRequest request) {
        List<Query> filters = new ArrayList<>();
        filters.add(exactMatch("slotCode", request.slotCode().toUpperCase(Locale.ROOT)));
        long now = Instant.now().toEpochMilli();
        filters.add(lessThanOrEqual("startTime", now));
        filters.add(greaterThanOrEqual("endTime", now));
        filters.add(optionalTerm("regionAll", "regions", request.region(), true));
        filters.add(optionalTerm("deviceAll", "deviceTypes", request.deviceType(), true));
        filters.add(optionalTerm("genderAll", "gender", request.gender(), true));
        filters.add(ageFilter(request.age()));
        filters.add(tagsFilter(request.tags()));

        // 配置值再受硬上限约束，防止误配置把大结果集拉回应用内存。
        int size = Math.min(properties.getCandidate().getRecallSize(), properties.getCandidate().getMaxRecallSize());
        Query candidateFilter = QueryBuilders.bool(boolBuilder -> boolBuilder.filter(filters));
        return NativeQuery.builder()
                .withQuery(candidateFilter)
                .withSort(sortBuilder -> sortBuilder.field(fieldSortBuilder -> fieldSortBuilder
                        .field("bidPrice").order(SortOrder.Desc)))
                .withSort(sortBuilder -> sortBuilder.field(fieldSortBuilder -> fieldSortBuilder
                        .field("materialId").order(SortOrder.Desc)))
                .withPageable(PageRequest.of(0, size))
                .withTimeout(properties.getCandidate().getQueryTimeout())
                .withTrackTotalHits(false)
                .build();
    }

    private Query optionalTerm(String allField, String valueField, String value, boolean uppercase) {
        if (!StringUtils.hasText(value)) {
            // 请求没有该画像时，只能投放“不限制该维度”的广告。
            return exactMatch(allField, true);
        }
        String normalized = uppercase ? value.toUpperCase(Locale.ROOT) : value.toLowerCase(Locale.ROOT);
        return anyOf(exactMatch(allField, true), exactMatch(valueField, normalized));
    }

    private Query ageFilter(Integer age) {
        if (age == null) {
            return exactMatch("ageAll", true);
        }
        Query ageRange = QueryBuilders.bool(boolBuilder -> boolBuilder.filter(
                lessThanOrEqual("ageMin", age),
                greaterThanOrEqual("ageMax", age)));
        return anyOf(exactMatch("ageAll", true), ageRange);
    }

    private Query tagsFilter(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return exactMatch("tagAll", true);
        }
        List<FieldValue> values = tags.stream()
                .filter(StringUtils::hasText)
                .map(value -> FieldValue.of(value.toLowerCase(Locale.ROOT)))
                .toList();
        if (values.isEmpty()) {
            return exactMatch("tagAll", true);
        }
        // 当前业务定义为任一标签命中即可，不要求请求标签覆盖广告的全部标签。
        Query tagsMatch = QueryBuilders.terms(termsBuilder -> termsBuilder
                .field("tags")
                .terms(valuesBuilder -> valuesBuilder.value(values)));
        return anyOf(exactMatch("tagAll", true), tagsMatch);
    }

    private Query anyOf(Query... alternatives) {
        return QueryBuilders.bool(boolBuilder -> boolBuilder
                .should(List.of(alternatives))
                .minimumShouldMatch("1"));
    }

    private Query exactMatch(String field, String value) {
        return QueryBuilders.term(termBuilder -> termBuilder
                .field(field)
                .value(value));
    }

    private Query exactMatch(String field, boolean value) {
        return QueryBuilders.term(termBuilder -> termBuilder
                .field(field)
                .value(value));
    }

    private Query lessThanOrEqual(String field, Number value) {
        return QueryBuilders.range(rangeBuilder -> rangeBuilder
                .field(field)
                .lte(JsonData.of(value)));
    }

    private Query greaterThanOrEqual(String field, Number value) {
        return QueryBuilders.range(rangeBuilder -> rangeBuilder
                .field(field)
                .gte(JsonData.of(value)));
    }
}
