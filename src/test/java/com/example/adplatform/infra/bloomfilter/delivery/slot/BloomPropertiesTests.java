package com.example.adplatform.infra.bloomfilter.delivery.slot;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BloomPropertiesTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldAcceptDefaultConfiguration() {
        assertTrue(validator.validate(new BloomProperties()).isEmpty());
    }

    @Test
    void shouldAcceptCompleteConfiguration() {
        assertTrue(validator.validate(completeProperties()).isEmpty());
    }

    @Test
    void shouldRejectNonPositiveSchedulerDelay() {
        BloomProperties properties = completeProperties();
        properties.setRebuildDelay(Duration.ZERO);

        assertFalse(validator.validate(properties).isEmpty());
    }

    @Test
    void shouldRejectMaxCapacityLessThanInitialCapacity() {
        BloomProperties properties = completeProperties();
        properties.setInitialCapacity(10_000);
        properties.setMaxCapacity(9_999L);

        assertTrue(validator.validate(properties).stream()
                .anyMatch(violation -> violation.getMessage().equals(
                        "最大容量必须大于初始容量")));
    }

    private BloomProperties completeProperties() {
        BloomProperties properties = new BloomProperties();
        properties.setInitialCapacity(10_000);
        properties.setFalsePositiveProbability(0.01D);
        properties.setExpansionFactor(2D);
        properties.setMaxCapacity(1_000_000L);
        properties.setRebuildDelay(Duration.ofMinutes(5));
        properties.setRebuildInitialDelay(Duration.ofMinutes(5));
        properties.setExpansionCheckDelay(Duration.ofSeconds(30));
        properties.setExpansionCheckInitialDelay(Duration.ofSeconds(30));
        return properties;
    }
}
