package com.example.adplatform.health.response;

import java.time.LocalDateTime;

public record HealthResponse(
        String application,
        String status,
        ComponentHealthResponse mysql,
        ComponentHealthResponse redis,
        LocalDateTime checkedAt) {
}
