package com.example.adplatform.search.candidate.service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.example.adplatform.delivery.dto.AdDeliveryRequest;
import com.example.adplatform.search.config.AdElasticsearchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateQueryFactoryTests {

    private AdElasticsearchProperties properties;
    private CandidateQueryFactory queryFactory;

    @BeforeEach
    void setUp() {
        properties = new AdElasticsearchProperties();
        queryFactory = new CandidateQueryFactory(properties);
    }

    @Test
    void shouldBuildNormalizedTargetingQueryAndKeepSearchOptions() {
        properties.getCandidate().setRecallSize(1500);
        properties.getCandidate().setMaxRecallSize(1000);
        properties.getCandidate().setQueryTimeout(Duration.ofMillis(80));
        AdDeliveryRequest request = new AdDeliveryRequest(
                1L, "home_banner", "beijing", "android", 25, "female",
                List.of("Sports", "Technology"), 3);

        long beforeBuild = Instant.now().toEpochMilli();
        NativeQuery nativeQuery = queryFactory.build(request);
        long afterBuild = Instant.now().toEpochMilli();

        Query rootQuery = nativeQuery.getQuery();
        assertThat(rootQuery.isBool()).isTrue();
        List<Query> filters = rootQuery.bool().filter();
        assertThat(filters).hasSize(8);

        assertStringTerm(filters.get(0), "slotCode", "HOME_BANNER");
        assertThat(rangeValue(filters.get(1), "startTime", true)).isBetween(beforeBuild, afterBuild);
        assertThat(rangeValue(filters.get(2), "endTime", false)).isBetween(beforeBuild, afterBuild);
        assertOptionalStringTarget(filters.get(3), "regionAll", "regions", "BEIJING");
        assertOptionalStringTarget(filters.get(4), "deviceAll", "deviceTypes", "ANDROID");
        assertOptionalStringTarget(filters.get(5), "genderAll", "gender", "FEMALE");
        assertAgeTarget(filters.get(6), 25);
        assertTagsTarget(filters.get(7), List.of("sports", "technology"));

        assertThat(nativeQuery.getPageable().getPageNumber()).isZero();
        assertThat(nativeQuery.getPageable().getPageSize()).isEqualTo(1000);
        assertThat(nativeQuery.getTimeout()).isEqualTo(Duration.ofMillis(80));
        assertThat(nativeQuery.getTrackTotalHits()).isFalse();
        assertThat(nativeQuery.getSortOptions()).hasSize(2);
        assertThat(nativeQuery.getSortOptions().get(0).field().field()).isEqualTo("bidPrice");
        assertThat(nativeQuery.getSortOptions().get(0).field().order()).isEqualTo(SortOrder.Desc);
        assertThat(nativeQuery.getSortOptions().get(1).field().field()).isEqualTo("materialId");
        assertThat(nativeQuery.getSortOptions().get(1).field().order()).isEqualTo(SortOrder.Desc);
    }

    @Test
    void shouldOnlyMatchUnrestrictedTargetingWhenProfileIsMissing() {
        AdDeliveryRequest request = new AdDeliveryRequest(
                1L, "HOME_BANNER", null, " ", null, null, List.of("", " "), 1);

        List<Query> filters = queryFactory.build(request).getQuery().bool().filter();

        assertBooleanTerm(filters.get(3), "regionAll", true);
        assertBooleanTerm(filters.get(4), "deviceAll", true);
        assertBooleanTerm(filters.get(5), "genderAll", true);
        assertBooleanTerm(filters.get(6), "ageAll", true);
        assertBooleanTerm(filters.get(7), "tagAll", true);
    }

    private void assertOptionalStringTarget(Query query, String allField, String valueField, String expectedValue) {
        assertThat(query.isBool()).isTrue();
        assertThat(query.bool().minimumShouldMatch()).isEqualTo("1");
        assertThat(query.bool().should()).hasSize(2);
        assertBooleanTerm(query.bool().should().get(0), allField, true);
        assertStringTerm(query.bool().should().get(1), valueField, expectedValue);
    }

    private void assertAgeTarget(Query query, int age) {
        assertThat(query.isBool()).isTrue();
        assertThat(query.bool().minimumShouldMatch()).isEqualTo("1");
        assertThat(query.bool().should()).hasSize(2);
        assertBooleanTerm(query.bool().should().get(0), "ageAll", true);

        Query boundedAge = query.bool().should().get(1);
        assertThat(boundedAge.isBool()).isTrue();
        assertThat(boundedAge.bool().filter()).hasSize(2);
        assertThat(rangeValue(boundedAge.bool().filter().get(0), "ageMin", true)).isEqualTo(age);
        assertThat(rangeValue(boundedAge.bool().filter().get(1), "ageMax", false)).isEqualTo(age);
    }

    private void assertTagsTarget(Query query, List<String> expectedTags) {
        assertThat(query.isBool()).isTrue();
        assertThat(query.bool().minimumShouldMatch()).isEqualTo("1");
        assertThat(query.bool().should()).hasSize(2);
        assertBooleanTerm(query.bool().should().get(0), "tagAll", true);

        Query termsQuery = query.bool().should().get(1);
        assertThat(termsQuery.isTerms()).isTrue();
        assertThat(termsQuery.terms().field()).isEqualTo("tags");
        assertThat(termsQuery.terms().terms().value())
                .extracting(FieldValue::stringValue)
                .containsExactlyElementsOf(expectedTags);
    }

    private void assertStringTerm(Query query, String field, String expectedValue) {
        assertThat(query.isTerm()).isTrue();
        assertThat(query.term().field()).isEqualTo(field);
        assertThat(query.term().value().stringValue()).isEqualTo(expectedValue);
    }

    private void assertBooleanTerm(Query query, String field, boolean expectedValue) {
        assertThat(query.isTerm()).isTrue();
        assertThat(query.term().field()).isEqualTo(field);
        assertThat(query.term().value().booleanValue()).isEqualTo(expectedValue);
    }

    private long rangeValue(Query query, String field, boolean upperBound) {
        assertThat(query.isRange()).isTrue();
        assertThat(query.range().field()).isEqualTo(field);
        return (upperBound ? query.range().lte() : query.range().gte()).to(Number.class).longValue();
    }
}
