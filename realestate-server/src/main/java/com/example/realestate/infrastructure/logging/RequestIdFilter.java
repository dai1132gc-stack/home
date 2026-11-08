package com.example.realestate.infrastructure.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求链路第一步：接收或生成 X-Request-Id（清理非法字符），写入 MDC 与响应头。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    private static final String SAFE_PATTERN = "[^a-zA-Z0-9\\-_]";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String requestId = sanitize(request.getHeader(HEADER));
            if (requestId == null || requestId.isBlank()) {
                requestId = UUID.randomUUID().toString().replace("-", "");
            }
            MDC.put("traceId", requestId);
            MDC.put("method", request.getMethod());
            MDC.put("path", request.getRequestURI());
            response.setHeader(HEADER, requestId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replaceAll(SAFE_PATTERN, "");
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }
}
