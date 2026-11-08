package com.example.realestate.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 客户端 IP 解析（Controller 层使用，Service 不依赖 Servlet API）。
 * 信任边界：X-Forwarded-For / X-Real-IP 仅应在可信反向代理（如 Nginx）覆盖该头之后使用；
 * 直连或不可信代理场景下客户端可伪造 XFF 绕过基于 IP 的限流（见技术方案 24.9/25.8）。
 */
@Component
public class ClientIpResolver {

    public String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
