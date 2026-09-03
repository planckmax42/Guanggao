package com.example.adplatform.tracking.service.impl;

import com.example.adplatform.tracking.converter.EventConverter;
import com.example.adplatform.tracking.entity.EventType;
import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.tracking.port.EventPublisher;
import com.example.adplatform.tracking.request.EventRequest;
import com.example.adplatform.tracking.response.EventResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EventServiceImplTests {

    @Test
    void shouldBuildResponseOnlyAfterPublishCompletes() {
        EventPublisher publisher = mock(EventPublisher.class);
        EventConverter converter = mock(EventConverter.class);
        EventRequest request = request();
        EventMessage message = message();
        CompletableFuture<Void> publish = new CompletableFuture<>();
        when(converter.toMessage(request, EventType.IMPRESSION))
                .thenReturn(message);
        when(publisher.publish(message)).thenReturn(publish);
        EventServiceImpl service = new EventServiceImpl(publisher, converter);

        CompletableFuture<EventResponse> response =
                service.collect(request).toCompletableFuture();

        assertThat(response).isNotDone();
        publish.complete(null);
        assertThat(response.join()).isEqualTo(
                new EventResponse("event-1", "IMPRESSION", false, null));
    }

    private EventRequest request() {
        return new EventRequest(
                "event-1",
                "request-1",
                "IMPRESSION",
                "mat_00000000000000000000000000000001",
                2L,
                LocalDateTime.of(2026, 7, 23, 12, 0));
    }

    private EventMessage message() {
        return new EventMessage(
                "event-1",
                "request-1",
                EventType.IMPRESSION,
                "mat_00000000000000000000000000000001",
                2L,
                LocalDateTime.of(2026, 7, 23, 12, 0));
    }
}
