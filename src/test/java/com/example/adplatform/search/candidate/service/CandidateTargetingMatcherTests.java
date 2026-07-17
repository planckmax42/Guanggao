package com.example.adplatform.search.candidate.service;

import com.example.adplatform.delivery.request.AdDeliveryRequest;
import com.example.adplatform.search.candidate.model.AdCandidateDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateTargetingMatcherTests {

    @Test
    void shouldMatchWhenEveryTargetingDimensionIsUnrestricted() {
        AdCandidateDocument candidate = candidate(true, true, true, true, true);
        AdDeliveryRequest request = new AdDeliveryRequest(
                1001L, "HOME_BANNER", null, null, null, null, List.of(), 3);

        assertThat(CandidateTargetingMatcher.matches(candidate, request)).isTrue();
    }

    @Test
    void shouldMatchCaseInsensitivelyAndAcceptAnyIntersectingTag() {
        AdCandidateDocument candidate = candidate(false, false, false, false, false);
        candidate.setRegions(List.of("BEIJING", "SHANGHAI"));
        candidate.setDeviceTypes(List.of("IOS", "ANDROID"));
        candidate.setGender("FEMALE");
        candidate.setAgeMin(18);
        candidate.setAgeMax(35);
        candidate.setTags(List.of("fresh", "family"));
        AdDeliveryRequest request = new AdDeliveryRequest(
                1001L, "HOME_BANNER", "beijing", "ios", 35,
                "female", List.of("shopping", "FAMILY"), 3);

        assertThat(CandidateTargetingMatcher.matches(candidate, request)).isTrue();
    }

    @Test
    void shouldRejectMissingOrOutOfRangeTargetingContext() {
        AdCandidateDocument candidate = candidate(false, false, false, false, false);
        candidate.setRegions(List.of("BEIJING"));
        candidate.setDeviceTypes(List.of("IOS"));
        candidate.setGender("FEMALE");
        candidate.setAgeMin(18);
        candidate.setAgeMax(35);
        candidate.setTags(List.of("fresh"));

        assertThat(CandidateTargetingMatcher.matches(candidate,
                new AdDeliveryRequest(1L, "HOME_BANNER", "SHANGHAI", "IOS", 20,
                        "FEMALE", List.of("fresh"), 1))).isFalse();
        assertThat(CandidateTargetingMatcher.matches(candidate,
                new AdDeliveryRequest(1L, "HOME_BANNER", "BEIJING", "IOS", 36,
                        "FEMALE", List.of("fresh"), 1))).isFalse();
        assertThat(CandidateTargetingMatcher.matches(candidate,
                new AdDeliveryRequest(1L, "HOME_BANNER", "BEIJING", "IOS", 20,
                        "FEMALE", List.of(), 1))).isFalse();
    }

    private AdCandidateDocument candidate(
            boolean regionAll,
            boolean deviceAll,
            boolean genderAll,
            boolean ageAll,
            boolean tagAll) {
        AdCandidateDocument candidate = new AdCandidateDocument();
        candidate.setRegionAll(regionAll);
        candidate.setDeviceAll(deviceAll);
        candidate.setGenderAll(genderAll);
        candidate.setAgeAll(ageAll);
        candidate.setTagAll(tagAll);
        return candidate;
    }
}
