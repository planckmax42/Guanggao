package com.example.adplatform.infra.redis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.common.enums.CommonStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class SlotCacheServiceImpl implements SlotCacheService {

    private static final Logger log = LoggerFactory.getLogger(SlotCacheServiceImpl.class);
    private static final Duration SLOT_CACHE_TTL = Duration.ofDays(1);

    private final StringRedisTemplate stringRedisTemplate;
    private final SlotMapper slotMapper;

    @Override
    public Optional<Long> getEnabledSlotIdByCode(String slotCode) {
        if (!StringUtils.hasText(slotCode)) {
            return Optional.empty();
        }

        Optional<Long> cachedSlotId = getSlotIdFromRedis(slotCode);
        if (cachedSlotId.isPresent()) {
            return cachedSlotId;
        }

        SlotEntity slot = slotMapper.selectOne(new LambdaQueryWrapper<SlotEntity>()
                .eq(SlotEntity::getSlotCode, slotCode)
                .eq(SlotEntity::getStatus, CommonStatus.ENABLED));
        if (slot == null) {
            return Optional.empty();
        }
        cacheSlot(slot);
        return Optional.of(slot.getId());
    }

    @Override
    public void cacheSlot(SlotEntity slot) {
        if (slot == null || !StringUtils.hasText(slot.getSlotCode())) {
            return;
        }
        if (!Objects.equals(slot.getStatus(), CommonStatus.ENABLED)) {
            evictSlotCode(slot.getSlotCode());
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                    RedisKeyConstants.slotCodeToId(slot.getSlotCode()),
                    String.valueOf(slot.getId()),
                    SLOT_CACHE_TTL);
        } catch (RuntimeException ex) {
            log.warn("写入广告位缓存失败，slotCode={}，后续请求将回源 MySQL：{}", slot.getSlotCode(), ex.getMessage());
        }
    }

    @Override
    public void refreshSlot(SlotEntity slot, String oldSlotCode) {
        if (StringUtils.hasText(oldSlotCode) && (slot == null || !oldSlotCode.equals(slot.getSlotCode()))) {
            evictSlotCode(oldSlotCode);
        }
        cacheSlot(slot);
    }

    @Override
    public void warmUp() {
        List<SlotEntity> enabledSlots;
        try {
            enabledSlots = slotMapper.selectList(new LambdaQueryWrapper<SlotEntity>()
                    .eq(SlotEntity::getStatus, CommonStatus.ENABLED));
        } catch (RuntimeException ex) {
            log.warn("广告位缓存预热查询 MySQL 失败：{}", ex.getMessage());
            return;
        }

        int successCount = 0;
        for (SlotEntity slot : enabledSlots) {
            try {
                stringRedisTemplate.opsForValue().set(
                        RedisKeyConstants.slotCodeToId(slot.getSlotCode()),
                        String.valueOf(slot.getId()),
                        SLOT_CACHE_TTL);
                successCount++;
            } catch (RuntimeException ex) {
                log.warn("广告位缓存预热写入 Redis 失败，已停止本次预热：{}", ex.getMessage());
                return;
            }
        }
        log.info("广告位缓存预热完成，数量={}", successCount);
    }

    private Optional<Long> getSlotIdFromRedis(String slotCode) {
        String key = RedisKeyConstants.slotCodeToId(slotCode);
        String value;
        try {
            value = stringRedisTemplate.opsForValue().get(key);
        } catch (RuntimeException ex) {
            log.warn("读取广告位缓存失败，slotCode={}，本次请求回源 MySQL：{}", slotCode, ex.getMessage());
            return Optional.empty();
        }
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.valueOf(value));
        } catch (NumberFormatException ex) {
            evictSlotCode(slotCode);
            return Optional.empty();
        }
    }

    private void evictSlotCode(String slotCode) {
        if (!StringUtils.hasText(slotCode)) {
            return;
        }
        try {
            stringRedisTemplate.delete(RedisKeyConstants.slotCodeToId(slotCode));
        } catch (RuntimeException ex) {
            log.warn("删除广告位缓存失败，slotCode={}：{}", slotCode, ex.getMessage());
        }
    }
}
