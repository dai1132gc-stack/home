package com.example.realestate.modules.auth.model.vo;

import java.time.Instant;

/**
 * 当前用户信息（不含手机号等敏感信息）。
 */
public record UserVO(
        Long id,
        String username,
        String nickname,
        String role,
        String status,
        Instant createdAt
) {
}
