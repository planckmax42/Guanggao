package com.example.adplatform.infra.bloom.tracking.materialMetadata;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
public  class MaterialMetadataBloomFilterProperties {
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