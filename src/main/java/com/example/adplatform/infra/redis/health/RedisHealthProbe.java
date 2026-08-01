package com.example.adplatform.infra.redis.health;

import com.example.adplatform.health.port.RedisHealthPort;
import com.example.adplatform.health.response.ComponentHealthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/** Redis 连接健康检查实现。 */
@Component
@RequiredArgsConstructor
public class RedisHealthProbe implements RedisHealthPort {

    private final RedisConnectionFactory redisConnectionFactory;

    @Override
    public ComponentHealthResponse check() {
        long start = System.currentTimeMillis();
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            if ("PONG".equalsIgnoreCase(pong)) {
                return ComponentHealthResponse.up(System.currentTimeMillis() - start);
            }
            return ComponentHealthResponse.down("unexpected ping response: " + pong, System.currentTimeMillis() - start);
        } catch (RedisConnectionFailureException ex) {
            return ComponentHealthResponse.down(ex.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
