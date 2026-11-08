package com.example.realestate.modules.auth.controller;

import com.example.realestate.common.api.ApiResponse;
import com.example.realestate.modules.auth.model.vo.LoginResponse;
import com.example.realestate.modules.auth.wechat.model.dto.WechatLoginRequest;
import com.example.realestate.modules.auth.wechat.service.WechatAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信小程序 code 登录。
 * Controller 只做参数校验与响应封装，code2session、绑定与令牌签发在 Service。
 * 响应结构与小程序的账号密码登录一致（含 refreshToken，由客户端存 Storage）。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/v1/auth")
public class WechatLoginController {

    private final WechatAuthService wechatAuthService;

    public WechatLoginController(WechatAuthService wechatAuthService) {
        this.wechatAuthService = wechatAuthService;
    }

    @Operation(summary = "微信 code 登录（AppID/AppSecret 未配置时 503，降级账号密码登录）")
    @PostMapping("/wechat-login")
    public ApiResponse<LoginResponse> wechatLogin(@Valid @RequestBody WechatLoginRequest request) {
        return ApiResponse.ok(wechatAuthService.login(request));
    }
}
