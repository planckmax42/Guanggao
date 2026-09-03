package com.example.adplatform.infra.kafka.tracking;

import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.DependencyException;
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

    private final Map<EventConsumerStage, StageMeters> meters = new EnumMap<>(EventConsumerStage.class);//枚举map存储各个消费者的指标

    public EventConsumerDispatcher(MeterRegistry meterRegistry) {
        for (EventConsumerStage stage : EventConsumerStage.values()) {
            meters.put(stage, new StageMeters(
                    messageCounter(meterRegistry, stage, "success"),//消费成功计数
                    messageCounter(meterRegistry, stage, "business_error"),//消费错误计数
                    messageCounter(meterRegistry, stage, "failure"),//消费失败计数
                    Timer.builder("ad.event.consumer.processing")//消息处理计时器
                            .description("广告事件各消费阶段的完整处理耗时")
                            .tag("stage", stage.metricTag())
                            .register(meterRegistry)));
        }
    }

    public void dispatch(
            EventConsumerStage stage,
            EventMessage message,
            Consumer<EventMessage> processor) {
        StageMeters stageMeters = meters.get(stage);//取出指标记录器
        Timer.Sample sample = Timer.start();//计时开始
        try {//todo：分析不同层面的异常报错，以及重试机制（重试次数多进入死信队列？）
            processor.accept(message);
            stageMeters.success().increment();
        } catch (DependencyException ex) {
            stageMeters.failure().increment();
            log.warn("广告事件消费临时依赖失败，将交由 Kafka 重试，stage={}，eventId={}，原因={}",
                    stage.metricTag(), message.eventId(), ex.getMessage());
            throw ex;
        } catch (BusinessException ex) {
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
