package com.example.adplatform.infra.bloomfilter.tracking.materialMetadata;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class BloomPropertiesTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "app.event-metadata-cache.bloom.initial-capacity=100000",
                    "app.event-metadata-cache.bloom.false-positive-probability=0.01",
                    "app.event-metadata-cache.bloom.expansion-factor=2.0",
                    "app.event-metadata-cache.bloom.max-capacity=5000000",
                    "app.event-metadata-cache.bloom.rebuild-delay-ms=300000",
                    "app.event-metadata-cache.bloom.rebuild-initial-delay-ms=300000",
                    "app.event-metadata-cache.bloom.expansion-cooldown=10m",
                    "app.event-metadata-cache.bloom.expansion-check-delay-ms=30000",
                    "app.event-metadata-cache.bloom.expansion-check-initial-delay-ms=30000");

    @Test
    void shouldBindTrackingBloomConfiguration() {
        contextRunner.run(context -> {
            BloomProperties properties = context.getBean(BloomProperties.class);

            assertThat(properties.getInitialCapacity()).isEqualTo(100_000L);
            assertThat(properties.getMaxCapacity()).isEqualTo(5_000_000L);
            assertThat(properties.getExpansionCooldown()).isEqualTo(Duration.ofMinutes(10));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BloomProperties.class)
    static class TestConfiguration {
    }
}
