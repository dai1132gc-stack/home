package com.example.realestate.modules.auth.service.impl;

import com.example.realestate.common.enums.ClientType;
import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.config.AppProperties;
import com.example.realestate.modules.auth.convert.UserConvertImpl;
import com.example.realestate.modules.auth.model.dto.LoginRequest;
import com.example.realestate.modules.auth.model.vo.LoginResponse;
import com.example.realestate.modules.user.mapper.AuthLoginLogMapper;
import com.example.realestate.modules.user.mapper.SysUserMapper;
import com.example.realestate.modules.user.model.entity.SysUser;
import com.example.realestate.security.jwt.JwtTokenProvider;
import com.example.realestate.security.model.LoginUser;
import com.example.realestate.security.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

    private static final String RAW_PASSWORD = "Admin@123456";

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private AuthLoginLogMapper loginLogMapper;
    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthServiceImpl authService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private SysUser admin;
    private SysUser customer;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt(
                        "unit-test-access-secret-0123456789abcdef0123456789abcdef",
                        "unit-test-refresh-secret-0123456789abcdef0123456789abcdef",
                        15, 7),
                new AppProperties.Cors(List.of("http://localhost:5174")),
                new AppProperties.Bootstrap(null, null));

        authService = new AuthServiceImpl(
                sysUserMapper,
                loginLogMapper,
                passwordEncoder,
                new JwtTokenProvider(properties),
                refreshTokenService,
                new UserConvertImpl(),
                properties);

        admin = new SysUser();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode(RAW_PASSWORD));
        admin.setRole(SysUser.ROLE_ADMIN);
        admin.setStatus(SysUser.STATUS_ACTIVE);

        customer = new SysUser();
        customer.setId(2L);
        customer.setUsername("customer");
        customer.setPasswordHash(passwordEncoder.encode("Customer@123456"));
        customer.setRole(SysUser.ROLE_CUSTOMER);
        customer.setStatus(SysUser.STATUS_ACTIVE);

        when(sysUserMapper.selectOne(any())).thenReturn(null);
        when(loginLogMapper.insert(any(com.example.realestate.modules.user.model.entity.AuthLoginLog.class))).thenReturn(1);
        when(refreshTokenService.createSession(anyLong(), anyString(), any(), any(Duration.class)))
                .thenAnswer(inv -> new RefreshTokenService.RotatedToken(
                        "new-refresh-token", "family-1",
                        inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
        when(refreshTokenService.refresh(anyString(), any(Duration.class)))
                .thenAnswer(inv -> new RefreshTokenService.RotatedToken(
                        "rotated-refresh-token", "family-1", 1L, "ADMIN", ClientType.WEB));
    }

    private LoginRequest login(String username, String password, ClientType client) {
        return new LoginRequest(username, password, client);
    }

    @Test
    @DisplayName("ADMIN 通过 WEB 登录成功：返回 accessToken 与 refreshToken")
    void adminWebLoginSuccess() {
        when(sysUserMapper.selectOne(any())).thenReturn(admin);
        LoginResponse response = authService.login(login("admin", RAW_PASSWORD, ClientType.WEB));

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().role()).isEqualTo("ADMIN");
        assertThat(response.expiresIn()).isEqualTo(15 * 60L);
    }

    @Test
    @DisplayName("密码错误：统一 LOGIN_FAILED，不暴露账号是否存在")
    void wrongPasswordUnifiedFailure() {
        when(sysUserMapper.selectOne(any())).thenReturn(admin);
        assertThatThrownBy(() -> authService.login(login("admin", "wrong-password", ClientType.WEB)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("账号不存在：与密码错误返回相同提示")
    void unknownUserSameFailure() {
        when(sysUserMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> authService.login(login("nobody", RAW_PASSWORD, ClientType.WEB)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("角色与客户端不匹配（CUSTOMER 走 WEB）→ 拒绝")
    void roleClientMismatchRejected() {
        when(sysUserMapper.selectOne(any())).thenReturn(customer);
        assertThatThrownBy(() -> authService.login(login("customer", "Customer@123456", ClientType.WEB)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("CUSTOMER 通过 MINI 登录成功")
    void customerMiniLoginSuccess() {
        when(sysUserMapper.selectOne(any())).thenReturn(customer);
        LoginResponse response = authService.login(login("customer", "Customer@123456", ClientType.MINI));
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.user().role()).isEqualTo("CUSTOMER");
    }

    @Test
    @DisplayName("禁用账号 → 统一 LOGIN_FAILED")
    void disabledUserRejected() {
        admin.setStatus("DISABLED");
        when(sysUserMapper.selectOne(any())).thenReturn(admin);
        assertThatThrownBy(() -> authService.login(login("admin", RAW_PASSWORD, ClientType.WEB)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.LOGIN_FAILED);
    }

    @Test
    @DisplayName("刷新：轮换后返回新 accessToken 与新 refreshToken")
    void refreshIssuesNewTokens() {
        when(sysUserMapper.selectById(eq(1L))).thenReturn(admin);
        LoginResponse response = authService.refresh(null, "old-refresh-token");

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isEqualTo("rotated-refresh-token");
        assertThat(response.user().username()).isEqualTo("admin");
    }

    @Test
    @DisplayName("刷新：账号禁用 → TOKEN_INVALID 并撤销新令牌")
    void refreshRejectedWhenUserDisabled() {
        admin.setStatus("DISABLED");
        when(sysUserMapper.selectById(eq(1L))).thenReturn(admin);
        assertThatThrownBy(() -> authService.refresh(null, "old-refresh-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.TOKEN_INVALID);
    }

    @Test
    @DisplayName("me：返回当前用户信息")
    void meReturnsCurrentUser() {
        when(sysUserMapper.selectById(eq(1L))).thenReturn(admin);
        var vo = authService.me(new LoginUser(1L, "admin", "ADMIN", ClientType.WEB));
        assertThat(vo.username()).isEqualTo("admin");
        assertThat(vo.role()).isEqualTo("ADMIN");
    }
}
