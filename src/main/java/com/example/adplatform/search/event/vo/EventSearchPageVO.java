package com.example.adplatform.search.event.vo;

import java.util.List;

/**
 * 基于 search_after 的事件分页结果。
 *
 * @param records 当前页记录
 * @param nextCursor 下一页不透明游标；无下一页时为 null
 * @param hasMore 是否仍有下一页
 */
public record EventSearchPageVO(
        List<EventSearchItemVO> records,
        String nextCursor,
        Boolean hasMore) {
}
