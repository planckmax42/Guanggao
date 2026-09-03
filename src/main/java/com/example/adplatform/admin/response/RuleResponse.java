package com.example.adplatform.admin.response;

import java.time.LocalDateTime;
import java.util.List;

public record RuleResponse(
        String publicId,
        String planPublicId,
        List<String> regions,
        List<String> deviceTypes,
        String gender,
        Integer ageMin,
        Integer ageMax,
        List<String> userTags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
