package com.example.adplatform.infra.resilience.delivery.slot;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotMysqlCircuitBreakerPropertiesTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldRejectMissingRequiredConfiguration() {
        assertFalse(validator.validate(new SlotMysqlCircuitBreakerProperties()).isEmpty());
    }

    @Test
    void shouldAcceptCompleteConfiguration() {
        SlotMysqlCircuitBreakerProperties properties = new SlotMysqlCircuitBreakerProperties();
        properties.setSlidingWindowSize(20);
        properties.setMinimumNumberOfCalls(10);
        properties.setFailureRateThreshold(50F);
        properties.setSlowCallRateThreshold(50F);
        properties.setSlowCallDurationThreshold(Duration.ofMillis(200));
        properties.setOpenStateWaitDuration(Duration.ofSeconds(10));
        properties.setPermittedCallsInHalfOpenState(3);

        assertTrue(validator.validate(properties).isEmpty());
    }
}
