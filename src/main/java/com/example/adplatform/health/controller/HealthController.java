package com.example.adplatform.health.controller;

import com.example.adplatform.common.response.Result;
import com.example.adplatform.health.response.ComponentHealthResponse;
import com.example.adplatform.health.response.HealthResponse;
import com.example.adplatform.health.port.RedisHealthPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;

@RestController
public class HealthController {

    private final DataSource dataSource;
    private final RedisHealthPort redisHealthPort;
    private final String applicationName;

    public HealthController(
            DataSource dataSource,
            RedisHealthPort redisHealthPort,
            @Value("${spring.application.name}") String applicationName) {
        this.dataSource = dataSource;
        this.redisHealthPort = redisHealthPort;
        this.applicationName = applicationName;
    }

    /**
     * 检查应用健康状态，并验证 MySQL 和 Redis 连接是否正常。
     */
    @GetMapping("/api/health")
    public Result<HealthResponse> health() {
        ComponentHealthResponse mysql = checkMysql();
        ComponentHealthResponse redis = redisHealthPort.check();
        String status = isUp(mysql) && isUp(redis) ? "UP" : "DOWN";
        return Result.success(new HealthResponse(applicationName, status, mysql, redis, LocalDateTime.now()));
    }

    private ComponentHealthResponse checkMysql() {
        long start = System.currentTimeMillis();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(2);
            statement.execute("SELECT 1");
            return ComponentHealthResponse.up(System.currentTimeMillis() - start);
        } catch (Exception ex) {
            return ComponentHealthResponse.down(ex.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private boolean isUp(ComponentHealthResponse componentHealth) {
        return "UP".equals(componentHealth.status());
    }
}
