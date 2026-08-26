package com.example.adplatform.infra.kafka.tracking;

import com.example.adplatform.infra.kafka.admin.SearchKafkaConfig;
import com.example.adplatform.tracking.message.EventMessage;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EventKafkaProducerConfigTests {

    @Test
    void shouldOverrideReliabilityOnlyForEventTemplate() {
        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.getProducer().setAcks("1");
        SslBundles sslBundles = mock(SslBundles.class);
        EventKafkaProducerProperties eventProperties =
                new EventKafkaProducerProperties();

        KafkaTemplate<String, EventMessage> eventTemplate =
                new EventKafkaProducerConfig().eventKafkaTemplate(
                        kafkaProperties, sslBundles, eventProperties);
        KafkaTemplate<String, String> outboxTemplate =
                new SearchKafkaConfig().outboxKafkaTemplate(
                        kafkaProperties, sslBundles);

        Map<String, Object> eventConfig = configuration(eventTemplate);
        Map<String, Object> outboxConfig = configuration(outboxTemplate);

        assertThat(eventConfig)
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
                .containsEntry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10_000)
                .containsEntry(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 3_000)
                .containsEntry(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 200L)
                .containsEntry(ProducerConfig.MAX_BLOCK_MS_CONFIG, 1_000L)
                .containsEntry(ProducerConfig.LINGER_MS_CONFIG, 5L)
                .containsEntry(ProducerConfig.BATCH_SIZE_CONFIG, 65_536)
                .containsEntry(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        assertThat(outboxConfig)
                .containsEntry(ProducerConfig.ACKS_CONFIG, "1")
                .doesNotContainKey(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG)
                .doesNotContainKey(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> configuration(KafkaTemplate<?, ?> template) {
        return ((DefaultKafkaProducerFactory<Object, Object>)
                template.getProducerFactory()).getConfigurationProperties();
    }
}
