package com.example.adplatform.tracking.service;

import com.example.adplatform.tracking.message.EventMessage;

/** 将 Kafka 广告事件幂等归档到事件明细表。 */
@FunctionalInterface
public interface EventArchiveProcessor {

    void archive(EventMessage message);
}
