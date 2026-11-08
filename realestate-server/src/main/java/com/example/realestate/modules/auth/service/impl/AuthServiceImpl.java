package com.example.realestate.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.realestate.common.enums.ClientType;
import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.config.AppProperties;
import com.example.realestate.modules.auth.convert.UserConvert;
import com.example.realestate.modules.auth.model.dto.LoginRequest;
import com.example.realestate.modules.auth.model.vo.LoginResponse;
import com.example.realestate.modules.auth.model.vo.UserVO;
import com.example.realestate.modules.auth.service.AuthService;
import com.example.realestate.modules.user.mapper.AuthLoginLogMapper;
import com.example.realestate.modules.user.mapper.SysUserMapper;
import com.example.realestate.modules.user.model.entity.AuthLoginLog;
import com.example.realestate.modules.user.model.entity.SysUser;
import com.example.realestate.security.jwt.JwtTokenProvider;
import com.example.realestate.security.model.LoginUser;
import com.example.realestate.security.service.RefreshTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * 认证服务实现。
 * 登录失败统一返回 LOGIN_FAILED，不暴露账号是否存在；
 * 角色与客户端绑定：WEB↔ADMIN、MINI↔CUSTOMER。
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final String EVENT_LOGIN = "LOGIN";
    private static final String EVENT_REFRESH = "REFRESH";
    private static final String EVENT_LOGOUT = "LOGOUT";

    private final SysUserMapper sysUserMapper;
    private final AuthLoginLogMapper loginLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserConvert userConvert;
    private final AppProperties properties;

    public AuthServiceImpl(SysUserMapper sysUserMapper,
                           AuthLoginLogMapper loginLogMapper,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider jwtTokenProvider,
                           RefreshTokenService refreshTokenService,
                           UserConvert userConvert,
                           AppProperties properties) {
        this.sysUserMapper = sysUserMapper;
        this.loginLogMapper = loginLogMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.userConvert = userConvert;
        this.properties = properties;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, request.username()));

        // 统一失败路径：无论账号不存在、密码错误、禁用还是角色不匹配，均返回同一提示
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            audit(user == null ? null : user.getId(), request.username(), request.clientType(),
                    EVENT_LOGIN, false, "LOGIN_FAILED");
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        if (!SysUser.STATUS_ACTIVE.equals(user.getStatus())) {
            audit(user.getId(), request.username(), request.clientType(), EVENT_LOGIN, false, "DISABLED");
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        if (!roleMatchesClient(user.getRole(), request.clientType())) {
            audit(user.getId(), request.username(), request.clientType(), EVENT_LOGIN, false, "ROLE_MISMATCH");
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        LoginUser loginUser = toLoginUser(user, request.clientType());
        LoginResponse response = issueTokens(user, request.clientType(), loginUser);
        audit(user.getId(), request.username(), request.clientType(), EVENT_LOGIN, true, null);
        log.info("登录成功 userId={} role={} client={}", user.getId(), user.getRole(), request.clientType());
        return response;
    }    @Override
    public LoginResponse refresh(String cookieToken, String bodyToken) {
        String presented = firstNonBlank(cookieToken, bodyToken);
        if (presented == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        Duration ttl = refreshTtl();
        RefreshTokenService.RotatedToken rotated = refreshTokenService.refresh(presented, ttl);

        // 重新读取用户，校验账号当前状态（禁用账号不可刷新）
        SysUser user = sysUserMapper.selectById(rotated.userId());
        if (user == null || !SysUser.STATUS_ACTIVE.equals(user.getStatus())) {
            refreshTokenService.revoke(rotated.refreshToken());
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        ClientType client = rotated.client();
        LoginUser loginUser = toLoginUser(user, client);
        String accessToken = jwtTokenProvider.generateAccessToken(loginUser);

        audit(user.getId(), user.getUsername(), client, EVENT_REFRESH, true, null);
        return new LoginResponse(
                accessToken,
                rotated.refreshToken(),
                "Bearer",
                jwtTokenProvider.accessTtlSeconds(),
                userConvert.toUserVO(user));
    }

    @Override
    public void logout(String cookieToken, String bodyToken) {
        String presented = firstNonBlank(cookieToken, bodyToken);
        if (presented != null) {
            refreshTokenService.revoke(presented);
        }
    }

    @Override
    public UserVO me(LoginUser loginUser) {
        SysUser user = sysUserMapper.selectById(loginUser.userId());
        if (user == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID);
        }
        return userConvert.toUserVO(user);
    }

    // ---------- 内部方法 ----------

    private LoginResponse issueTokens(SysUser user, ClientType client, LoginUser loginUser) {
        String accessToken = jwtTokenProvider.generateAccessToken(loginUser);
        RefreshTokenService.RotatedToken rotated =
                refreshTokenService.createSession(user.getId(), user.getRole(), client, refreshTtl());
        return new LoginResponse(
                accessToken,
                rotated.refreshToken(),
                "Bearer",
                jwtTokenProvider.accessTtlSeconds(),
                userConvert.toUserVO(user));
    }

    private boolean roleMatchesClient(String role, ClientType client) {
        return switch (client) {
            case WEB -> SysUser.ROLE_ADMIN.equals(role);
            case MINI -> SysUser.ROLE_CUSTOMER.equals(role);
        };
    }

    private LoginUser toLoginUser(SysUser user, ClientType client) {
        return new LoginUser(user.getId(), user.getUsername(), user.getRole(), client);
    }

    private Duration refreshTtl() {
        return Duration.ofDays(properties.jwt().refreshTtlDays());
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }

    private void audit(Long userId, String username, ClientType client,
                       String eventType, boolean success, String failReason) {
        try {
            AuthLoginLog entry = new AuthLoginLog();
            entry.setUserId(userId);
            entry.setUsername(username);
            entry.setClientType(client.name());
            entry.setEventType(eventType);
            entry.setSuccess(success);
            entry.setFailReason(failReason);
            entry.setTraceId(MDC.get("traceId"));
            entry.setCreatedAt(Instant.now());
            loginLogMapper.insert(entry);
        } catch (Exception e) {
            log.warn("登录审计写入失败（不影响主流程）: {}", e.getMessage());
        }
    }
}
