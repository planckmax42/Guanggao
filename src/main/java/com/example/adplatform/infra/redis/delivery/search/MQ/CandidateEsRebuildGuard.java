package com.example.adplatform.infra.redis.delivery.search.MQ;

import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CandidateEsRebuildGuard {

    private final StringRedisTemplate stringRedisTemplate;
    private final String REBUILD_LOCK = "delivery:es:candidate:rebuild-lock";

    public String acquireLock(){
        String lockToken = UUID.randomUUID().toString();//生成锁唯一标识
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(REBUILD_LOCK, lockToken, Duration.ofDays(1));//获取Redis分布式锁，todo：多实例情况下在解决什么问题
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "候选索引正在重建");
        }
        return lockToken;
    }

    public void releaseLock(String lockToken){
        if (lockToken.equals(stringRedisTemplate.opsForValue().get(REBUILD_LOCK))) {
            stringRedisTemplate.delete(REBUILD_LOCK);
        }
    }
}
