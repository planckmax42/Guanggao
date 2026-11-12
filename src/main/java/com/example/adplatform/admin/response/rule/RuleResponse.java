package com.example.adplatform.admin.response.rule;

import java.time.LocalDateTime;
import java.util.List;

public record RuleResponse(
        String publicId,
        String advertiserPublicId,
        String name,
        List<String> deviceTypes,
        String gender,
        Integer ageMin,
        Integer ageMax,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
