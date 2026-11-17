package com.example.adplatform.infra.elasticsearch;

public record QueryCandidateRequest(
        Long slotId,
        Long bidPrice,
        String billingType,
        String deviceType,
        String gender,
        Integer age,
        String region) {
}
