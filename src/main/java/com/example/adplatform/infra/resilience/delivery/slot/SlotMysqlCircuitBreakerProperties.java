package com.example.adplatform.infra.resilience.delivery.slot;

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

/** 广告位缓存回源 MySQL 的熔断参数。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.slot-cache.mysql-circuit-breaker")
public class SlotMysqlCircuitBreakerProperties {

    @Min(1)
    private int slidingWindowSize;

    @Min(1)
    private int minimumNumberOfCalls;

    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax("100.0")
    private float failureRateThreshold;

    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax("100.0")
    private float slowCallRateThreshold;

    @NotNull
    private Duration slowCallDurationThreshold;

    @NotNull
    private Duration openStateWaitDuration;

    @Min(1)
    private int permittedCallsInHalfOpenState;
}
