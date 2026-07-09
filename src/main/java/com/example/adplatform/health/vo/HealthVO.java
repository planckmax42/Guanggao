package com.example.adplatform.health.vo;

import java.time.LocalDateTime;

public record HealthVO(
        String application,
        String status,
        ComponentHealthVO mysql,
        ComponentHealthVO redis,
        LocalDateTime checkedAt) {
}
