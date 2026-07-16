/**
 * 广告候选索引领域。
 *
 * <p>负责 MySQL 配置去范式化、版本化索引重建、增量同步和多维粗召回。预算消耗、
 * 用户频控等高频动态状态不写入 ES，仍由 Redis 在投放请求中判断。</p>
 */
package com.example.adplatform.search.candidate;
