package com.example.realestate.security.model;

import com.example.realestate.common.enums.ClientType;

/**
 * 认证上下文用户（来自 Access Token claims，不含手机号等敏感信息）。
 */
public record LoginUser(
        Long userId,
        String username,
        String role,
        ClientType client
) {
}
