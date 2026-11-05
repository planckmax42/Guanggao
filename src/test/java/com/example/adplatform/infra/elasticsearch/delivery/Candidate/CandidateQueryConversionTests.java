package com.example.adplatform.infra.elasticsearch.delivery.Candidate;

import com.example.adplatform.search.candidate.query.CandidateQueryResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandidateQueryConversionTests {

    private final ElasticSearchManager service = new ElasticSearchManager(
            null, null, null, null, null, null, new ObjectMapper());

    @Test
    void convertsTargetingAndPreservesPriceAndIdentifiers() {
        CandidateQueryResult row = row();
        row.setAdvertiserPublicId("adv_1");
        row.setDeviceType("[\"ANDROID\",\"IOS\"]");
        row.setRegionCode("[\"BEIJING\",\"SHANGHAI\"]");
        row.setGender("FEMALE");
        row.setAgeMin(18);
        row.setAgeMax(35);
        row.setBidPrice(123456L);
        row.setBillingType("CPC");
        row.setSlotPublicId("slot_1");
        row.setSlotCode("HOME");

        var document = service.fromQueryResultToDocument(row);

        assertThat(document.getDeviceType()).containsExactly("ANDROID", "IOS");
        assertThat(document.getRegionCode()).containsExactly("BEIJING", "SHANGHAI");
        assertThat(document.getAgeRange()).isEqualTo(Range.closed(18, 35));
        assertThat(document.getAdvertiserPublicId()).isEqualTo("adv_1");
        assertThat(document.getGender()).isEqualTo("FEMALE");
        assertThat(document.getBidPrice()).isEqualTo(123456L);
        assertThat(document.getBillingType()).isEqualTo("CPC");
        assertThat(document.getSlotPublicId()).isEqualTo("slot_1");
        assertThat(document.getSlotCode()).isEqualTo("HOME");
        assertThat(document.getId()).isEqualTo(service.fromQueryResultToDocument(row).getId());
        row.setRulePublicId("rule_2");
        assertThat(service.fromQueryResultToDocument(row).getId()).isNotEqualTo(document.getId());
    }

    @Test
    void supportsMissingRuleAndOpenAgeBounds() {
        CandidateQueryResult row = row();
        row.setRulePublicId(null);
        var document = service.fromQueryResultToDocument(row);
        assertThat(document.getRulePublicId()).isNull();
        assertThat(document.getDeviceType()).isEmpty();
        assertThat(document.getRegionCode()).isEmpty();
        assertThat(document.getAgeRange()).isEqualTo(Range.<Integer>unbounded());

        row.setAgeMin(18);
        assertThat(service.fromQueryResultToDocument(row).getAgeRange())
                .isEqualTo(Range.rightUnbounded(Range.Bound.inclusive(18)));
        row.setAgeMin(null);
        row.setAgeMax(35);
        assertThat(service.fromQueryResultToDocument(row).getAgeRange())
                .isEqualTo(Range.leftUnbounded(Range.Bound.inclusive(35)));
    }

    @Test
    void rejectsMalformedTargetingInsteadOfRemovingRestrictions() {
        CandidateQueryResult row = row();
        row.setDeviceType("ANDROID");
        assertThatThrownBy(() -> service.fromQueryResultToDocument(row))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CandidateQueryResult row() {
        CandidateQueryResult row = new CandidateQueryResult();
        row.setPlanPublicId("plan_1");
        row.setMaterialPublicId("mat_1");
        row.setRulePublicId("rule_1");
        return row;
    }
}
