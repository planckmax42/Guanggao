package com.example.adplatform.admin.vo;

import java.time.LocalDateTime;
import java.util.List;

public record RuleVO(
        Long id,
        Long planId,
        List<String> regions,
        List<String> deviceTypes,
        String gender,
        Integer ageMin,
        Integer ageMax,
        List<String> userTags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
