package com.example.adplatform.infra.redis.delivery.slot;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotRedisPropertiesTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldRejectMissingRequiredConfiguration() {
        assertFalse(validator.validate(new SlotRedisProperties()).isEmpty());
    }

    @Test
    void shouldAcceptCompleteConfiguration() {
        SlotRedisProperties properties = new SlotRedisProperties();
        properties.setRedisTtl(Duration.ofDays(1));
        properties.setStripes(1_024);
        properties.setReadWaitTimeout(Duration.ofMillis(100));

        assertTrue(validator.validate(properties).isEmpty());
    }
}
