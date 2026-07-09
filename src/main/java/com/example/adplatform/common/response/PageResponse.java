package com.example.adplatform.common.response;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

public record PageResponse<T>(long current, long size, long total, long pages, List<T> records) {

    public static <T> PageResponse<T> of(Page<?> page, List<T> records) {
        return new PageResponse<>(page.getCurrent(), page.getSize(), page.getTotal(), page.getPages(), records);
    }
}
