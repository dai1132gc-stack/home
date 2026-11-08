package com.example.realestate.modules.auth.service;

import com.example.realestate.modules.auth.model.dto.LoginRequest;
import com.example.realestate.modules.auth.model.vo.LoginResponse;
import com.example.realestate.modules.auth.model.vo.UserVO;
import com.example.realestate.common.enums.ClientType;
import com.example.realestate.security.model.LoginUser;

/**
 * 认证服务：不依赖 HttpServletRequest/Response（Cookie 读写留在 Controller 层）。
 */
public interface AuthService {

    /** 账号密码登录（无客户端 IP 时使用，限流仅按用户名维度退化为 "unknown" IP 维度） */
    default LoginResponse login(LoginRequest request) {
        return login(request, "unknown");
    }

    /**
     * 账号密码登录：校验 BCrypt、账号状态、角色与客户端匹配；签发双令牌并创建刷新会话。
     * clientIp 用于登录限流（V6.0.1 F4）：按 IP + 用户名哈希双维度计数。
     */
    LoginResponse login(LoginRequest request, String clientIp);

    /**
     * 外部身份（如微信 code）已在 Service 内校验完成后，按 userId 签发与账号密码登录一致的
     * accessToken + refreshToken + user。调用方不得直接把客户端提交的身份标识透传到此处。
     */
    LoginResponse loginAfterExternalAuth(Long userId, ClientType client);

    /** 刷新：校验会话并轮换 Refresh Token；重放则撤销整个会话族 */
    LoginResponse refresh(String cookieToken, String bodyToken);

    /** 退出：撤销当前刷新会话 */
    void logout(String cookieToken, String bodyToken);

    /** 当前登录用户信息 */
    UserVO me(LoginUser loginUser);
}
