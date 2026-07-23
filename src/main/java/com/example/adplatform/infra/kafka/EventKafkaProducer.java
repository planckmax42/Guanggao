package com.example.adplatform.infra.kafka;

import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.tracking.port.EventPublisher;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.common.errors.RetriableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaProducerException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

/**
 * 基于 Kafka 实现的广告事件发布适配器。
 *
 * <p>业务层仅依赖 {@link EventPublisher}，由该类负责选择 Topic、设置消息 Key，
 * 并以非阻塞方式等待 Broker 确认。</p>
 */
@Component
public class EventKafkaProducer implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventKafkaProducer.class);

    private final KafkaTemplate<String, EventMessage> kafkaTemplate;
    private final EventKafkaCircuitBreaker circuitBreaker;
    private final MeterRegistry meterRegistry;
    private final Counter successCounter;
    private final Counter retryableFailureCounter;
    private final Counter nonRetryableFailureCounter;
    private final Counter circuitOpenCounter;
    private final Map<String, Timer> acknowledgementTimers;

    public EventKafkaProducer(
            @Qualifier("eventKafkaTemplate")
            KafkaTemplate<String, EventMessage> kafkaTemplate,
            EventKafkaCircuitBreaker circuitBreaker,
            MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.circuitBreaker = circuitBreaker;
        this.meterRegistry = meterRegistry;
        this.successCounter = counter(meterRegistry, "success");
        this.retryableFailureCounter = counter(meterRegistry, "retryable_failure");
        this.nonRetryableFailureCounter = counter(meterRegistry, "non_retryable_failure");
        this.circuitOpenCounter = counter(meterRegistry, "circuit_open");
        this.acknowledgementTimers = Map.of(
                "success", acknowledgementTimer(meterRegistry, "success"),
                "retryable_failure", acknowledgementTimer(
                        meterRegistry, "retryable_failure"),
                "non_retryable_failure", acknowledgementTimer(
                        meterRegistry, "non_retryable_failure"),
                "circuit_open", acknowledgementTimer(
                        meterRegistry, "circuit_open"));
    }

    @Value("${app.kafka.topics.event}")
    private String eventTopic;

    /**
     * 异步等待 Kafka broker 确认写入，避免接口返回成功但消息实际没有进入 Kafka。
     *
     * @param message 待发布的广告事件消息
     * @return Broker 确认后正常完成、发送失败后异常完成的异步结果
     */
    @Override
    public CompletionStage<Void> publish(EventMessage message) {
        Timer.Sample sample = Timer.start(meterRegistry);
        CompletionStage<SendResult<String, EventMessage>> send;
        try {
            send = circuitBreaker.execute(
                    () -> kafkaTemplate.send(eventTopic, message.eventId(), message));
        } catch (CallNotPermittedException ex) {
            sample.stop(acknowledgementTimers.get("circuit_open"));
            circuitOpenCounter.increment();
            log.warn("事件 Kafka Producer 已熔断，拒绝发送，eventId={}，topic={}",
                    message.eventId(), eventTopic);
            return failedFuture(dependencyUnavailable());
        } catch (RuntimeException ex) {
            return failedFuture(handleSendFailure(message, ex, sample));
        }

        return send.handle((result, error) -> {
            if (error == null) {
                sample.stop(acknowledgementTimers.get("success"));
                successCounter.increment();
                return null;
            }
            throw handleSendFailure(message, error, sample);
        });
    }

    private BusinessException handleSendFailure(
            EventMessage message,
            Throwable error,
            Timer.Sample sample) {
        Throwable cause = unwrap(error);
        if (cause instanceof CallNotPermittedException) {
            sample.stop(acknowledgementTimers.get("circuit_open"));
            circuitOpenCounter.increment();
            log.warn("事件 Kafka Producer 已熔断，拒绝发送，eventId={}，topic={}",
                    message.eventId(), eventTopic);
            return dependencyUnavailable();
        }
        if (cause instanceof RetriableException) {
            sample.stop(acknowledgementTimers.get("retryable_failure"));
            retryableFailureCounter.increment();
            log.warn("Kafka 事件发送在投递时间窗内未成功，eventId={}，topic={}，原因={}",
                    message.eventId(), eventTopic, cause.toString());
            return dependencyUnavailable();
        }
        sample.stop(acknowledgementTimers.get("non_retryable_failure"));
        nonRetryableFailureCounter.increment();
        log.error("Kafka 事件发送发生不可重试错误，eventId={}，topic={}",
                message.eventId(), eventTopic, cause);
        return new BusinessException(ErrorCode.SYSTEM_ERROR, "广告事件写入 Kafka 失败");
    }

    private Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null
                && (current instanceof CompletionException
                || current instanceof ExecutionException
                || current instanceof KafkaProducerException)) {
            current = current.getCause();
        }
        return current;
    }

    private BusinessException dependencyUnavailable() {
        return new BusinessException(
                ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE,
                "Kafka 暂时不可用，请使用相同 eventId 稍后重试");
    }

    private CompletionStage<Void> failedFuture(Throwable error) {
        return java.util.concurrent.CompletableFuture.failedFuture(error);
    }

    private Counter counter(MeterRegistry registry, String result) {
        return Counter.builder("ad.event.producer.messages")
                .description("Event Kafka producer outcomes")
                .tag("result", result)
                .register(registry);
    }

    private Timer acknowledgementTimer(
            MeterRegistry registry,
            String result) {
        return Timer.builder("ad.event.producer.ack")
                .description("Time from event publish to Kafka acknowledgement or final failure")
                .tag("result", result)
                .publishPercentileHistogram()
                .register(registry);
    }
}
