package com.example.adplatform.infra.redis;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;

@RequiredArgsConstructor
@Service
public class FrequencyRedisServiceImpl implements FrequencyRedisService {

    private static final Logger log = LoggerFactory.getLogger(FrequencyRedisServiceImpl.class);
    private static final Duration FREQUENCY_TTL = Duration.ofDays(2);

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean isViewerPlanFrequencyExceeded(Long viewerId, Long planId, LocalDate statDate, int maxFrequency) {
        if (viewerId == null || planId == null || maxFrequency <= 0) {
            return false;
        }
        String key = RedisKeyConstants.viewerPlanFrequency(viewerId, planId, statDate);
        try {
            String value = stringRedisTemplate.opsForValue().get(key);
            long current = value == null ? 0L : Long.parseLong(value);
            return current >= maxFrequency;
        } catch (RuntimeException ex) {
            log.warn("读取用户频控缓存失败，viewerId={}，planId={}，本次投放放行：{}", viewerId, planId, ex.getMessage());
            return false;
        }
    }

    @Override
    public void incrementViewerPlanImpression(Long viewerId, Long planId, LocalDate statDate) {
        if (viewerId == null || planId == null) {
            return;
        }
        String key = RedisKeyConstants.viewerPlanFrequency(viewerId, planId, statDate);
        try {
            stringRedisTemplate.opsForValue().increment(key);
            stringRedisTemplate.expire(key, FREQUENCY_TTL);
        } catch (RuntimeException ex) {
            log.warn("写入用户频控缓存失败，viewerId={}，planId={}：{}", viewerId, planId, ex.getMessage());
        }
    }
}
