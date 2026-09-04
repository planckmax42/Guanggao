package com.example.adplatform.infra.redis.delivery.slot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** 广告位 Redis 缓存参数。 */
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.slot-cache")
public class SlotCacheProperties {

    /** 广告位编码到 ID 的 Redis 映射缓存过期时间。 */
    @NotNull
    private Duration redisTtl;

    /** Redis 读取失败时的投放策略，默认停投优先。 */
    @NotNull
    private FailurePolicy failurePolicy = FailurePolicy.FAIL_CLOSED;

    /** 同类 Redis 异常完整堆栈的最小输出间隔。 */
    @NotNull
    private Duration failureLogInterval = Duration.ofSeconds(30);

    /** 单实例内协调缓存回填与提交后刷新的条带锁参数。 */
    @Valid
    @NotNull
    private Lock lock = new Lock();

    /** Slot 编码条带锁数量和缓存未命中读请求的最长等待时间。 */
    @Getter
    @Setter
    public static class Lock {

        /** 固定条带数量；编码哈希碰撞时共享同一把非公平互斥锁。 */
        @Min(1)
        private int stripes;

        /** 缓存未命中后等待同编码回填锁的最长时间。 */
        @NotNull
        private Duration readWaitTimeout;

        @AssertTrue(message = "readWaitTimeout must be greater than zero")
        public boolean isReadWaitTimeoutPositive() {
            return readWaitTimeout != null
                    && !readWaitTimeout.isZero()
                    && !readWaitTimeout.isNegative();
        }
    }

    public enum FailurePolicy {
        FAIL_CLOSED,
        FAIL_OPEN
    }

}
