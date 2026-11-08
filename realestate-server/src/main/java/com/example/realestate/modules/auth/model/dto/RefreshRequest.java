package com.example.realestate.modules.auth.model.dto;

import jakarta.validation.constraints.Size;

/**
 * 刷新请求：小程序通过 body 传递 refreshToken；Web 端使用 HttpOnly Cookie，body 留空。
 */
public record RefreshRequest(
        @Size(max = 256, message = "refreshToken 长度非法")
        String refreshToken
) {
}
