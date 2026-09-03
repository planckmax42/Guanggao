package com.example.adplatform.search.outbox.message;

/**
 * 候选配置变更通知。消息刻意不携带配置快照，消费者始终回查 MySQL 最新状态。
 *
 * @param eventId 单次变更标识，便于追踪重复投递
 * @param aggregateType 配置聚合类型
 * @param aggregateId 跨服务使用的不可变公开标识
 */
public record ConfigChangeMessage(
        String eventId,
        ConfigAggregateType aggregateType,
        String aggregateId) {
}
