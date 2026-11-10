package com.example.adplatform.admin.response;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

/**
 * 统一分页返回结构，只把分页元数据和转换后的 Response 列表返回给前端。
 */
public record PageResponse<T>(
        long current,
        long size,
        long total,
        long pages,
        List<T> records) {

}
