package com.example.adplatform.infra.redis.slot;

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

        SlotCacheProperties.Bloom bloom = properties.getBloom();
        bloom.setExpectedInsertions(10_000);
        bloom.setFalsePositiveProbability(0.01D);
        bloom.setMinimumAbsentSamples(1_000L);
        bloom.setExpansionFactor(2D);
        bloom.setMaxExpectedInsertions(1_000_000L);
        bloom.setExpansionCooldown(Duration.ofMinutes(10));

        SlotCacheProperties.MysqlCircuitBreaker circuitBreaker = properties.getMysqlCircuitBreaker();
        circuitBreaker.setSlidingWindowSize(20);
        circuitBreaker.setMinimumNumberOfCalls(10);
        circuitBreaker.setFailureRateThreshold(50F);
        circuitBreaker.setSlowCallRateThreshold(50F);
        circuitBreaker.setSlowCallDurationThreshold(Duration.ofMillis(200));
        circuitBreaker.setOpenStateWaitDuration(Duration.ofSeconds(10));
        circuitBreaker.setPermittedCallsInHalfOpenState(3);

        assertTrue(validator.validate(properties).isEmpty());
    }
}
