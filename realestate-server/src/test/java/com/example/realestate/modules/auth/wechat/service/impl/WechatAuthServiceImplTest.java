package com.example.realestate.modules.auth.wechat.service.impl;

import com.example.realestate.common.enums.ClientType;
import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.config.AppProperties;
import com.example.realestate.modules.auth.model.vo.LoginResponse;
import com.example.realestate.modules.auth.model.vo.UserVO;
import com.example.realestate.modules.auth.service.AuthService;
import com.example.realestate.modules.auth.wechat.client.WechatMiniClient;
import com.example.realestate.modules.auth.mapper.UserWechatBindingMapper;
import com.example.realestate.modules.auth.wechat.model.dto.WechatLoginRequest;
import com.example.realestate.modules.auth.wechat.model.entity.UserWechatBinding;
import com.example.realestate.modules.auth.wechat.service.WechatUserRegistrar;
import com.example.realestate.modules.user.mapper.SysUserMapper;
import com.example.realestate.modules.user.model.entity.SysUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * W5 微信 code 登录单测：新用户创建、老用户幂等、code 无效、未配置 503、
 * 上游 5xx、openid 唯一键并发冲突、非小程序客户端拒绝。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WechatAuthServiceImplTest {

    private static final String OPENID = "oWxTestOpenid0123456789";

    @Mock
    private WechatMiniClient wechatMiniClient;
    @Mock
    private WechatUserRegistrar registrar;
    @Mock
    private UserWechatBindingMapper bindingMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private AuthService authService;
    @Mock
    private com.example.realestate.modules.auth.service.AuthRateLimiter authRateLimiter;

    private WechatAuthServiceImpl service;
    private SysUser customer;

    @BeforeEach
    void setUp() {
        service = new WechatAuthServiceImpl(
                wechatMiniClient, registrar, bindingMapper, sysUserMapper, authService,
                properties(true), authRateLimiter);

        customer = new SysUser();
        customer.setId(9L);
        customer.setUsername("wx_abc");
        customer.setRole(SysUser.ROLE_CUSTOMER);
        customer.setStatus(SysUser.STATUS_ACTIVE);

        when(wechatMiniClient.code2Session("valid-code"))
                .thenReturn(new WechatMiniClient.WechatSession(OPENID, null));
        when(authService.loginAfterExternalAuth(anyLong(), eq(ClientType.MINI)))
                .thenAnswer(inv -> tokenResponse(inv.getArgument(0)));
    }

    private AppProperties properties(boolean configured) {
        return new AppProperties(
                new AppProperties.Jwt(
                        "unit-test-access-secret-0123456789abcdef0123456789abcdef",
                        "unit-test-refresh-secret-0123456789abcdef0123456789abcdef",
                        15, 7),
                new AppProperties.Cors(List.of("http://localhost:5174")),
                new AppProperties.Bootstrap(null, null),
                new AppProperties.Wechat(
                        configured ? "wx-test-appid" : "",
                        configured ? "test-secret-not-real" : "",
                        4, "https://api.weixin.qq.com"),
                null);
    }

    private LoginResponse tokenResponse(Long userId) {
        UserVO user = new UserVO(userId, "wx_abc", "微信用户A1B2C3",
                SysUser.ROLE_CUSTOMER, SysUser.STATUS_ACTIVE, Instant.now());
        return new LoginResponse("access-token", "refresh-token", "Bearer", 900, user);
    }

    private WechatLoginRequest request() {
        return new WechatLoginRequest("valid-code", ClientType.MINI);
    }

    private UserWechatBinding bindingOf(Long userId) {
        UserWechatBinding binding = new UserWechatBinding();
        binding.setId(1L);
        binding.setUserId(userId);
        binding.setOpenid(OPENID);
        return binding;
    }

    @Test
    @DisplayName("首次登录：创建 CUSTOMER 并签发与账号密码登录一致的双令牌")
    void firstLoginCreatesCustomer() {
        when(bindingMapper.selectOne(any())).thenReturn(null);
        when(registrar.createCustomer(OPENID, null)).thenReturn(customer);

        LoginResponse response = service.login(request());

        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().id()).isEqualTo(9L);
        verify(registrar).createCustomer(OPENID, null);
    }

    @Test
    @DisplayName("老用户重复登录：命中绑定，不再创建新账号（幂等）")
    void existingBindingIsIdempotent() {
        when(bindingMapper.selectOne(any())).thenReturn(bindingOf(9L));
        when(sysUserMapper.selectById(9L)).thenReturn(customer);

        LoginResponse first = service.login(request());
        LoginResponse second = service.login(request());

        assertThat(first.user().id()).isEqualTo(second.user().id()).isEqualTo(9L);
        verify(registrar, never()).createCustomer(any(), any());
    }

    @Test
    @DisplayName("code 无效/过期：401 + AUTH_40105")
    void invalidCodeReturns401() {
        when(wechatMiniClient.code2Session("bad-code"))
                .thenThrow(new BusinessException(ErrorCode.WECHAT_CODE_INVALID));

        assertThatThrownBy(() -> service.login(new WechatLoginRequest("bad-code", ClientType.MINI)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.WECHAT_CODE_INVALID);
        verifyNoInteractions(registrar);
    }

    @Test
    @DisplayName("未配置 AppID/AppSecret：503 + AUTH_50301，且不调用微信接口")
    void notConfiguredReturns503() {
        WechatAuthServiceImpl unconfigured = new WechatAuthServiceImpl(
                wechatMiniClient, registrar, bindingMapper, sysUserMapper, authService,
                properties(false), authRateLimiter);

        assertThatThrownBy(() -> unconfigured.login(request()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.WECHAT_NOT_CONFIGURED);
        verifyNoInteractions(wechatMiniClient);
        // 账号密码登录不依赖微信配置：同一 AuthService 仍可用于密码登录（由 AuthServiceImplTest 覆盖）
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("微信上游 5xx/超时：502 + AUTH_50201")
    void upstreamFailureReturns502() {
        when(wechatMiniClient.code2Session("valid-code"))
                .thenThrow(new BusinessException(ErrorCode.WECHAT_UPSTREAM_ERROR));

        assertThatThrownBy(() -> service.login(request()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.WECHAT_UPSTREAM_ERROR);
    }

    @Test
    @DisplayName("openid 唯一键并发冲突：回滚后重查绑定，复用已有账号")
    void uniqueConflictFallsBackToExistingBinding() {
        when(bindingMapper.selectOne(any())).thenReturn(null, bindingOf(9L));
        when(registrar.createCustomer(OPENID, null))
                .thenThrow(new DuplicateKeyException("uk_wechat_binding_openid"));
        when(sysUserMapper.selectById(9L)).thenReturn(customer);

        LoginResponse response = service.login(request());

        assertThat(response.user().id()).isEqualTo(9L);
        verify(registrar).createCustomer(OPENID, null);
    }

    @Test
    @DisplayName("冲突后仍无绑定：409，不重复建号")
    void unresolvedConflictReturns409() {
        when(bindingMapper.selectOne(any())).thenReturn(null);
        when(registrar.createCustomer(OPENID, null))
                .thenThrow(new DuplicateKeyException("uk_wechat_binding_openid"));

        assertThatThrownBy(() -> service.login(request()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("clientType=WEB：拒绝，微信登录仅限小程序")
    void webClientRejected() {
        assertThatThrownBy(() -> service.login(new WechatLoginRequest("valid-code", ClientType.WEB)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verifyNoInteractions(wechatMiniClient);
    }

    @Test
    @DisplayName("绑定用户已不存在：统一 LOGIN_FAILED，不签发令牌")
    void missingUserRejected() {
        when(bindingMapper.selectOne(any())).thenReturn(bindingOf(9L));
        when(sysUserMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.login(request()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.LOGIN_FAILED);
        verify(authService, never()).loginAfterExternalAuth(anyLong(), any());
    }
}
