package com.example.adplatform.infra.kafka;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 原始广告事件 Producer 的可靠性、批处理和熔断参数。
 *
 * <p>这些参数只用于 {@code eventKafkaTemplate}，不会改变 Outbox Producer。</p>
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.kafka.event-producer")
public class EventKafkaProducerProperties {

    @NotNull
    private Duration deliveryTimeout = Duration.ofSeconds(10);
    @NotNull
    private Duration requestTimeout = Duration.ofSeconds(3);
    @NotNull
    private Duration retryBackoff = Duration.ofMillis(200);
    @NotNull
    private Duration maxBlock = Duration.ofSeconds(1);
    @NotNull
    private Duration linger = Duration.ofMillis(5);
    @Min(1)
    private int batchSize = 65_536;
    @NotBlank
    private String compressionType = "lz4";
    @Valid
    @NotNull
    private CircuitBreakerSettings circuitBreaker = new CircuitBreakerSettings();

    @AssertTrue(message = "event producer durations must be valid and deliveryTimeout >= requestTimeout + linger")
    public boolean isDurationConfigurationValid() {
        return isPositive(deliveryTimeout)
                && isPositive(requestTimeout)
                && isNonNegative(retryBackoff)
                && isPositive(maxBlock)
                && isNonNegative(linger)
                && deliveryTimeout.compareTo(requestTimeout.plus(linger)) >= 0;
    }

    private boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }

    private boolean isNonNegative(Duration duration) {
        return duration != null && !duration.isNegative();
    }

    @Getter
    @Setter
    public static class CircuitBreakerSettings {

        @Min(1)
        private int slidingWindowSize = 50;
        @Min(1)
        private int minimumNumberOfCalls = 20;
        @DecimalMin(value = "0.0", inclusive = false)
        @DecimalMax("100.0")
        private float failureRateThreshold = 50F;
        @NotNull
        private Duration openStateWaitDuration = Duration.ofSeconds(10);
        @Min(1)
        private int permittedCallsInHalfOpenState = 5;

        @AssertTrue(message = "openStateWaitDuration must be greater than zero")
        public boolean isOpenStateWaitDurationPositive() {
            return openStateWaitDuration != null
                    && !openStateWaitDuration.isZero()
                    && !openStateWaitDuration.isNegative();
        }

        @AssertTrue(message = "minimumNumberOfCalls must not exceed slidingWindowSize")
        public boolean isMinimumNumberOfCallsWithinWindow() {
            return minimumNumberOfCalls <= slidingWindowSize;
        }
    }
}
