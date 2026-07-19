package com.example.adplatform.infra.redis.event;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventMetadataCachePropertiesTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldRejectMissingRequiredConfiguration() {
        assertFalse(validator.validate(new EventMetadataCacheProperties()).isEmpty());
    }

    @Test
    void shouldAcceptCompleteConfiguration() {
        EventMetadataCacheProperties properties = new EventMetadataCacheProperties();
        properties.setRedisTtl(Duration.ofHours(1));
        properties.setRedisTtlJitter(Duration.ofMinutes(10));
        properties.setSingleFlightWaitTimeout(Duration.ofMillis(500));
        properties.getLock().setStripes(1_024);
        properties.getLock().setReadWaitTimeout(Duration.ofMillis(100));

        EventMetadataCacheProperties.Bloom bloom = properties.getBloom();
        bloom.setExpectedInsertions(100_000L);
        bloom.setFalsePositiveProbability(0.01D);
        bloom.setExpansionFactor(2D);
        bloom.setMaxExpectedInsertions(5_000_000L);
        bloom.setExpansionCooldown(Duration.ofMinutes(10));

        assertTrue(validator.validate(properties).isEmpty());
    }
}
