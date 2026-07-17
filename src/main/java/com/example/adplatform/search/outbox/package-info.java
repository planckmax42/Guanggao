/**
 * MySQL Outbox 可靠消息领域。
 *
 * <p>业务写入和消息记录同事务提交；默认由 Debezium 读取提交后的 Binlog 并至少一次
 * 投递到 Kafka，应用轮询 Relay 只作为显式回退。因此候选 ES 消费者必须以业务主键
 * 实现幂等。</p>
 */
package com.example.adplatform.search.outbox;
