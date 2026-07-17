package com.example.adplatform.infra.kafka;

import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.tracking.service.EventArchiveProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** event-topic 的事件明细归档 Consumer Group。 */
@RequiredArgsConstructor
@Component
public class EventArchiveKafkaConsumer {

    private final EventArchiveProcessor eventArchiveProcessor;
    private final EventConsumerDispatcher dispatcher;

    @KafkaListener(
            id = "event-archive-listener",
            topics = "${app.kafka.topics.event}",
            groupId = "${app.kafka.consumer-groups.archive}",
            autoStartup = "false")
    public void consume(EventMessage message) {
        dispatcher.dispatch(EventConsumerStage.ARCHIVE, message, eventArchiveProcessor::archive);
    }
}
