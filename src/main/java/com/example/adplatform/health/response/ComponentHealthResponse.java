package com.example.adplatform.health.response;

public record ComponentHealthResponse(String status, String message, Long responseTimeMs) {

    public static ComponentHealthResponse up(long responseTimeMs) {
        return new ComponentHealthResponse("UP", "ok", responseTimeMs);
    }

    public static ComponentHealthResponse down(String message, long responseTimeMs) {
        return new ComponentHealthResponse("DOWN", message, responseTimeMs);
    }
}
