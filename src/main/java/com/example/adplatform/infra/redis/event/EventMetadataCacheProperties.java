package com.example.adplatform.infra.redis.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.event-metadata-cache")
public class EventMetadataCacheProperties {

    @NotNull
    private Duration redisTtl;
    @NotNull
    private Duration redisTtlJitter;
    @Valid
    @NotNull
    private Bloom bloom = new Bloom();

    @Getter
    @Setter
    public static class Bloom {
        @Min(1)
        private long expectedInsertions;
        @DecimalMin(value = "0.0", inclusive = false)
        @DecimalMax(value = "1.0", inclusive = false)
        private double falsePositiveProbability;
        @DecimalMin(value = "1.0", inclusive = false)
        private double expansionFactor;
        @Min(1)
        private long maxExpectedInsertions;
        @NotNull
        private Duration expansionCooldown;
        private long rebuildDelayMs;
        private long rebuildInitialDelayMs;
        private long expansionCheckDelayMs;
        private long expansionCheckInitialDelayMs;
    }
}
