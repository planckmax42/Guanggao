package com.example.adplatform.infra.kafka;

import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.tracking.service.EventBillingProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** event-topic 的独立计费 Consumer Group。 */
@RequiredArgsConstructor
@Component
public class EventBillingKafkaConsumer {

    private final EventBillingProcessor eventBillingProcessor;
    private final EventConsumerDispatcher dispatcher;

    @KafkaListener(
            id = "event-billing-listener",
            topics = "${app.kafka.topics.event}",
            groupId = "${app.kafka.consumer-groups.billing}",
            autoStartup = "false")
    public void consume(EventMessage message) {
        dispatcher.dispatch(EventConsumerStage.BILLING, message, eventBillingProcessor::bill);
    }
}
