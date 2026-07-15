package com.example.adplatform.tracking.port;

import com.example.adplatform.tracking.message.EventMessage;

/**
 * 广告事件发布端口。业务层只依赖该接口，不感知底层使用 Kafka 或其他消息中间件。
 */
public interface EventPublisher {

    /**
     * 发布一条待异步处理的广告事件。
     */
    void publish(EventMessage message);
}
