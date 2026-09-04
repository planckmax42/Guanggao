package com.example.adplatform.search.outbox.service;

import com.example.adplatform.search.outbox.entity.OutboxMessageEntity;
import com.example.adplatform.search.outbox.mapper.OutboxMessageMapper;
import com.example.adplatform.search.outbox.message.SlotCacheSyncMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SlotCacheOutboxServiceTests {

    private final OutboxMessageMapper mapper = mock(OutboxMessageMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SlotCacheOutboxService service = new SlotCacheOutboxService(mapper, objectMapper);

    @BeforeEach
    void setTopic() {
        ReflectionTestUtils.setField(service, "topic", "ad-slot-cache-sync");
    }

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void shouldWriteOrderedCacheMessageInsideBusinessTransaction() throws Exception {
        TransactionSynchronizationManager.setActualTransactionActive(true);

        service.append("slot_001", "OLD_CODE");

        ArgumentCaptor<OutboxMessageEntity> captor = ArgumentCaptor.forClass(OutboxMessageEntity.class);
        verify(mapper).insert(captor.capture());
        OutboxMessageEntity entity = captor.getValue();
        assertEquals("ad-slot-cache-sync", entity.getTopic());
        assertEquals("SLOT:slot_001", entity.getMessageKey());
        assertEquals(SlotCacheSyncMessage.class.getSimpleName(), entity.getMessageType());
        SlotCacheSyncMessage payload = objectMapper.readValue(entity.getPayload(), SlotCacheSyncMessage.class);
        assertEquals("slot_001", payload.slotPublicId());
        assertEquals("OLD_CODE", payload.previousSlotCode());
        assertEquals(entity.getEventId(), payload.eventId());
    }

    @Test
    void shouldRejectOutboxAppendOutsideTransaction() {
        assertThrows(IllegalStateException.class, () -> service.append("slot_001", null));
    }
}
