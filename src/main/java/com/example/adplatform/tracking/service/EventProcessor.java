package com.example.adplatform.tracking.service;

import com.example.adplatform.tracking.message.EventMessage;

public interface EventProcessor {

    /**
     * 处理异步广告事件，完成明细写入、计费判断和实时统计。
     */
    void process(EventMessage message);
}
