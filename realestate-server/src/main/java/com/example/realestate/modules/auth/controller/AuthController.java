package com.example.realestate.modules.auth.controller;

import com.example.realestate.common.api.ApiResponse;
import com.example.realestate.common.enums.ClientType;
import com.example.realestate.config.AppProperties;
import com.example.realestate.modules.auth.model.dto.LoginRequest;
import com.example.realestate.modules.auth.model.dto.RefreshRequest;
import com.example.realestate.modules.auth.model.vo.LoginResponse;
import com.example.realestate.modules.auth.model.vo.UserVO;
import com.example.realestate.modules.auth.service.AuthService;
import com.example.realestate.security.model.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。Cookie 的读写仅出现在 Controller 层（Service 不依赖 Servlet API）：
 * - WEB：Refresh Token 走 HttpOnly Cookie（Path=/api/v1/auth，SameSite=Lax）
 * - MINI：Refresh Token 在响应体返回，由客户端保存 Storage
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public static final String REFRESH_COOKIE = "rt";

    private final AuthService authService;
    private final AppProperties properties;

    public AuthController(AuthService authService, AppProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @Operation(summary = "账号密码登录（Web/小程序共用）")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                             HttpServletResponse response) {
        LoginResponse result = authService.login(request);
        if (request.clientType() == ClientType.WEB) {
            writeRefreshCookie(response, result.refreshToken());
            return ApiResponse.ok(stripRefreshToken(result));
        }
        return ApiResponse.ok(result);
    }

    @Operation(summary = "刷新令牌（Web 用 Cookie，小程序用 body.refreshToken）")
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody(required = false) RefreshRequest body,
                                              HttpServletRequest request,
                                              HttpServletResponse response) {
        String cookieToken = readRefreshCookie(request);
        String bodyToken = body == null ? null : body.refreshToken();
        LoginResponse result = authService.refresh(cookieToken, bodyToken);
        if (cookieToken != null) {
            // Web 端：新 Refresh Token 重新写入 HttpOnly Cookie，不进响应体
            writeRefreshCookie(response, result.refreshToken());
            return ApiResponse.ok(stripRefreshToken(result));
        }
        return ApiResponse.ok(result);
    }

    @Operation(summary = "退出登录（撤销当前会话）")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) RefreshRequest body,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {
        String cookieToken = readRefreshCookie(request);
        String bodyToken = body == null ? null : body.refreshToken();
        authService.logout(cookieToken, bodyToken);
        clearRefreshCookie(response);
        return ApiResponse.ok();
    }

    @Operation(summary = "当前登录用户信息")
    @GetMapping("/me")
    public ApiResponse<UserVO> me(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(authService.me(loginUser));
    }

    // ---------- Cookie 工具（仅 Controller 层） ----------

    /** WEB 端响应体中剔除 refreshToken（仅保留在 HttpOnly Cookie） */
    private LoginResponse stripRefreshToken(LoginResponse result) {
        return new LoginResponse(result.accessToken(), null,
                result.tokenType(), result.expiresIn(), result.user());
    }

    private String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (REFRESH_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void writeRefreshCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge((int) (properties.jwt().refreshTtlDays() * 24L * 3600L));
        response.addCookie(cookie);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
