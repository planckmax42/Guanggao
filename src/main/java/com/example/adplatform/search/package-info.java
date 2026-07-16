/**
 * Elasticsearch 第三阶段读取模型。
 *
 * <p>MySQL 始终是业务真实数据源；本模块通过 Outbox 和 Kafka 异步维护候选、事件两类
 * ES 读取模型。候选模型服务在线投放粗召回，事件模型服务运营检索。</p>
 */
package com.example.adplatform.search;
