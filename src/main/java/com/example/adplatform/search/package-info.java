/**
 * Elasticsearch 第三阶段读取模型。
 *
 * <p>MySQL 始终是业务真实数据源；本模块通过 Outbox 和 Kafka 异步维护候选 ES 读取
 * 模型，用于在线投放粗召回。</p>
 */
package com.example.adplatform.search;
