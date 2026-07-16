/**
 * MySQL Outbox 可靠消息领域。
 *
 * <p>业务写入和消息记录同事务提交，调度器在提交后将消息至少一次投递到 Kafka；因此
 * 所有下游 ES 消费者都必须以业务主键实现幂等。</p>
 */
package com.example.adplatform.search.outbox;
