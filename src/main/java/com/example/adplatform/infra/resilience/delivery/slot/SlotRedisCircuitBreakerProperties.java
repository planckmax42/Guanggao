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

/** 广告位 Redis 访问熔断参数。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.slot-cache.redis-circuit-breaker")
public class SlotRedisCircuitBreakerProperties {

    @Min(1)
    private int slidingWindowSize = 20;
    @Min(1)
    private int minimumNumberOfCalls = 10;
    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax("100.0")
    private float failureRateThreshold = 50F;
    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax("100.0")
    private float slowCallRateThreshold = 50F;
    @NotNull
    private Duration slowCallDurationThreshold = Duration.ofMillis(100);
    @NotNull
    private Duration openStateWaitDuration = Duration.ofSeconds(10);
    @Min(1)
    private int permittedCallsInHalfOpenState = 3;
}
