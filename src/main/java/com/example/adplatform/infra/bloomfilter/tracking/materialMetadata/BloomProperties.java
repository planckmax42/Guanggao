package com.example.adplatform.infra.bloomfilter.tracking.materialMetadata;

import jakarta.validation.constraints.AssertTrue;
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
@ConfigurationProperties(
        prefix = "app.event-metadata-cache.bloom",
        ignoreUnknownFields = false)
public class BloomProperties {
    @Min(1)
    private long initialCapacity;
    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax(value = "1.0", inclusive = false)
    private double falsePositiveProbability;
    @DecimalMin(value = "1.0", inclusive = false)
    private double expansionFactor;
    @Min(1)
    private long maxCapacity;
    @Min(1)
    private long rebuildDelayMs;
    @Min(1)
    private long rebuildInitialDelayMs;
    @NotNull
    private Duration expansionCooldown;
    @Min(1)
    private long expansionCheckDelayMs;
    @Min(1)
    private long expansionCheckInitialDelayMs;

    @AssertTrue(message = "最大容量必须大于或等于初始容量")
    public boolean isMaxCapacityValid() {
        return maxCapacity >= initialCapacity;
    }
}
