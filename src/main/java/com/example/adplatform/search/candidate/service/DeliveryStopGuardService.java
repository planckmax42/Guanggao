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

/**
 * 配置变更最终一致窗口内的 Redis 紧急停投保护。
 *
 * <p>计划、素材或广告位被暂停后，事务提交监听器立即把 ID 写入 stopped set；待 Kafka
 * 消费者已更新并 refresh ES 后再移除。读取使用 pipeline 批量执行，避免候选数量增加时
 * 产生 N 次网络往返。Redis 故障时当前策略为 fail-open，以保证投放接口可用。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryStopGuardService {

    private static final String STOPPED_PLANS = "delivery:stopped:plans";
    private static final String STOPPED_MATERIALS = "delivery:stopped:materials";
    private static final String STOPPED_SLOTS = "delivery:stopped:slots";

    private final StringRedisTemplate stringRedisTemplate;

    /** 添加或移除某个聚合的临时停投标记。定向规则本身不需要独立 stopped set。 */
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
        if (ids == null || ids.isEmpty()) {//传入参数为空直接返回
            return Set.of();
        }
        List<Object> results;
        try {
            // 一条 Redis pipeline 承载全部 SISMEMBER，返回顺序与 ids 遍历顺序一致。
            results = stringRedisTemplate.executePipelined(//批量调用集合成员存在判断函数，减少网络开销
                    (RedisCallback<Object>) connection -> {
                ids.forEach(id -> connection.setCommands().sIsMember(key.getBytes(), id.toString().getBytes()));
                return null;
            });
        } catch (RuntimeException ex) {
            log.warn("Failed to read delivery stop guards, key={}, fail-open", key, ex);
            return Set.of();
        }
        Set<Long> stopped = new HashSet<>();
        int index = 0;
        for (Long id : ids) {//遍历集合得到停用Id
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
