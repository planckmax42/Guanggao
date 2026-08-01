package com.example.adplatform.delivery.port;

import java.util.Optional;

/**
 * 广告位编码缓存门面，协调布隆过滤器、Redis 缓存和 MySQL 权威数据。
 */
public interface SlotLookupPort {

    /**
     * 根据广告位编码获取启用广告位 ID。先用布隆过滤器拦截明显不存在的编码，再读 Redis，未命中或 Redis 异常时回源 MySQL。
     *
     * @param slotCode 对外广告位编码
     * @return 启用广告位 ID；编码无效或不存在时返回 empty
     */
    Optional<Long> getEnabledSlotIdByCode(String slotCode);
}
