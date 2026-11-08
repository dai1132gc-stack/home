package com.example.realestate.modules.auth.wechat.service;

import com.example.realestate.modules.auth.model.vo.LoginResponse;
import com.example.realestate.modules.auth.wechat.model.dto.WechatLoginRequest;

/**
 * 微信小程序 code 登录：服务端换取 openid → 幂等查找/创建绑定 → 签发与账号密码登录一致的双令牌。
 */
public interface WechatAuthService {

    LoginResponse login(WechatLoginRequest request);
}
