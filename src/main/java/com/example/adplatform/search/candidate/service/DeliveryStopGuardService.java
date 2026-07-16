package com.example.adplatform.search.candidate.service;

import com.example.adplatform.search.outbox.message.ConfigAggregateType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryStopGuardService {

    private static final String STOPPED_PLANS = "delivery:stopped:plans";
    private static final String STOPPED_MATERIALS = "delivery:stopped:materials";
    private static final String STOPPED_SLOTS = "delivery:stopped:slots";

    private final StringRedisTemplate stringRedisTemplate;

    public void mark(ConfigAggregateType type, Long id, boolean stopped) {
        String key = keyFor(type);
        if (key == null || id == null) {
            return;
        }
        try {
            if (stopped) {
                stringRedisTemplate.opsForSet().add(key, id.toString());
            } else {
                stringRedisTemplate.opsForSet().remove(key, id.toString());
            }
        } catch (RuntimeException ex) {
            log.warn("Failed to update delivery stop guard, type={}, id={}, stopped={}", type, id, stopped, ex);
        }
    }

    public Set<Long> findStoppedPlans(Collection<Long> planIds) {
        return findMembers(STOPPED_PLANS, planIds);
    }

    public Set<Long> findStoppedMaterials(Collection<Long> materialIds) {
        return findMembers(STOPPED_MATERIALS, materialIds);
    }

    public Set<Long> findStoppedSlots(Collection<Long> slotIds) {
        return findMembers(STOPPED_SLOTS, slotIds);
    }

    private Set<Long> findMembers(String key, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        List<Object> results;
        try {
            results = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                ids.forEach(id -> connection.setCommands().sIsMember(
                        key.getBytes(), id.toString().getBytes()));
                return null;
            });
        } catch (RuntimeException ex) {
            log.warn("Failed to read delivery stop guards, key={}, fail-open", key, ex);
            return Set.of();
        }
        Set<Long> stopped = new HashSet<>();
        int index = 0;
        for (Long id : ids) {
            if (index < results.size() && Boolean.TRUE.equals(results.get(index))) {
                stopped.add(id);
            }
            index++;
        }
        return stopped;
    }

    private String keyFor(ConfigAggregateType type) {
        return switch (type) {
            case PLAN -> STOPPED_PLANS;
            case MATERIAL -> STOPPED_MATERIALS;
            case SLOT -> STOPPED_SLOTS;
            case RULE -> null;
        };
    }
}
