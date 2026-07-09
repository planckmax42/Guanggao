package com.example.adplatform.health.vo;

public record ComponentHealthVO(String status, String message, Long responseTimeMs) {

    public static ComponentHealthVO up(long responseTimeMs) {
        return new ComponentHealthVO("UP", "ok", responseTimeMs);
    }

    public static ComponentHealthVO down(String message, long responseTimeMs) {
        return new ComponentHealthVO("DOWN", message, responseTimeMs);
    }
}
