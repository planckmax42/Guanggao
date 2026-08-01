package com.example.adplatform.infra.redis.delivery.slot;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotCachePropertiesTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldRejectMissingRequiredConfiguration() {
        assertFalse(validator.validate(new SlotCacheProperties()).isEmpty());
    }

    @Test
    void shouldAcceptCompleteConfiguration() {
        SlotCacheProperties properties = new SlotCacheProperties();
        properties.setRedisTtl(Duration.ofDays(1));

        SlotCacheProperties.Lock lock = properties.getLock();
        lock.setStripes(1_024);
        lock.setReadWaitTimeout(Duration.ofMillis(100));

        assertTrue(validator.validate(properties).isEmpty());
    }
}
