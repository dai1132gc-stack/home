package com.example.realestate.modules.auth.wechat.model.dto;

import com.example.realestate.common.enums.ClientType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 微信小程序登录请求：code 来自 wx.login，一次性使用；不接受客户端提交的 openid。
 */
public record WechatLoginRequest(
        @NotBlank(message = "微信登录凭证不能为空")
        @Size(max = 128, message = "微信登录凭证长度不合法")
        String code,

        @NotNull(message = "客户端类型不能为空")
        ClientType clientType
) {
}
