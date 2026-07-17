package com.example.adplatform.infra.kafka;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventKafkaConsumerGroupTests {

    @Test
    void shouldUseIndependentConsumerGroupsForEachStage() throws Exception {
        assertGroup(
                EventArchiveKafkaConsumer.class,
                "event-archive-listener",
                "${app.kafka.consumer-groups.archive}");
        assertGroup(
                EventBillingKafkaConsumer.class,
                "event-billing-listener",
                "${app.kafka.consumer-groups.billing}");
        assertGroup(
                EventStatisticsKafkaConsumer.class,
                "event-statistics-listener",
                "${app.kafka.consumer-groups.statistics}");
    }

    private void assertGroup(Class<?> consumerType, String expectedId, String expectedGroup) throws Exception {
        Method consume = consumerType.getMethod(
                "consume",
                com.example.adplatform.tracking.message.EventMessage.class);
        KafkaListener listener = consume.getAnnotation(KafkaListener.class);
        assertEquals(expectedId, listener.id());
        assertEquals(expectedGroup, listener.groupId());
        assertEquals("${app.kafka.topics.event}", listener.topics()[0]);
        assertEquals("false", listener.autoStartup());
    }
}
