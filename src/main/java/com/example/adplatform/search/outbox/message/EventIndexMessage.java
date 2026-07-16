package com.example.adplatform.search.outbox.message;

/**
 * 事件进入 ES 的轻量通知；完整内容以 MySQL event 表为准。
 *
 * @param eventId 广告事件唯一标识
 */
public record EventIndexMessage(String eventId) {
}
