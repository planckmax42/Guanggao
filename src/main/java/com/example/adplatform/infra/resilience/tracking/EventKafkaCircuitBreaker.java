package com.example.adplatform.infra.resilience.tracking;

import com.example.adplatform.infra.kafka.tracking.EventKafkaProducerProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 对异步 Kafka 发送结果计数，在持续故障时快速拒绝新的事件发送。
 */
@Slf4j
@Component
public class EventKafkaCircuitBreaker {

    private final CircuitBreaker circuitBreaker;

    public EventKafkaCircuitBreaker(EventKafkaProducerProperties properties,//用户自定义Kafka熔断器配置
                                    MeterRegistry meterRegistry) {//
        EventKafkaProducerProperties.CircuitBreakerSettings settings = properties.getCircuitBreaker();//取出用户配置的嵌套熔断器配置类
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()//创建配置构建器，后续链式添加配置
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)//设置滑动窗口为最近次数而不是最近时间
                .slidingWindowSize(settings.getSlidingWindowSize())//滑动窗口大小为最近50次
                .minimumNumberOfCalls(settings.getMinimumNumberOfCalls())//至少20次开始判断
                .failureRateThreshold(settings.getFailureRateThreshold())//失败率达到50进入Open开始熔断
                .waitDurationInOpenState(settings.getOpenStateWaitDuration())//打开后等待10s
                .permittedNumberOfCallsInHalfOpenState(settings.getPermittedCallsInHalfOpenState())//设置试探次数为5
                .build();
        this.circuitBreaker = CircuitBreaker.of("eventKafkaProducer", config);//创建熔断器，导入名字和规则
        this.circuitBreaker.getEventPublisher().onStateTransition(event ->//注册事件监听器，状态变化的时候自动执行Lambda输出日志
                log.warn("事件 Kafka Producer 熔断器状态变化：{} -> {}", event.getStateTransition().getFromState(), event.getStateTransition().getToState()));
        Gauge.builder("ad.event.producer.circuit.open",//指标名称
                        circuitBreaker,//指标对象
                        breaker -> breaker.getState() == CircuitBreaker.State.OPEN ? 1D : 0D)//对于指标对象执行的Lamba表达式
                .description("Whether the event Kafka producer circuit breaker is open")//指标描述
                .register(meterRegistry);//登记到指标中心
    }
    public <T> CompletionStage<T> execute(Supplier<CompletionStage<T>> operation) {//函数返回T类型的结果/一个异常，todo:补习一下异常处理的逻辑
        return circuitBreaker.executeCompletionStage(operation);
    }
    public CircuitBreaker.State currentState() {
        return circuitBreaker.getState();//得到当前状态，用于测试代码
    }
}
