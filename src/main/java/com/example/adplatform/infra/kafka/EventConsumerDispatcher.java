package com.example.adplatform.infra.kafka;

import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.tracking.message.EventMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/** 统一三条消费链路的异常语义与分阶段指标。 */
@Slf4j
@Component
public class EventConsumerDispatcher {

    private final Map<EventConsumerStage, StageMeters> meters = new EnumMap<>(EventConsumerStage.class);

    public EventConsumerDispatcher(MeterRegistry meterRegistry) {
        for (EventConsumerStage stage : EventConsumerStage.values()) {
            meters.put(stage, new StageMeters(
                    messageCounter(meterRegistry, stage, "success"),
                    messageCounter(meterRegistry, stage, "business_error"),
                    messageCounter(meterRegistry, stage, "failure"),
                    Timer.builder("ad.event.consumer.processing")
                            .description("广告事件各消费阶段的完整处理耗时")
                            .tag("stage", stage.metricTag())
                            .register(meterRegistry)));
        }
    }

    public void dispatch(
            EventConsumerStage stage,
            EventMessage message,
            Consumer<EventMessage> processor) {
        StageMeters stageMeters = meters.get(stage);
        Timer.Sample sample = Timer.start();
        try {
            processor.accept(message);
            stageMeters.success().increment();
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE) {
                stageMeters.failure().increment();
                log.warn("广告事件消费临时依赖失败，将交由 Kafka 重试，stage={}，eventId={}，原因={}",
                        stage.metricTag(), message.eventId(), ex.getMessage());
                throw ex;
            }
            stageMeters.businessError().increment();
            log.warn("广告事件消费业务校验失败，stage={}，eventId={}，原因={}",
                    stage.metricTag(), message.eventId(), ex.getMessage());
        } catch (RuntimeException ex) {
            stageMeters.failure().increment();
            log.error("广告事件消费失败，stage={}，eventId={}",
                    stage.metricTag(), message.eventId(), ex);
            throw ex;
        } finally {
            sample.stop(stageMeters.processing());
        }
    }

    private Counter messageCounter(
            MeterRegistry meterRegistry,
            EventConsumerStage stage,
            String result) {
        return Counter.builder("ad.event.consumer.messages")
                .description("广告事件各消费阶段的处理结果数量")
                .tag("stage", stage.metricTag())
                .tag("result", result)
                .register(meterRegistry);
    }

    private record StageMeters(
            Counter success,
            Counter businessError,
            Counter failure,
            Timer processing) {
    }
}
