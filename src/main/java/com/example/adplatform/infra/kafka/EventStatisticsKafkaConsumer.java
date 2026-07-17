package com.example.adplatform.infra.kafka;

import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.tracking.service.EventStatisticsProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** event-topic 的独立实时统计 Consumer Group。 */
@RequiredArgsConstructor
@Component
public class EventStatisticsKafkaConsumer {

    private final EventStatisticsProcessor eventStatisticsProcessor;
    private final EventConsumerDispatcher dispatcher;

    @KafkaListener(
            id = "event-statistics-listener",
            topics = "${app.kafka.topics.event}",
            groupId = "${app.kafka.consumer-groups.statistics}",
            autoStartup = "false")
    public void consume(EventMessage message) {
        dispatcher.dispatch(EventConsumerStage.STATISTICS, message, eventStatisticsProcessor::record);
    }
}
