package com.example.adplatform.infra.redis.delivery.frequency.frequencyMQ;

import com.example.adplatform.delivery.portMQ.DeliveryFrequencyControlPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 用户每日素材曝光频控：Key 为日期和用户，Hash 保存素材 ID 与曝光次数。 */
@Service
@RequiredArgsConstructor
public class FrequencyControlService implements DeliveryFrequencyControlPort {

    private static final long TTL_SECONDS = Duration.ofDays(2).toSeconds();

    private static final RedisScript<Long> RECORD_EVENT_SCRIPT = RedisScript.of("""
            local count = redis.call('HINCRBY', KEYS[1], ARGV[1], 1)
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            return count
            """, Long.class);

    private final StringRedisTemplate stringRedisTemplate;


    public void recordEvent(Long viewerId, Long materialId, LocalDate statDate) {
        stringRedisTemplate.execute(
                RECORD_EVENT_SCRIPT,
                List.of(buildKey(viewerId, statDate)),
                materialId.toString(),
                String.valueOf(TTL_SECONDS));
    }

    public Set<Long> findAllowedMaterials(
            Long viewerId, Collection<Long> materialIds, LocalDate statDate, int maxFrequency) {
        Set<Long> allowed = new LinkedHashSet<>(materialIds);
        if (maxFrequency <= 0) {
            return allowed;
        }
        List<Long> ids = List.copyOf(allowed);
        List<String> fields = ids.stream().map(String::valueOf).toList();
        List<String> counts = stringRedisTemplate.<String, String>opsForHash()
                .multiGet(buildKey(viewerId, statDate), fields);
        for (int i = 0; i < ids.size(); i++) {
            String count = counts.get(i);
            if (count != null && Long.parseLong(count) >= maxFrequency) {
                allowed.remove(ids.get(i));
            }
        }
        return allowed;
    }

    private String buildKey(Long viewerId, LocalDate statDate) {
        return "frequency:user:" + statDate + ":" + viewerId;
    }
}
