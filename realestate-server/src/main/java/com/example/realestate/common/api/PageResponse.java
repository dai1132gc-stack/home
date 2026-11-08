package com.example.realestate.common.api;

import java.util.List;

/**
 * 统一分页响应体。
 * 分页在 SQL 层完成（MyBatis-Plus PaginationInnerInterceptor），不在 Service 内存截取。
 */
public record PageResponse<T>(
        List<T> items,
        long total,
        long page,
        long size
) {

    public static <T> PageResponse<T> of(List<T> items, long total, long page, long size) {
        return new PageResponse<>(items == null ? List.of() : items, total, page, size);
    }
}
