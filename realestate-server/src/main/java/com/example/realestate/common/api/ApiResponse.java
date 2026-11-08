package com.example.realestate.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 统一 JSON 响应结构。
 * code 为稳定业务错误码；traceId 同时写入 X-Request-Id 响应头。
 */
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        Map<String, List<String>> errors,
        String traceId,
        String timestamp
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("0", "success", data, null,
                currentTraceId(), Instant.now().toString());
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>("0", "success", null, null,
                currentTraceId(), Instant.now().toString());
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(code, message, null, null,
                currentTraceId(), Instant.now().toString());
    }

    public static ApiResponse<Void> error(String code, String message, Map<String, List<String>> errors) {
        return new ApiResponse<>(code, message, null, errors,
                currentTraceId(), Instant.now().toString());
    }

    private static String currentTraceId() {
        String traceId = org.slf4j.MDC.get("traceId");
        return traceId == null ? "" : traceId;
    }
}
