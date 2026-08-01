package com.example.adplatform.infra.resilience.delivery.slot;

import com.example.adplatform.infra.redis.delivery.slot.SlotCacheProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 保护广告位缓存回源 MySQL 的熔断器。
 *
 * <p>当 Redis 未命中需要查询 MySQL 时，业务代码通过此类执行查询。
 * Resilience4j 会统计调用的失败率和慢调用率，异常达到阈值后打开熔断器，
 * 暂时拒绝后续 MySQL 请求，避免数据库故障时请求持续堆积。</p>
 */
@Slf4j
@Component
public class SlotMysqlCircuitBreaker {

    /** Resilience4j 提供的熔断器实例，内部维护 CLOSED、OPEN 和 HALF_OPEN 状态。 */
    private final CircuitBreaker circuitBreaker;

    /**
     * 根据广告位缓存配置创建 MySQL 回源熔断器，并注册状态变更日志监听器。
     *
     * @param properties 包含滑动窗口、失败率、慢调用率和半开探测参数的配置
     * @throws IllegalArgumentException 熔断参数不符合 Resilience4j 约束时抛出
     */
    public SlotMysqlCircuitBreaker(SlotCacheProperties properties) {
        // 读取 app.slot-cache.mysql-circuit-breaker 下的项目配置。
        SlotCacheProperties.MysqlCircuitBreaker config = properties.getMysqlCircuitBreaker();

        /*
         * 构建 Resilience4j 熔断规则。这些配置只负责统计调用和切换熔断状态，
         * 不会替代数据库驱动本身的连接超时或 SQL 执行超时配置。
         */
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                // 按最近 N 次调用统计，而不是按最近一段时间统计。
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                // 滑动窗口最多保留的调用数，本项目默认为最近 20 次。
                .slidingWindowSize(config.getSlidingWindowSize())
                // 至少收集这么多次调用后才计算失败率/慢调用率，避免样本太少就熔断。
                .minimumNumberOfCalls(config.getMinimumNumberOfCalls())
                // 失败调用百分比达到该阈值时进入 OPEN，本项目默认为 50%。
                .failureRateThreshold(config.getFailureRateThreshold())
                // 慢调用百分比达到该阈值时也进入 OPEN，本项目默认为 50%。
                .slowCallRateThreshold(config.getSlowCallRateThreshold())
                // 单次调用超过该时长就记为慢调用，本项目默认为 200ms。
                .slowCallDurationThreshold(config.getSlowCallDurationThreshold())
                // OPEN 状态维持的时间，期间请求会被直接拒绝；本项目默认为 10s。
                .waitDurationInOpenState(config.getOpenStateWaitDuration())
                // 进入 HALF_OPEN 后允许执行的试探调用数，本项目默认为 3 次。
                .permittedNumberOfCallsInHalfOpenState(config.getPermittedCallsInHalfOpenState())
                // OPEN 等待时间结束后自动转为 HALF_OPEN，无需先来一次业务请求触发转换。
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();

        // 使用上述规则创建名为 slotMysqlFallback 的熔断器。
        this.circuitBreaker = CircuitBreaker.of("slotMysqlFallback", circuitBreakerConfig);

        // 只在熔断器状态发生变化时记录日志，便于观察 MySQL 回源是否已熔断或恢复。
        this.circuitBreaker.getEventPublisher().onStateTransition(event ->
                log.warn("广告位 MySQL 回源熔断器状态变化：{} -> {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()));
    }

    /**
     * 在熔断器保护下执行一个无参数、有返回值的操作。
     *
     * <p>CLOSED 状态会执行 {@code supplier.get()}并记录结果；
     * OPEN 状态不会执行 supplier，而是抛出 {@code CallNotPermittedException}。</p>
     *
     * @param supplier 由熔断器决定是否执行的无参数操作
     * @param <T> 操作结果类型
     * @return supplier 执行结果
     * @throws io.github.resilience4j.circuitbreaker.CallNotPermittedException 熔断器当前拒绝调用时抛出
     * @throws RuntimeException supplier 执行失败时原样向上抛出
     */
    public <T> T execute(Supplier<T> supplier) {
        return circuitBreaker.executeSupplier(supplier);
    }

    /**
     * 返回熔断器当前状态，可用于健康检查或监控。
     *
     * @return Resilience4j 熔断器当前状态
     */
    public CircuitBreaker.State currentState() {
        return circuitBreaker.getState();
    }
}
