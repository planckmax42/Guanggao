package com.example.adplatform.admin.vo;

import java.time.LocalDateTime;
import java.util.List;

public record TargetingRuleVO(
        Long id,
        Long campaignId,
        List<String> regions,
        List<String> deviceTypes,
        String gender,
        Integer ageMin,
        Integer ageMax,
        List<String> userTags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
