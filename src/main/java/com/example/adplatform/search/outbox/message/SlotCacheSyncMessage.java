package com.example.adplatform.search.outbox.message;

/**
 * 广告位缓存收敛消息。消费者回查 MySQL 最新状态，旧编码仅用于改名后清理。
 */
public record SlotCacheSyncMessage(
        String eventId,
        String slotPublicId,
        String previousSlotCode) {
}
