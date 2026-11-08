package com.example.realestate.modules.auth.wechat.service;

import com.example.realestate.modules.auth.model.vo.LoginResponse;
import com.example.realestate.modules.auth.wechat.model.dto.WechatLoginRequest;

/**
 * 微信小程序 code 登录：服务端换取 openid → 幂等查找/创建绑定 → 签发与账号密码登录一致的双令牌。
 */
public interface WechatAuthService {

    /** 无客户端 IP 时的兼容入口（限流按 "unknown" IP 维度） */
    default LoginResponse login(WechatLoginRequest request) {
        return login(request, "unknown");
    }

    /**
     * 微信 code 登录。clientIp 用于限流（V6.0.1 F4）：按 IP 计数，防 jscode2session 放大。
     */
    LoginResponse login(WechatLoginRequest request, String clientIp);
}
