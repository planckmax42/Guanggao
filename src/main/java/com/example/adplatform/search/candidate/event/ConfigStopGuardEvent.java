package com.example.adplatform.search.candidate.event;

import com.example.adplatform.search.outbox.message.ConfigAggregateType;

/**
 * 管理配置事务提交后更新 Redis 停投保护的本地事件。
 *
 * @param aggregateType 发生变化的配置类型
 * @param aggregateId 配置主键
 * @param stopped {@code true} 表示立即禁止投放，{@code false} 表示恢复投放
 */
public record ConfigStopGuardEvent(
        ConfigAggregateType aggregateType,
        Long aggregateId,
        boolean stopped) {
}
