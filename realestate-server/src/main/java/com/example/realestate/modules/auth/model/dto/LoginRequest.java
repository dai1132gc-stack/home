package com.example.realestate.modules.auth.model.dto;

import com.example.realestate.common.enums.ClientType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 登录请求：W1 Web 与小程序共用账号密码登录，以 clientType 区分。
 */
public record LoginRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名长度不能超过64")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 64, message = "密码长度须为6~64")
        String password,

        @NotNull(message = "客户端类型不能为空")
        ClientType clientType
) {
}
