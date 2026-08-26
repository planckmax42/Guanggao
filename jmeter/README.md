# 广告平台 JMeter 压测说明

## 运行前准备

1. 启动 MySQL、Redis、Kafka、Elasticsearch 和 Spring Boot 应用。
2. 执行基础 SQL，其中必须包含 `06-elasticsearch-outbox-schema.sql` 和 `07-debezium-cdc.sql`。
3. 确认应用和 ES 可用：

```bash
curl http://127.0.0.1:8080/api/health
curl http://127.0.0.1:9200/_cluster/health
```

## 准备 1k / 5k / 10k 候选数据

`sql/prepare-es-recall-data.sql` 只使用 `900000` 号段，可重复执行，不会清理演示数据。下面的 `@candidate_count` 可换成 `1000`、`5000` 或 `10000`：

```bash
mysql -h127.0.0.1 -P3306 -uroot -p ad_platform \
  -e "SET @candidate_count=5000; SOURCE jmeter/sql/prepare-es-recall-data.sql;"

curl -X POST http://127.0.0.1:8080/api/admin/search/candidates/rebuild
```

脚本直接写 MySQL，不会触发应用内的广告位布隆过滤器增量更新。建议在应用启动前生成数据；如果应用已在运行，生成后重启应用，再执行候选索引重建。

重建响应中的 `indexedCount` 应等于演示可投候选数加本次生成数。ES 通过新建版本索引后原子切换别名，重建期间不影响旧索引读取。

压测完成后可清理专用 ID 段，然后再重建候选索引：

```bash
mysql -h127.0.0.1 -P3306 -uroot -p ad_platform \
  -e "SOURCE jmeter/sql/cleanup-es-recall-data.sql;"
redis-cli DEL slot:code:ES_LOAD_HOME slot:code:ES_LOAD_FEED slot:code:ES_LOAD_SEARCH
curl -X POST http://127.0.0.1:8080/api/admin/search/candidates/rebuild
```

## 线程组

- `02-广告投放高并发`：演示数据上的 5 类业务场景，实际走 ES 粗召回、Redis 批量动态过滤和 Java 精排。
- `03-事件采集高并发写入`：事件 API -> Kafka -> MySQL 事件与计费流水，同时更新 Redis 实时统计。
- `04-重复事件幂等冲突`：验证 `event_id` 唯一索引和消费幂等。
- `05-报表查询混合读`：日报、漏斗和素材排行。
- `06-ES多维粗召回投放链路`：专用于 1k / 5k / 10k 数据，覆盖 3 个压测广告位和多维定向。

`06` 默认关闭，用 GUI 打开时建议同时关闭其他线程组，避免混杂指标。

## 命令行运行

JMX 默认启用基础管理和投放线程组：

```bash
jmeter -n \
  -t jmeter/ad-platform-load-test.jmx \
  -l jmeter/result.jtl \
  -j jmeter/jmeter.log \
  -e -o jmeter/report \
  -Jhost=127.0.0.1 \
  -Jport=8080 \
  -JadminThreads=5 \
  -JdeliveryThreads=80 \
  -JdeliveryRamp=10 \
  -JdeliveryLoops=20
```

各专项线程数参数：

```text
eventThreads / eventRamp / eventLoops
duplicateThreads / duplicateRamp / duplicateLoops
reportThreads / reportRamp / loops
esRecallThreads / esRecallRamp / esRecallLoops
```

## 建议压测方法

1. 单线程、单循环校验返回值，然后关闭 `查看结果树`。
2. 分别在 1k、5k、10k 候选量下运行 `06`，记录吞吐、P95、P99 和错误率。
3. 查看 `/actuator/metrics/ad.candidate.recall.duration`，确认 `source=ELASTICSEARCH`，避免把 MySQL 降级结果误当成 ES 结果。
4. 停止 ES 再运行相同请求，验证接口仍成功，且指标出现 `source=MYSQL_FALLBACK`。恢复 ES 后继续压测，验证熔断器自动恢复。
5. 运行 `03` 时打开 Grafana 的 **Kafka Event Pipeline** Dashboard，观察 HTTP 速率、Topic 写入、
   三个事件 Consumer Group 的消费速率、分阶段处理 P95 与 Lag 是否同步变化，并核对 MySQL
   `event`/`charge_record` 写入和 Redis 实时统计。Debezium 只负责广告配置 Outbox 到候选 ES 的同步。

第一次请求可能需要从 MySQL 回建预算 Redis Key，建议先预热 30 秒再采集稳态数据。
