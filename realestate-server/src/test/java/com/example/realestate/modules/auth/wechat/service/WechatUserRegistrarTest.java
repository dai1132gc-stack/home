package com.example.realestate.modules.auth.wechat.service;

import com.example.realestate.modules.auth.mapper.UserWechatBindingMapper;
import com.example.realestate.modules.auth.wechat.model.entity.UserWechatBinding;
import com.example.realestate.modules.user.mapper.SysUserMapper;
import com.example.realestate.modules.user.model.entity.SysUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 微信首次登录建号细节：用户名不暴露 openid、昵称带随机后缀、密码为不可用的随机哈希。
 */
@ExtendWith(MockitoExtension.class)
class WechatUserRegistrarTest {

    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private UserWechatBindingMapper bindingMapper;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private WechatUserRegistrar registrar;

    @BeforeEach
    void setUp() {
        registrar = new WechatUserRegistrar(sysUserMapper, bindingMapper, passwordEncoder);
        when(sysUserMapper.insert(any(SysUser.class))).thenAnswer(inv -> {
            SysUser user = inv.getArgument(0);
            user.setId(100L);
            return 1;
        });
    }

    @Test
    @DisplayName("首次登录建 CUSTOMER：用户名脱敏派生、昵称随机后缀、随机密码哈希")
    void createsCustomerWithMaskedIdentity() {
        SysUser user = registrar.createCustomer("oWxOpenidForUnitTest123456", "union-1");

        assertThat(user.getId()).isEqualTo(100L);
        assertThat(user.getUsername()).matches("wx_[0-9a-f]{16}");
        assertThat(user.getUsername()).doesNotContain("oWxOpenid");
        assertThat(user.getNickname()).startsWith("微信用户");
        assertThat(user.getNickname()).hasSize(10);
        assertThat(user.getRole()).isEqualTo(SysUser.ROLE_CUSTOMER);
        assertThat(user.getStatus()).isEqualTo(SysUser.STATUS_ACTIVE);
        assertThat(user.getPasswordHash()).startsWith("$2");
        assertThat(user.getPasswordHash()).isNotEqualTo(user.getUsername());
        assertThat(passwordEncoder.matches("", user.getPasswordHash())).isFalse();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();

        ArgumentCaptor<UserWechatBinding> captor = ArgumentCaptor.forClass(UserWechatBinding.class);
        verify(bindingMapper).insert(captor.capture());
        UserWechatBinding binding = captor.getValue();
        assertThat(binding.getUserId()).isEqualTo(100L);
        assertThat(binding.getOpenid()).isEqualTo("oWxOpenidForUnitTest123456");
        assertThat(binding.getUnionid()).isEqualTo("union-1");
        assertThat(binding.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("不同 openid 生成不同用户名，同一 openid 稳定派生（便于唯一键兜底）")
    void usernameIsDeterministicPerOpenid() {
        SysUser first = registrar.createCustomer("openid-a", null);
        SysUser second = registrar.createCustomer("openid-a", null);
        SysUser other = registrar.createCustomer("openid-b", null);

        assertThat(first.getUsername()).isEqualTo(second.getUsername());
        assertThat(other.getUsername()).isNotEqualTo(first.getUsername());
        assertThat(other.getUsername()).matches("wx_[0-9a-f]{16}");
    }
}
