# Debezium Outbox CDC

本目录保存运行在 Docker 中的 Kafka Connect Connector 配置；Kafka Broker 仍是仓库内的宿主机
本地进程。应用只在 MySQL 业务事务中追加
`outbox_message`；Debezium 读取提交后的 Binlog INSERT，并使用 Outbox Event Router 输出：

```text
Kafka topic = outbox_message.topic
Kafka key   = outbox_message.message_key
Kafka value = outbox_message.payload
Kafka header type = outbox_message.message_type
```

因此现有 Spring Kafka 消费者不依赖 Debezium 的 CDC Envelope，也不需要修改消息 DTO。

Connector 没有把 `created_at` 映射为 Kafka Record Timestamp。该列是无时区的 MySQL `DATETIME`，
Kafka Connect 与 Broker 分处不同时区时可能被解释成未来时间并遭 Broker 拒绝；这里使用 Debezium 的
Binlog 事件时间，业务时间仍由 payload 和业务表保存。

## 本地前置条件

1. 执行 `src/main/resources/sql/07-debezium-cdc.sql`。
2. 确认 MySQL 的 `log_bin=ON`、`binlog_format=ROW`、`binlog_row_image=FULL`。
3. 启动本地 Kafka：

   ```bash
   ./kafka_2.13-4.3.1/bin/kafka-server-start.sh -daemon \
     ./kafka_2.13-4.3.1/config/server.properties
   ```

4. 执行 `docker compose -f docker-compose.elasticsearch.yml up -d`。该编排不会创建 Kafka 容器，
   Debezium Connect 通过 host 网络连接 `127.0.0.1:9092`。
5. 查询 `http://127.0.0.1:8083/connectors/ad-platform-outbox/bloomSnapshot`。

Connector 使用 `snapshot.mode=no_data`，首次注册不会重放表中已有的历史 Outbox，只从注册时的
Binlog 位置继续读取。Kafka Connect offset 和 schema history 保存在 Kafka 内部 Topic 中。

配置文件中的 `debezium/DbzLocal_2026!` 账号只用于本机开发。生产部署应通过 Secret 或 Kafka Connect
ConfigProvider 注入凭据，并监控 Connector 状态、Binlog 保留时间和 CDC 延迟。
