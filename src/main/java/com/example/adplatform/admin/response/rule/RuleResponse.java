package com.example.adplatform.admin.response.rule;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;
import java.util.List;

public record RuleResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long advertiserId,
        String name,
        List<String> deviceTypes,
        String gender,
        Integer ageMin,
        Integer ageMax,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
