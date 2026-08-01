package com.example.adplatform.infra.bloom.delivery.slot;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotBloomFilterPropertiesTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldRejectMissingRequiredConfiguration() {
        assertFalse(validator.validate(new SlotBloomFilterProperties()).isEmpty());
    }

    @Test
    void shouldAcceptCompleteConfiguration() {
        assertTrue(validator.validate(completeProperties()).isEmpty());
    }

    @Test
    void shouldRejectNonPositiveSchedulerDelay() {
        SlotBloomFilterProperties properties = completeProperties();
        properties.setRebuildDelay(Duration.ZERO);

        assertFalse(validator.validate(properties).isEmpty());
    }

    private SlotBloomFilterProperties completeProperties() {
        SlotBloomFilterProperties properties = new SlotBloomFilterProperties();
        properties.setExpectedInsertions(10_000);
        properties.setFalsePositiveProbability(0.01D);
        properties.setMinimumAbsentSamples(1_000L);
        properties.setExpansionFactor(2D);
        properties.setMaxExpectedInsertions(1_000_000L);
        properties.setExpansionCooldown(Duration.ofMinutes(10));
        properties.setRebuildDelay(Duration.ofMinutes(5));
        properties.setRebuildInitialDelay(Duration.ofMinutes(5));
        properties.setExpansionCheckDelay(Duration.ofSeconds(30));
        properties.setExpansionCheckInitialDelay(Duration.ofSeconds(30));
        return properties;
    }
}
