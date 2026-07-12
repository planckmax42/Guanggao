# 广告平台 JMeter 压测说明

## 运行前准备

1. 先启动本地 MySQL、Redis、Kafka 和 Spring Boot 服务。当前事件采集接口会先写 Kafka，再由消费者异步写入明细和实时统计。
2. 表结构已统一为新命名，建议先重建本地库，再按顺序执行 `00-create-database.sql`、`01-admin-schema.sql`、`02-delivery-schema.sql`、`04-tracking-report-schema.sql`、`99-seed-demo-data.sql`，保证存在固定测试数据：
   - 广告位：`HOME_BANNER`、`FEED_CARD`、`SEARCH_TEXT`
   - 在线计划：`planId=1`、`planId=2`、`planId=6`、`planId=7`
   - 已审核且可投放素材：`materialId=1`、`2`、`3`、`4`、`9`、`10`、`11`、`12`、`13`
3. 确认接口可访问：

```bash
curl http://127.0.0.1:8080/actuator/health
```

## 命令行运行

```bash
jmeter -n \
  -t scripts/jmeter/ad-platform-load-test.jmx \
  -l scripts/jmeter/result.jtl \
  -e -o scripts/jmeter/report \
  -Jhost=127.0.0.1 \
  -Jport=8080 \
  -JadminThreads=5 \
  -JdeliveryThreads=80 \
  -JdeliveryLoops=1 \
  -JeventThreads=100 \
  -JduplicateThreads=30 \
  -JreportThreads=20 \
  -Jloops=100 \
  -JeventLoops=10
```

也可以用 GUI 打开 `ad-platform-load-test.jmx`，逐步调小线程数观察接口表现。

## 覆盖的业务

- 后台管理完整链路：广告主、广告位、广告计划、素材、定向规则、分页查询、计划状态流转。
- 广告投放链路：`POST /api/delivery/ads`，拆成首页生鲜、首页课程、信息流生鲜、信息流游戏、搜索混合 5 类请求，覆盖不同广告位、地区、设备、人群标签和素材集合。
- 事件采集链路：`POST /api/tracking/events`，覆盖曝光、点击、转化事件，并在 9 个可投放素材之间随机上报。
- 重复事件链路：大量线程使用同一个 `eventId`，压测数据库唯一索引和幂等处理。
- 报表链路：日统计、漏斗、素材排行。

## 重点观察的问题

- 投放接口是否因为循环查询计划、定向、预算、频控出现 RT 升高，即 N+1 查询问题。
- 事件采集接口写 Kafka 是否稳定，消费者是否能持续处理消息。
- Redis 实时统计刷入 `daily_report` 时，热点计划/素材是否出现锁竞争。
- 重复事件压测下，`event_id` 唯一索引是否能正确兜底，接口是否返回成功但 `duplicate=true`。
- Hikari 连接池默认 `maximum-pool-size=10`，高并发时是否出现等待连接导致响应时间上升。
- 报表接口在事件写入同时查询时，是否出现响应时间抖动。

## 建议压测步骤

1. 先用默认线程数跑 1 分钟，确认脚本可用。
2. 单独启用 `02-广告投放高并发` 时，实际请求数 = `deliveryThreads * deliveryLoops * 5`，因为每个线程会依次请求 5 个投放场景。
3. 将 `deliveryThreads` 提高到 100 以上，观察投放接口 P95/P99。
4. 将 `eventThreads` 提高到 100 以上，观察事件接口、Kafka 堆积、消费者处理速度和 MySQL 连接数。
5. 单独启用重复事件线程组，观察幂等处理是否稳定。
6. 后续引入 Elasticsearch/ClickHouse 后，再用同一套压测维度对比分析型存储改造前后的吞吐量和 P95/P99。

`查看结果树` 默认关闭。只在单线程调试返回值时打开，高并发压测时不要打开，否则 GUI 会保存大量响应内容，影响压测结果。
