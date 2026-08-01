package com.example.adplatform.infra.elasticsearch.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 第三阶段搜索链路的集中配置，映射 {@code app.elasticsearch.*}。
 *
 * <p>候选召回和 Outbox 参数分别建模，避免业务代码散落字符串常量。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.elasticsearch")
public class AdElasticsearchProperties {

    /** 总开关；关闭后投放直接使用 MySQL 降级，启动阶段也不会构建候选索引。 */
    private boolean enabled = true;
    private final Candidate candidate = new Candidate();
    private final Outbox outbox = new Outbox();

    /** 候选索引、粗召回和全量重建参数。 */
    @Getter
    @Setter
    public static class Candidate {
        /** 在线查询只使用读别名。 */
        private String readAlias = "ad-candidate-read";
        /** 增量同步只使用写别名。 */
        private String writeAlias = "ad-candidate-write";
        /** 正常单次粗召回数量。 */
        private int recallSize = 200;
        /** 防止误配置导致超大结果集的硬上限。 */
        private int maxRecallSize = 1000;
        /** ES 单次查询超时，超时会触发 MySQL 降级。 */
        private Duration queryTimeout = Duration.ofMillis(100);
        /** 分布式重建锁及 rebuilding 标记的存活时间。 */
        private Duration rebuildLockTtl = Duration.ofMinutes(10);
    }

    /** Outbox 传输方式、轮询回退和历史清理参数。 */
    @Getter
    @Setter
    public static class Outbox {
        /**
         * Outbox 到 Kafka 的传输方式。生产式本地环境默认由 Debezium 读取 MySQL Binlog；
         * polling 仅用于不运行 Kafka Connect 时的应急回退和集成测试。
         */
        private Transport transport = Transport.DEBEZIUM;
        /** 两次发布批次之间的固定延迟。 */
        private long publishDelayMs = 500;
        /** 应用启动后首次发布前的等待时间。 */
        private long publishInitialDelayMs = 3000;
        /** 单个事务最多领取的 Outbox 行数。 */
        private int batchSize = 100;
        /** Outbox 历史记录保留天数，必须大于允许的 Debezium 最大故障恢复窗口。 */
        private int retentionDays = 7;
        /** 历史清理任务，独立于消息发布链路。 */
        private String cleanupCron = "0 20 3 * * *";
    }

    public enum Transport {
        DEBEZIUM,
        POLLING
    }
}
