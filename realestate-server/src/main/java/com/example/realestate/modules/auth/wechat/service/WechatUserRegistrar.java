package com.example.realestate.modules.auth.wechat.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.realestate.modules.auth.mapper.UserWechatBindingMapper;
import com.example.realestate.modules.auth.wechat.model.entity.UserWechatBinding;
import com.example.realestate.modules.user.mapper.SysUserMapper;
import com.example.realestate.modules.user.model.entity.SysUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 微信首次登录的账号创建：sys_user（CUSTOMER，随机密码）+ user_wechat_binding。
 * 独立 Bean + 事务，便于调用方在唯一键冲突（并发首次登录）时回滚后重查绑定。
 */
@Component
public class WechatUserRegistrar {

    private static final String NICKNAME_PREFIX = "微信用户";
    private static final String USERNAME_PREFIX = "wx_";

    private final SysUserMapper sysUserMapper;
    private final UserWechatBindingMapper bindingMapper;
    private final PasswordEncoder passwordEncoder;

    public WechatUserRegistrar(SysUserMapper sysUserMapper,
                               UserWechatBindingMapper bindingMapper,
                               PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.bindingMapper = bindingMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 首次微信登录创建 CUSTOMER：
     * - 用户名由 openid 摘要派生，不暴露 openid，也不会与人工账号冲突；
     * - 密码为随机 UUID 的 BCrypt 哈希，任何人（含用户本人）都无法用密码登录该账号；
     * - 昵称为“微信用户+随机后缀”，不做账号接管、不合并既有账号。
     */
    @Transactional
    public SysUser createCustomer(String openid, String unionid) {
        Instant now = Instant.now();
        SysUser user = new SysUser();
        user.setUsername(USERNAME_PREFIX + sha256(openid).substring(0, 16));
        user.setPasswordHash(passwordEncoder.encode(randomSecret()));
        user.setRole(SysUser.ROLE_CUSTOMER);
        user.setNickname(NICKNAME_PREFIX + randomSuffix());
        user.setStatus(SysUser.STATUS_ACTIVE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        sysUserMapper.insert(user);

        UserWechatBinding binding = new UserWechatBinding();
        binding.setUserId(user.getId());
        binding.setOpenid(openid);
        binding.setUnionid(unionid);
        binding.setCreatedAt(now);
        binding.setUpdatedAt(now);
        bindingMapper.insert(binding);
        return user;
    }

    /** 并发首次登录后重查绑定（唯一键兜底路径） */
    public UserWechatBinding findByOpenid(String openid) {
        return bindingMapper.selectOne(
                Wrappers.<UserWechatBinding>lambdaQuery().eq(UserWechatBinding::getOpenid, openid));
    }

    private static String randomSecret() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    private static String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
