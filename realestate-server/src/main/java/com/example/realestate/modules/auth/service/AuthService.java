package com.example.realestate.modules.auth.service;

import com.example.realestate.modules.auth.model.dto.LoginRequest;
import com.example.realestate.modules.auth.model.vo.LoginResponse;
import com.example.realestate.modules.auth.model.vo.UserVO;
import com.example.realestate.security.model.LoginUser;

/**
 * 认证服务：不依赖 HttpServletRequest/Response（Cookie 读写留在 Controller 层）。
 */
public interface AuthService {

    /** 账号密码登录：校验 BCrypt、账号状态、角色与客户端匹配；签发双令牌并创建刷新会话 */
    LoginResponse login(LoginRequest request);

    /** 刷新：校验会话并轮换 Refresh Token；重放则撤销整个会话族 */
    LoginResponse refresh(String cookieToken, String bodyToken);

    /** 退出：撤销当前刷新会话 */
    void logout(String cookieToken, String bodyToken);

    /** 当前登录用户信息 */
    UserVO me(LoginUser loginUser);
}
