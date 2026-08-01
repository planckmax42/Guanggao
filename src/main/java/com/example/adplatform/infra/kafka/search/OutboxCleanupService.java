package com.example.adplatform.infra.kafka.search;

import com.example.adplatform.infra.elasticsearch.shared.AdElasticsearchProperties;
import com.example.adplatform.search.outbox.mapper.OutboxMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 清理超过保留期的 Outbox 历史记录。
 *
 * <p>Polling 模式只能删除已确认发送的 SENT 行；Debezium 模式由 Kafka Connect offset
 * 记录读取进度，Outbox 表保持 append-only，并按一个足够安全的时间窗口统一清理。</p>
 */
@Service
@RequiredArgsConstructor
public class OutboxCleanupService {

    private final OutboxMessageMapper outboxMessageMapper;
    private final AdElasticsearchProperties properties;

    @Transactional
    public int cleanupExpired() {
        int retentionDays = properties.getOutbox().getRetentionDays();
        if (properties.getOutbox().getTransport() == AdElasticsearchProperties.Transport.POLLING) {
            return outboxMessageMapper.deleteSentBefore(retentionDays);
        }
        return outboxMessageMapper.deleteCreatedBefore(retentionDays);
    }
}
