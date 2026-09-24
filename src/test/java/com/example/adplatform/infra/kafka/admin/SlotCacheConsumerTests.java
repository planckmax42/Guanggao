package com.example.adplatform.infra.kafka.admin;

import com.example.adplatform.admin.port.slot.SlotCacheAdminPort;
import com.example.adplatform.search.outbox.message.SlotCacheSyncMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SlotCacheConsumerTests {

    @Test
    void shouldReconcileLatestMysqlStateAndRecordSuccess() throws Exception {
        SlotCacheAdminPort cachePort = mock(SlotCacheAdminPort.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObjectMapper objectMapper = new ObjectMapper();
        SlotCacheSyncMessage message = new SlotCacheSyncMessage("event_1", "slot_1", "OLD_CODE");
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "ad-slot-cache-sync", 0, 1L, "SLOT:slot_1", objectMapper.writeValueAsString(message));
        SlotCacheConsumer consumer = new SlotCacheConsumer(
                objectMapper, cachePort, registry);

        consumer.consumeWrite(record);

        verify(cachePort).reconcileSlot("slot_1", "OLD_CODE");
        assertEquals(1D, registry.counter(
                "ad.slot.cache.sync", "stage", "consumer", "result", "success").count());
    }

    @Test
    void shouldRejectMalformedPayloadBeforeTouchingCache() {
        SlotCacheAdminPort cachePort = mock(SlotCacheAdminPort.class);
        SlotCacheConsumer consumer = new SlotCacheConsumer(
                new ObjectMapper(), cachePort, new SimpleMeterRegistry());

        assertThrows(Exception.class, () -> consumer.consumeWrite(new ConsumerRecord<>(
                "ad-slot-cache-sync", 0, 1L, "bad", "{}")));
    }
}
