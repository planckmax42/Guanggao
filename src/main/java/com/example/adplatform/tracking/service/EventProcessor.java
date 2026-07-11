package com.example.adplatform.tracking.service;

import com.example.adplatform.tracking.message.EventMessage;

public interface EventProcessor {

    /**
     * 消费 Kafka 事件消息，完成明细写入、计费判断和 Redis 实时统计。
     */
    void process(EventMessage message);
}
