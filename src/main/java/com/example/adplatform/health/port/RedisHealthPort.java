package com.example.adplatform.health.port;

import com.example.adplatform.health.response.ComponentHealthResponse;

/** Redis 健康检查端口。 */
public interface RedisHealthPort {

    ComponentHealthResponse check();
}
