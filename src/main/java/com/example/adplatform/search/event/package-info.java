/**
 * 广告事件 ES 读取模型。
 *
 * <p>事件在 MySQL 事务提交后异步写入按日切分的索引，使用 ILM 控制保留周期，并通过
 * search_after 提供稳定游标分页。</p>
 */
package com.example.adplatform.search.event;
