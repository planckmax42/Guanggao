package com.example.adplatform.search.candidate.service;

import com.example.adplatform.delivery.request.DeliveryRequest;
import com.example.adplatform.infra.elasticsearch.CandidateDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateTargetingMatcherTests {

    @Test
    void shouldMatchWhenEveryTargetingDimensionIsUnrestricted() {
        CandidateDocument candidate = candidate(true, true, true, true, true);
        DeliveryRequest request = new DeliveryRequest(
                1001L, "HOME_BANNER", null, null, null, null, List.of(), 3);

        assertThat(CandidateTargetingMatcher.matches(candidate, request)).isTrue();
    }

    @Test
    void shouldMatchCaseInsensitivelyAndAcceptAnyIntersectingTag() {
        CandidateDocument candidate = candidate(false, false, false, false, false);
        candidate.setRegions(List.of("BEIJING", "SHANGHAI"));
        candidate.setDeviceTypes(List.of("IOS", "ANDROID"));
        candidate.setGender("FEMALE");
        candidate.setAgeMin(18);
        candidate.setAgeMax(35);
        candidate.setTags(List.of("fresh", "family"));
        DeliveryRequest request = new DeliveryRequest(
                1001L, "HOME_BANNER", "beijing", "ios", 35,
                "female", List.of("shopping", "FAMILY"), 3);

        assertThat(CandidateTargetingMatcher.matches(candidate, request)).isTrue();
    }

    @Test
    void shouldRejectMissingOrOutOfRangeTargetingContext() {
        CandidateDocument candidate = candidate(false, false, false, false, false);
        candidate.setRegions(List.of("BEIJING"));
        candidate.setDeviceTypes(List.of("IOS"));
        candidate.setGender("FEMALE");
        candidate.setAgeMin(18);
        candidate.setAgeMax(35);
        candidate.setTags(List.of("fresh"));

        assertThat(CandidateTargetingMatcher.matches(candidate,
                new DeliveryRequest(1L, "HOME_BANNER", "SHANGHAI", "IOS", 20,
                        "FEMALE", List.of("fresh"), 1))).isFalse();
        assertThat(CandidateTargetingMatcher.matches(candidate,
                new DeliveryRequest(1L, "HOME_BANNER", "BEIJING", "IOS", 36,
                        "FEMALE", List.of("fresh"), 1))).isFalse();
        assertThat(CandidateTargetingMatcher.matches(candidate,
                new DeliveryRequest(1L, "HOME_BANNER", "BEIJING", "IOS", 20,
                        "FEMALE", List.of(), 1))).isFalse();
    }

    private CandidateDocument candidate(
            boolean regionAll,
            boolean deviceAll,
            boolean genderAll,
            boolean ageAll,
            boolean tagAll) {
        CandidateDocument candidate = new CandidateDocument();
        candidate.setRegionAll(regionAll);
        candidate.setDeviceAll(deviceAll);
        candidate.setGenderAll(genderAll);
        candidate.setAgeAll(ageAll);
        candidate.setTagAll(tagAll);
        return candidate;
    }
}
