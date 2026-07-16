package com.example.adplatform.search.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 第三阶段搜索链路的集中配置，映射 {@code app.elasticsearch.*}。
 *
 * <p>候选召回、事件检索和 Outbox 三组参数分别建模，避免业务代码散落字符串常量。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.elasticsearch")
public class AdElasticsearchProperties {

    /** 总开关；关闭后投放直接使用 MySQL 降级，启动阶段也不会创建索引模板。 */
    private boolean enabled = true;
    private final Candidate candidate = new Candidate();
    private final Event event = new Event();
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

    /** 事件日索引、保留策略和查询范围参数。 */
    @Getter
    @Setter
    public static class Event {
        /** 日索引统一前缀，实际名称还会拼接 yyyyMMdd。 */
        private String indexPrefix = "ad-event-";
        /** composable index template 名称。 */
        private String templateName = "ad-event-template";
        /** 事件历史自动淘汰使用的 ILM policy 名称。 */
        private String lifecyclePolicy = "ad-event-retention-30d";
        /** 日索引保留天数。 */
        private int retentionDays = 30;
        /** 未传时间范围时默认向前查询的时长。 */
        private Duration defaultLookback = Duration.ofHours(24);
        /** 单次接口允许跨越的最大自然日范围。 */
        private int maxQueryDays = 31;
    }

    /** Outbox 发布轮询、批量大小和历史清理参数。 */
    @Getter
    @Setter
    public static class Outbox {
        /** 两次发布批次之间的固定延迟。 */
        private long publishDelayMs = 500;
        /** 应用启动后首次发布前的等待时间。 */
        private long publishInitialDelayMs = 3000;
        /** 单个事务最多领取的 Outbox 行数。 */
        private int batchSize = 100;
        /** SENT 历史记录保留天数。 */
        private int sentRetentionDays = 7;
    }
}
