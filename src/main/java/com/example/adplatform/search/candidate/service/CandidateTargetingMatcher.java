package com.example.adplatform.search.candidate.service;

import com.example.adplatform.delivery.dto.AdDeliveryRequest;
import com.example.adplatform.search.candidate.model.AdCandidateDocument;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class CandidateTargetingMatcher {

    private CandidateTargetingMatcher() { }

    public static boolean matches(AdCandidateDocument candidate, AdDeliveryRequest request) {
        if (!candidate.isRegionAll() && !containsIgnoreCase(candidate.getRegions(), request.region())) return false;
        if (!candidate.isDeviceAll() && !containsIgnoreCase(candidate.getDeviceTypes(), request.deviceType())) return false;
        if (!candidate.isGenderAll() && (!StringUtils.hasText(request.gender())
                || !candidate.getGender().equalsIgnoreCase(request.gender()))) return false;
        if (!candidate.isAgeAll() && (request.age() == null
                || request.age() < candidate.getAgeMin() || request.age() > candidate.getAgeMax())) return false;
        if (!candidate.isTagAll()) {
            if (request.tags() == null || request.tags().isEmpty()) return false;
            Set<String> requestTags = new HashSet<>();
            request.tags().forEach(tag -> requestTags.add(tag.toLowerCase(Locale.ROOT)));
            if (candidate.getTags().stream().noneMatch(requestTags::contains)) return false;
        }
        return true;
    }

    private static boolean containsIgnoreCase(java.util.List<String> values, String requestValue) {
        return StringUtils.hasText(requestValue)
                && values != null
                && values.stream().anyMatch(value -> value.equalsIgnoreCase(requestValue));
    }
}
