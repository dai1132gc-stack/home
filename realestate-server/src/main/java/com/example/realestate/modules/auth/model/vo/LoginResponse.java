package com.example.realestate.modules.auth.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 登录/刷新响应。
 * WEB：refreshToken 为空（HttpOnly Cookie 下发）；MINI：refreshToken 在 body 返回，由客户端存 Storage。
 */
public record LoginResponse(
        String accessToken,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserVO user
) {
}
