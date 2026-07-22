package com.example.adplatform.infra.redis;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedisLuaScriptResourcesTests {

    @Test
    void shouldLoadAllRedisLuaScriptsFromClasspath() {
        List<String> paths = List.of(
                "redis/scripts/try-charge.lua",
                "redis/scripts/record-event.lua",
                "redis/scripts/record-cost.lua",
                "redis/scripts/pop-daily-stats.lua");

        for (String path : paths) {
            RedisScript<Object> script = RedisScript.of(new ClassPathResource(path), Object.class);

            assertThat(script.getScriptAsString())
                    .as("Lua script %s", path)
                    .isNotBlank()
                    .contains("redis.call");
            assertThat(script.getSha1())
                    .as("Lua script SHA1 %s", path)
                    .isNotBlank();
        }
    }
}
