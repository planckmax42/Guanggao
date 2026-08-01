package com.example.adplatform.infra.bloom.delivery.slot;

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

/** 广告位布隆过滤器的容量、误判率、扩容和调度参数。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.slot-cache.bloom")
public class SlotBloomFilterProperties {

    @Min(1)
    private int expectedInsertions;

    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax(value = "1.0", inclusive = false)
    private double falsePositiveProbability;

    @Min(1)
    private long minimumAbsentSamples;

    @DecimalMin(value = "1.0", inclusive = false)
    private double expansionFactor;

    @Min(1)
    private long maxExpectedInsertions;

    @NotNull
    private Duration expansionCooldown;

    @NotNull
    private Duration rebuildDelay;

    @NotNull
    private Duration rebuildInitialDelay;

    @NotNull
    private Duration expansionCheckDelay;

    @NotNull
    private Duration expansionCheckInitialDelay;

    @AssertTrue(message = "Bloom filter scheduler delays must be greater than zero")
    public boolean isSchedulerDelayValid() {
        return isPositiveOrNull(rebuildDelay)
                && isPositiveOrNull(rebuildInitialDelay)
                && isPositiveOrNull(expansionCheckDelay)
                && isPositiveOrNull(expansionCheckInitialDelay);
    }

    private boolean isPositiveOrNull(Duration duration) {
        return duration == null || (!duration.isZero() && !duration.isNegative());
    }
}
