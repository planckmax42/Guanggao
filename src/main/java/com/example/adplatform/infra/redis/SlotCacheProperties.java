package com.example.adplatform.infra.redis;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 广告位缓存、布隆过滤器和 MySQL 回源熔断参数。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.slot-cache")
public class SlotCacheProperties {

    private Duration redisTtl = Duration.ofDays(1);
    private Bloom bloom = new Bloom();
    private MysqlCircuitBreaker mysqlCircuitBreaker = new MysqlCircuitBreaker();

    @Getter
    @Setter
    public static class Bloom {

        /**
         * 预计启用广告位数量，容量不足时应调大后触发重建。
         */
        private int expectedInsertions = 10_000;

        /**
         * 允许的误判率。误判只会多一次缓存或数据库查询，不会返回错误广告位。
         */
        private double falsePositiveProbability = 0.01D;

        /**
         * 至少收集这么多次“确认不存在”的请求后，才计算实际误判率。
         */
        private long minimumAbsentSamples = 1_000L;

        /**
         * 每次扩容后的容量倍数。
         */
        private double expansionFactor = 2D;

        /**
         * 自动扩容上限，防止异常流量造成布隆过滤器无限增长。
         */
        private long maxExpectedInsertions = 1_000_000L;

        /**
         * 两次自动扩容之间的最短间隔。
         */
        private Duration expansionCooldown = Duration.ofMinutes(10);
    }

    @Getter
    @Setter
    public static class MysqlCircuitBreaker {

        private int slidingWindowSize = 20;
        private int minimumNumberOfCalls = 10;
        private float failureRateThreshold = 50F;
        private float slowCallRateThreshold = 50F;
        private Duration slowCallDurationThreshold = Duration.ofMillis(200);
        private Duration openStateWaitDuration = Duration.ofSeconds(10);
        private int permittedCallsInHalfOpenState = 3;
    }
}
