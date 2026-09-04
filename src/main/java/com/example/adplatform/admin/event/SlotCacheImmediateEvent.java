package com.example.adplatform.admin.event;

/** 事务提交后仅执行一次的广告位 Redis 快速同步事件。 */
public record SlotCacheImmediateEvent(Action action, String slotCode, Long slotId) {

    public enum Action {
        WRITE,
        EVICT
    }
}
