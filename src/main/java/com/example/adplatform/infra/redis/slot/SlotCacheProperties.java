package com.example.adplatform.infra.redis.slot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 广告位缓存、布隆过滤器和 MySQL 回源熔断参数。
 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.slot-cache")
public class SlotCacheProperties {

    /** 广告位编码到 ID 的 Redis 映射缓存过期时间。 */
    @NotNull
    private Duration redisTtl;

    /** 广告位编码布隆过滤器参数。 */
    @Valid
    @NotNull
    private Bloom bloom = new Bloom();

    /** MySQL 缓存回源熔断器参数。 */
    @Valid
    @NotNull
    private MysqlCircuitBreaker mysqlCircuitBreaker = new MysqlCircuitBreaker();

    /** 广告位编码布隆过滤器的容量、误判率和自动扩容配置。 */
    @Getter
    @Setter
    public static class Bloom {

        /**
         * 预计启用广告位数量，容量不足时应调大后触发重建。
         */
        @Min(1)
        private int expectedInsertions;

        /**
         * 允许的误判率。误判只会多一次缓存或数据库查询，不会返回错误广告位。
         */
        @DecimalMin(value = "0.0", inclusive = false)
        @DecimalMax(value = "1.0", inclusive = false)
        private double falsePositiveProbability;

        /**
         * 至少收集这么多次“确认不存在”的请求后，才计算实际误判率。
         */
        @Min(1)
        private long minimumAbsentSamples;

        /**
         * 每次扩容后的容量倍数。
         */
        @DecimalMin(value = "1.0", inclusive = false)
        private double expansionFactor;

        /**
         * 自动扩容上限，防止异常流量造成布隆过滤器无限增长。
         */
        @Min(1)
        private long maxExpectedInsertions;

        /**
         * 两次自动扩容之间的最短间隔。
         */
        @NotNull
        private Duration expansionCooldown;
    }

    /** 广告位缓存回源 MySQL 时使用的熔断窗口和状态转换参数。 */
    @Getter
    @Setter
    public static class MysqlCircuitBreaker {

        /** 基于调用次数的滑动窗口大小。 */
        @Min(1)
        private int slidingWindowSize;

        /** 开始计算失败率和慢调用率前需要收集的最少调用数。 */
        @Min(1)
        private int minimumNumberOfCalls;

        /** 触发熔断的失败调用百分比阈值。 */
        @DecimalMin(value = "0.0", inclusive = false)
        @DecimalMax("100.0")
        private float failureRateThreshold;

        /** 触发熔断的慢调用百分比阈值。 */
        @DecimalMin(value = "0.0", inclusive = false)
        @DecimalMax("100.0")
        private float slowCallRateThreshold;

        /** 超过该时长的 MySQL 回源调用会被记为慢调用。 */
        @NotNull
        private Duration slowCallDurationThreshold;

        /** 熔断器在 OPEN 状态下拒绝请求的持续时间。 */
        @NotNull
        private Duration openStateWaitDuration;

        /** HALF_OPEN 状态允许通过的试探调用数。 */
        @Min(1)
        private int permittedCallsInHalfOpenState;
    }
}
