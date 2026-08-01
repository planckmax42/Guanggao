package com.example.adplatform.search.port;

import com.example.adplatform.search.outbox.message.ConfigAggregateType;

/** 更新配置最终一致窗口内紧急停投标记的端口。 */
public interface DeliveryStopGuardWritePort {

    void mark(ConfigAggregateType type, Long id, boolean stopped);
}
