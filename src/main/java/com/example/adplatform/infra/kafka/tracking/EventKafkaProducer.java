package com.example.adplatform.infra.kafka.tracking;

import com.example.adplatform.common.exception.DependencyException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.tracking.message.EventMessage;
import com.example.adplatform.tracking.port.EventPublisher;
import com.example.adplatform.infra.resilience.tracking.EventKafkaCircuitBreaker;
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
import java.util.concurrent.CompletableFuture;
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

    private final KafkaTemplate<String, EventMessage> kafkaTemplate;//kafaka操作模板
    private final EventKafkaCircuitBreaker circuitBreaker;//kafka熔断器
    private final MeterRegistry meterRegistry;//指标信息总登记，后续暴露给普罗米修斯
    private final Counter successCounter;//成功发送消息数计数器
    private final Counter retryableFailureCounter;//RetriableException异常计数器
    private final Counter nonRetryableFailureCounter;//非熔断异常与RetriableException异常计数器
    private final Counter circuitOpenCounter;//熔断拒绝计数器
    private final Map<String, Timer> acknowledgementTimers;//上述四个指标计时器，todo:具体这些指标在记录什么，要看调用处具体做了什么

    public EventKafkaProducer(
            @Qualifier("eventKafkaTemplate")
            KafkaTemplate<String, EventMessage> kafkaTemplate,
            EventKafkaCircuitBreaker circuitBreaker,
            MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;//kafka工具模板
        this.circuitBreaker = circuitBreaker;//熔断器
        this.meterRegistry = meterRegistry;//参数：普罗米休斯指标注册中心，项目引入依赖后Spring Boot自动创建对象并管理，用于注入
        this.successCounter = counter(meterRegistry, "success");//四个计数器
        this.retryableFailureCounter = counter(meterRegistry, "retryable_failure");
        this.nonRetryableFailureCounter = counter(meterRegistry, "non_retryable_failure");
        this.circuitOpenCounter = counter(meterRegistry, "circuit_open");
        this.acknowledgementTimers = Map.of(//map集合包裹的四个计时器，todo:为什么计时器不用map包裹
                "success", acknowledgementTimer(meterRegistry, "success"),
                "retryable_failure", acknowledgementTimer(meterRegistry, "retryable_failure"),
                "non_retryable_failure", acknowledgementTimer(meterRegistry, "non_retryable_failure"),
                "circuit_open", acknowledgementTimer(meterRegistry, "circuit_open"));
    }
    private Counter counter(MeterRegistry registry, String result) {
        return Counter.builder("ad.event.producer.messages")//指定指标名称
                .description("Event Kafka producer outcomes")//设置指标描述
                .tag("result", result)//给指标设置标签，参数1：标签名称，参数2：标签值
                .register(registry);
    }
    private Timer acknowledgementTimer(
            MeterRegistry registry,
            String result) {
        return Timer.builder("ad.event.producer.ack")//指定指标名称
                .description("Time from event publish to Kafka acknowledgement or final failure")//设置指标描述
                .tag("result", result)//给指标设置标签，参数1：标签名称，参数2：标签值
                .publishPercentileHistogram()//便于计算P95,P99
                .register(registry);
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
    public CompletionStage<Void> publish(EventMessage message) {//生产者消息发布函数
        Timer.Sample sample = Timer.start(meterRegistry);
        CompletionStage<SendResult<String, EventMessage>> send;//对象返回的值可能是异常/消息发送结果（里面包含这个消息被发送到了哪个分区，哪个Topic,对应什么Offset）
        try {
            send = circuitBreaker.execute(//在熔断器的保护下发布消息
                    () -> kafkaTemplate.send(eventTopic, message.eventId(), message));
        } catch (CallNotPermittedException ex) {//捕捉熔断器拒绝异常
            sample.stop(acknowledgementTimers.get("circuit_open"));//记录到计时器
            circuitOpenCounter.increment();//记录到计数器
            log.warn("事件 Kafka Producer 已熔断，拒绝发送，eventId={}，topic={}", message.eventId(), eventTopic);
            return CompletableFuture.failedFuture(dependencyUnavailable(ex));//抛出熔断异常，最终被全局异常捕获器捕获然后返回503
        } catch (RuntimeException ex) {//捕获其他异常，在函数内拆包然后分别处理
            return CompletableFuture.failedFuture(handleSendFailure(message, ex, sample));
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

    private DependencyException handleSendFailure(
            EventMessage message,
            Throwable error,
            Timer.Sample sample) {
        Throwable cause = unwrap(error);
        if (cause instanceof CallNotPermittedException) {
            sample.stop(acknowledgementTimers.get("circuit_open"));
            circuitOpenCounter.increment();
            log.warn("事件 Kafka Producer 已熔断，拒绝发送，eventId={}，topic={}",
                    message.eventId(), eventTopic);
            return dependencyUnavailable(cause);
        }
        if (cause instanceof RetriableException) {
            sample.stop(acknowledgementTimers.get("retryable_failure"));
            retryableFailureCounter.increment();
            log.warn("Kafka 事件发送在投递时间窗内未成功，eventId={}，topic={}，原因={}",
                    message.eventId(), eventTopic, cause.toString());
            return dependencyUnavailable(cause);
        }
        sample.stop(acknowledgementTimers.get("non_retryable_failure"));
        nonRetryableFailureCounter.increment();
        log.error("Kafka 事件发送发生不可重试错误，eventId={}，topic={}",
                message.eventId(), eventTopic, cause);
        return new DependencyException(ErrorCode.SYSTEM_ERROR, "广告事件写入 Kafka 失败", cause);
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

    private DependencyException dependencyUnavailable(Throwable cause) {
        return new DependencyException(
                ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE,
                "Kafka 暂时不可用，请使用相同 eventId 稍后重试",
                cause);
    }

}
