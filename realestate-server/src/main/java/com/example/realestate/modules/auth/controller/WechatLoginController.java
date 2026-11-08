package com.example.realestate.modules.auth.controller;

import com.example.realestate.common.api.ApiResponse;
import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信 code 登录适配层（预留，W1 不启用）。
 * 正式 AppID/AppSecret 配置后接入 code2session；不信任客户端提交的 openid。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/v1/auth")
public class WechatLoginController {

    @Operation(summary = "微信 code 登录（预留接口，正式 AppID 配置后启用）")
    @PostMapping("/wechat-login")
    public ApiResponse<Void> wechatLogin() {
        // W1 测试号阶段未启用 code2session，统一返回业务异常
        throw new BusinessException(ErrorCode.BAD_REQUEST, "微信登录暂未启用，当前请使用账号密码登录");
    }
}
