package com.example.adplatform.infra.redis.tracking.materialMetadata;

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

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.event-metadata-cache")
public class MaterialMetadataRedisProperties {//todo：后续优化一下，把配置都留在yml文件里面

    @NotNull
    private Duration redisTtl;
    @NotNull
    private Duration redisTtlJitter;
    @NotNull
    private Duration singleFlightWaitTimeout;
    @Valid
    @NotNull
    private Lock lock = new Lock();

    @AssertTrue(message = "singleFlightWaitTimeout must be greater than zero")
    public boolean isSingleFlightWaitTimeoutPositive() {
        return singleFlightWaitTimeout != null
                && !singleFlightWaitTimeout.isZero()
                && !singleFlightWaitTimeout.isNegative();
    }

    @Getter
    @Setter
    public static class Lock {
        @Min(1)
        private int stripes;
        @NotNull
        private Duration readWaitTimeout;

        @AssertTrue(message = "readWaitTimeout must be greater than zero")
        public boolean isReadWaitTimeoutPositive() {
            return readWaitTimeout != null
                    && !readWaitTimeout.isZero()
                    && !readWaitTimeout.isNegative();
        }
    }
}
