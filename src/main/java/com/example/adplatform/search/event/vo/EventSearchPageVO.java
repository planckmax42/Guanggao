package com.example.adplatform.search.event.vo;

import java.util.List;

public record EventSearchPageVO(
        List<EventSearchItemVO> records,
        String nextCursor,
        Boolean hasMore) {
}
