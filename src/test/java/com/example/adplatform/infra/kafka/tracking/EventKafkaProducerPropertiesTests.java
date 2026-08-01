package com.example.adplatform.infra.kafka.tracking;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventKafkaProducerPropertiesTests {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldAcceptDefaultConfiguration() {
        assertTrue(validator.validate(new EventKafkaProducerProperties()).isEmpty());
    }

    @Test
    void shouldRejectDeliveryTimeoutShorterThanRequestAndLinger() {
        EventKafkaProducerProperties properties =
                new EventKafkaProducerProperties();
        properties.setDeliveryTimeout(Duration.ofSeconds(2));
        properties.setRequestTimeout(Duration.ofSeconds(3));

        assertFalse(validator.validate(properties).isEmpty());
    }

    @Test
    void shouldRejectCircuitMinimumCallsLargerThanWindow() {
        EventKafkaProducerProperties properties =
                new EventKafkaProducerProperties();
        properties.getCircuitBreaker().setSlidingWindowSize(10);
        properties.getCircuitBreaker().setMinimumNumberOfCalls(11);

        assertFalse(validator.validate(properties).isEmpty());
    }
}
