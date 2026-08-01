package com.example.adplatform.infra.redis.delivery.slot;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.adplatform.admin.entity.SlotEntity;
import com.example.adplatform.admin.mapper.SlotMapper;
import com.example.adplatform.admin.port.SlotCacheMaintenancePort;
import com.example.adplatform.common.enums.CommonStatus;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.delivery.port.SlotLookupPort;
import com.example.adplatform.infra.redis.delivery.DeliveryRedisKeys;
import com.example.adplatform.infra.bloom.delivery.slot.SlotBloomFilterTracker;
import com.example.adplatform.infra.bloom.delivery.slot.SlotBloomFilterService;
import com.example.adplatform.infra.resilience.delivery.slot.SlotMysqlCircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Optional;

/**
 * 广告位编码缓存的默认实现。
 *
 * <p>查询按“布隆过滤器→Redis→MySQL”顺序执行；缓存更新在数据库事务提交后进行，
 * 避免未提交数据进入缓存。</p>
 */
@RequiredArgsConstructor
@Service
public class SlotCacheServiceImpl implements SlotLookupPort, SlotCacheMaintenancePort {

    private static final Logger log = LoggerFactory.getLogger(SlotCacheServiceImpl.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final SlotMapper slotMapper;
    private final SlotCacheProperties properties;
    private final SlotBloomFilterService bloomFilterService;
    private final SlotBloomFilterTracker bloomFilterMetrics;
    private final SlotMysqlCircuitBreaker mysqlCircuitBreaker;
    private final SlotCacheLockManager lockManager;

    /** {@inheritDoc} */
    @Override
    public Optional<Long> getEnabledSlotIdByCode(String slotCode) {
        if (!StringUtils.hasText(slotCode)) {//防御性校验，防止绕过controller层传入非法参数
            return Optional.empty();
        }
        if (bloomFilterService.definitelyNotContains(slotCode)) {//布隆过滤器初筛
            bloomFilterMetrics.recordDefiniteMiss();//记录明确不存在数，用于后续计算误判率决定是否要扩容
            return Optional.empty();
        }

        Optional<Long> cachedSlotId = getSlotIdFromRedis(slotCode);//先走Redis
        if (cachedSlotId.isPresent()) {
            return cachedSlotId;
        }

        SlotCacheLockManager.LockHandle readLock = lockManager.tryAcquireForRead(slotCode).orElse(null);//嵌套类获取条带锁，todo:引入条带锁扩容机制，动态计算获取锁失败率决定是否扩容（目前想法）
        if (readLock == null) {//获取条带锁失败处理逻辑，todo：目前太糙，以及上面那个null，或许增加补偿机制？
            if (Thread.currentThread().isInterrupted()) {//失败原因为中断
                log.warn("广告位缓存回源锁等待被中断，slotCode={}", slotCode);
                throw new BusinessException(
                        ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE,
                        "广告位查询被中断，请稍后重试");
            }
            cachedSlotId = getSlotIdFromRedis(slotCode);//再走一次redis,是否在等待间隔其他线程回源成功
            if (cachedSlotId.isPresent()) {
                return cachedSlotId;
            }
            log.warn("广告位缓存回源锁等待超时，slotCode={}", slotCode);
            throw new BusinessException(
                    ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE,
                    "广告位查询繁忙，请稍后重试");
        }

        try (readLock) {//继承AutoCloseable类实现离开try代码块自动释放锁
            cachedSlotId = getSlotIdFromRedis(slotCode);
            if (cachedSlotId.isPresent()) {//真正进入mysql之前再次进行redis,最大程度上减少数据库压力
                return cachedSlotId;
            }
            if (bloomFilterService.definitelyNotContains(slotCode)) {//再次查询布隆过滤器是为了防止再此期间布隆过滤器重建完成
                bloomFilterMetrics.recordDefiniteMiss();
                return Optional.empty();
            }
            return loadEnabledSlotFromMysql(slotCode);//回源mysql
        }
    }

    private Optional<Long> loadEnabledSlotFromMysql(String slotCode) {
        SlotEntity slot;
        try {//在熔断器的保护下进入mysql查询
            slot = mysqlCircuitBreaker.execute(() -> selectEnabledSlotByCode(slotCode));
        } catch (CallNotPermittedException ex) {
            throw new BusinessException(
                    ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE,
                    "广告位查询服务已熔断，请稍后重试");
        } catch (RuntimeException ex) {
            log.warn("广告位缓存回源 MySQL 失败，slotCode={}", slotCode, ex);
            throw new BusinessException(
                    ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE,
                    "广告位查询服务暂时不可用，请稍后重试");
        }

        if (slot == null) {
            if (bloomFilterService.status().ready()) {
                bloomFilterMetrics.recordFalsePositive();//记录布隆过滤器误判数，用于计算误判率决定是否扩容
            }
            return Optional.empty();
        }
        cacheSlot(slot);//回源后缓存进redis和布隆过滤器
        return Optional.of(slot.getId());
    }

    /**
     * {@inheritDoc}
     * 将启用广告位写入 Redis；如果广告位已停用，则删除对应缓存。
     *
     * @param slot 待同步的广告位，为 {@code null} 时忽略
     */
    public void cacheSlot(SlotEntity slot) {
        if (slot == null || !StringUtils.hasText(slot.getSlotCode())) {
            return;
        }
        if (Objects.equals(slot.getStatus(), CommonStatus.ENABLED)) {
            bloomFilterService.put(slot.getSlotCode());
        }
        writeSlotToRedis(slot);
    }

    /** 只刷新 Redis，不改变布隆过滤器；用于数据库提交后的缓存同步。 */
    private void writeSlotToRedis(SlotEntity slot) {
        if (slot == null || !StringUtils.hasText(slot.getSlotCode())) {
            return;
        }
        if (!Objects.equals(slot.getStatus(), CommonStatus.ENABLED)) {
            evictSlotCode(slot.getSlotCode());
            return;
        }

        try {
            stringRedisTemplate.opsForValue().set(
                    DeliveryRedisKeys.slotCodeToId(slot.getSlotCode()),
                    String.valueOf(slot.getId()),
                    properties.getRedisTtl());
        } catch (RuntimeException ex) {
            log.warn("写入广告位缓存失败，slotCode={}，后续请求将回源 MySQL：{}", slot.getSlotCode(), ex.getMessage());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void refreshSlot(SlotEntity slot, String oldSlotCode) {
        if (slot != null && Objects.equals(slot.getStatus(), CommonStatus.ENABLED)) {
            // 布隆过滤器允许假阳性：提交前加入可避免提交后的假阴性误杀。
            bloomFilterService.put(slot.getSlotCode());
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /** 数据库事务成功提交后再刷新缓存，避免脏数据进入 Redis。 */
                @Override
                public void afterCommit() {
                    refreshSlotCache(slot, oldSlotCode);
                }
            });
            return;
        }
        refreshSlotCache(slot, oldSlotCode);
    }

    /** {@inheritDoc} */
    @Override
    public void refreshSlotByCode(String slotCode) {
        if (!StringUtils.hasText(slotCode)) {
            return;
        }
        try (SlotCacheLockManager.LockHandle ignored = lockManager.acquireForWrite(slotCode)) {
            SlotEntity current = selectEnabledSlotByCode(slotCode);
            if (current == null) {
                evictSlotCode(slotCode);
                return;
            }
            writeSlotToRedis(current);
        }
    }

    /**
     * 在数据库事务提交后删除旧编码并写入当前广告位缓存。
     *
     * @param slot 更新后的广告位
     * @param oldSlotCode 更新前的广告位编码
     */
    private void refreshSlotCache(SlotEntity slot, String oldSlotCode) {
        String currentSlotCode = slot == null ? null : slot.getSlotCode();
        try (SlotCacheLockManager.LockHandle ignored =
                     lockManager.acquireForWrite(oldSlotCode, currentSlotCode)) {
            if (StringUtils.hasText(oldSlotCode)
                    && (slot == null || !oldSlotCode.equals(currentSlotCode))) {
                evictSlotCode(oldSlotCode);
            }
            writeSlotToRedis(slot);
        }
    }

    /**
     * 从 MySQL 查询指定编码且状态为启用的广告位。
     *
     * @param slotCode 对外广告位编码
     * @return 启用广告位；不存在时返回 {@code null}
     */
    private SlotEntity selectEnabledSlotByCode(String slotCode) {
        return slotMapper.selectOne(new LambdaQueryWrapper<SlotEntity>()
                .eq(SlotEntity::getSlotCode, slotCode)
                .eq(SlotEntity::getStatus, CommonStatus.ENABLED));
    }

    /**
     * 从 Redis 读取广告位 ID，读取失败或值格式错误时按缓存未命中处理。
     *
     * @param slotCode 对外广告位编码
     * @return Redis 中的广告位 ID；未命中或异常时返回 empty
     */
    private Optional<Long> getSlotIdFromRedis(String slotCode) {
        String value;
        try {
            value = stringRedisTemplate.opsForValue().get(DeliveryRedisKeys.slotCodeToId(slotCode));
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

    /**
     * 删除指定广告位编码的 Redis 映射缓存。
     *
     * @param slotCode 待清理的广告位编码
     */
    private void evictSlotCode(String slotCode) {
        if (!StringUtils.hasText(slotCode)) {
            return;
        }
        try {
            stringRedisTemplate.delete(DeliveryRedisKeys.slotCodeToId(slotCode));
        } catch (RuntimeException ex) {
            log.warn("删除广告位缓存失败，slotCode={}：{}", slotCode, ex.getMessage());
        }
    }
}
