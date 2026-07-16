package com.example.adplatform.search.candidate.service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import com.example.adplatform.delivery.dto.AdDeliveryRequest;
import com.example.adplatform.search.config.AdElasticsearchProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class CandidateQueryFactory {

    private final AdElasticsearchProperties properties;

    public CandidateQueryFactory(AdElasticsearchProperties properties) {
        this.properties = properties;
    }

    public NativeQuery build(AdDeliveryRequest request) {
        List<Query> filters = new ArrayList<>();
        filters.add(term("slotCode", request.slotCode().toUpperCase(Locale.ROOT)));
        long now = Instant.now().toEpochMilli();
        filters.add(rangeLte("startTime", now));
        filters.add(rangeGte("endTime", now));
        filters.add(optionalTerm("regionAll", "regions", request.region(), true));
        filters.add(optionalTerm("deviceAll", "deviceTypes", request.deviceType(), true));
        filters.add(optionalTerm("genderAll", "gender", request.gender(), true));
        filters.add(ageFilter(request.age()));
        filters.add(tagsFilter(request.tags()));

        int size = Math.min(properties.getCandidate().getRecallSize(),
                properties.getCandidate().getMaxRecallSize());
        return NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b.filter(filters)))
                .withSort(s -> s.field(f -> f.field("bidPrice").order(SortOrder.Desc)))
                .withSort(s -> s.field(f -> f.field("materialId").order(SortOrder.Desc)))
                .withPageable(PageRequest.of(0, size))
                .withTimeout(properties.getCandidate().getQueryTimeout())
                .withTrackTotalHits(false)
                .build();
    }

    private Query optionalTerm(String allField, String valueField, String value, boolean uppercase) {
        if (!StringUtils.hasText(value)) {
            return term(allField, true);
        }
        String normalized = uppercase ? value.toUpperCase(Locale.ROOT) : value.toLowerCase(Locale.ROOT);
        return or(term(allField, true), term(valueField, normalized));
    }

    private Query ageFilter(Integer age) {
        if (age == null) {
            return term("ageAll", true);
        }
        return or(
                term("ageAll", true),
                Query.of(q -> q.bool(b -> b.filter(
                        rangeLte("ageMin", age),
                        rangeGte("ageMax", age)))));
    }

    private Query tagsFilter(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return term("tagAll", true);
        }
        List<FieldValue> values = tags.stream()
                .filter(StringUtils::hasText)
                .map(value -> FieldValue.of(value.toLowerCase(Locale.ROOT)))
                .toList();
        if (values.isEmpty()) {
            return term("tagAll", true);
        }
        Query terms = Query.of(q -> q.terms(t -> t.field("tags").terms(v -> v.value(values))));
        return or(term("tagAll", true), terms);
    }

    private Query or(Query... queries) {
        return Query.of(q -> q.bool(b -> b.should(List.of(queries)).minimumShouldMatch("1")));
    }

    private Query term(String field, String value) {
        return Query.of(q -> q.term(t -> t.field(field).value(value)));
    }

    private Query term(String field, boolean value) {
        return Query.of(q -> q.term(t -> t.field(field).value(value)));
    }

    private Query rangeLte(String field, Object value) {
        return Query.of(q -> q.range(r -> r.field(field).lte(JsonData.of(value))));
    }

    private Query rangeGte(String field, Object value) {
        return Query.of(q -> q.range(r -> r.field(field).gte(JsonData.of(value))));
    }
}
