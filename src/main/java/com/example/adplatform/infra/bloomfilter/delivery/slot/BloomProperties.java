package com.example.adplatform.infra.bloomfilter.delivery.slot;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** 广告位布隆过滤器的容量、误判率、扩容和调度参数。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.slot-cache.bloom",ignoreUnknownFields = false)
public class BloomProperties {

    @Min(1)
    private int initialCapacity=10_000;

    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax(value = "1.0", inclusive = false)
    private double falsePositiveProbability=0.01;


    @DecimalMin(value = "1.0", inclusive = false)
    private double expansionFactor=2.0;

    @Min(1)
    private long maxCapacity=1_000_000;

    @NotNull
    @DurationMin(seconds = 1)
    private Duration rebuildDelay=Duration.ofMinutes(5);;

    @NotNull
    @DurationMin(seconds = 1)
    private Duration rebuildInitialDelay=Duration.ofMinutes(5);;

    @NotNull
    @DurationMin(seconds = 1)
    private Duration expansionCheckDelay=Duration.ofSeconds(30);;

    @NotNull
    @DurationMin(seconds = 1)
    private Duration expansionCheckInitialDelay=Duration.ofSeconds(30);

    @AssertTrue(message = "最大容量必须大于初始容量")
    public boolean isMaxCapacityValid() {
        return maxCapacity >= initialCapacity;
    }
}
