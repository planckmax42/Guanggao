package com.example.adplatform.infra.redis.delivery.search;

import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** 使用 Redis 协调候选索引全量重建与增量同步。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandidateIndexRebuildGuard {

    private static final String REBUILDING_KEY = "delivery:es:candidate:rebuilding";
    private static final String REBUILD_LOCK_KEY = "delivery:es:candidate:rebuild-lock";

    private final StringRedisTemplate stringRedisTemplate;

    public String acquire(Duration lockTtl) {
        String lockToken = UUID.randomUUID().toString();//生成锁唯一标识
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(REBUILD_LOCK_KEY, lockToken, lockTtl);//获取Redis分布式锁，todo：多实例情况下在解决什么问题
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "候选索引正在重建");
        }
        stringRedisTemplate.opsForValue().set(REBUILDING_KEY, lockToken, lockTtl);//设置标志位，告诉同步消费者此时不要向ES写入数据，让其稍后重试同步，避免产生重试冲突
        return lockToken;
    }

    public boolean isRebuilding() {
        return stringRedisTemplate.hasKey(REBUILDING_KEY);
    }

    public void release(String token) {
        try {
            // 只释放自己持有的锁，避免过期后误删另一个实例刚获取的新锁。
            if (token.equals(stringRedisTemplate.opsForValue().get(REBUILD_LOCK_KEY))) {
                stringRedisTemplate.delete(List.of(REBUILD_LOCK_KEY, REBUILDING_KEY));
            }
        } catch (RuntimeException ex) {
            log.warn("Failed to release candidate rebuild lock", ex);
        }
    }
}
