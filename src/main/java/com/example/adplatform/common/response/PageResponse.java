package com.example.adplatform.common.response;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

/**
 * 统一分页返回结构，只把分页元数据和转换后的 VO 列表返回给前端。
 */
public record PageResponse<T>(long current, long size, long total, long pages, List<T> records) {

    /**
     * 从 MyBatis-Plus 分页对象中提取分页信息，并使用业务层转换后的 records。
     */
    public static <T> PageResponse<T> of(Page<?> page, List<T> records) {
        return new PageResponse<>(page.getCurrent(), page.getSize(), page.getTotal(), page.getPages(), records);
    }
}
