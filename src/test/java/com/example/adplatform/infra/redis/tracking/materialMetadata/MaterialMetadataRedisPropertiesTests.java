package com.example.adplatform.infra.redis.tracking.materialMetadata;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialMetadataRedisPropertiesTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldRejectMissingRequiredConfiguration() {
        assertFalse(validator.validate(new MaterialMetadataRedisProperties()).isEmpty());
    }

    @Test
    void shouldAcceptCompleteConfiguration() {
        MaterialMetadataRedisProperties properties = new MaterialMetadataRedisProperties();
        properties.setRedisTtl(Duration.ofHours(1));
        properties.setRedisTtlJitter(Duration.ofMinutes(10));
        properties.setSingleFlightWaitTimeout(Duration.ofMillis(500));
        properties.getLock().setStripes(1_024);
        properties.getLock().setReadWaitTimeout(Duration.ofMillis(100));

        assertTrue(validator.validate(properties).isEmpty());
    }
}
