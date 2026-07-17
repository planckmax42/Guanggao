package com.example.adplatform.tracking.service;

import com.example.adplatform.tracking.message.EventMessage;

/** 独立累加广告事件的频控与实时统计。 */
@FunctionalInterface
public interface EventStatisticsProcessor {

    void record(EventMessage message);
}
