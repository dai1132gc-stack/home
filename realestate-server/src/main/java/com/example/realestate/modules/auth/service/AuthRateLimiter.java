package com.example.realestate.modules.auth.service;

import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * 认证入口限流（V6.0.1 修复 F4）：
 * - /auth/login：按 IP（10 次/分钟）+ 用户名 SHA-256（5 次/分钟）双维度计数，防密码爆破；
 * - /auth/wechat-login：按 IP（10 次/分钟），防 jscode2session 上游调用放大。
 * 用户名不明文写入 Redis key；超限统一 429（COMMON_42901），不暴露账号是否存在；
 * Redis 异常时 fail-open 并记录告警（与测算限流口径一致，见技术方案 24.9/25.8）。
 */
@Component
public class AuthRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimiter.class);
    private static final int LOGIN_IP_LIMIT = 10;
    private static final int LOGIN_USER_LIMIT = 5;
    private static final int WECHAT_IP_LIMIT = 10;
    private static final String PREFIX = "ratelimit:auth:";

    private final StringRedisTemplate redis;

    public AuthRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 账号密码登录：IP 与用户名双维度，任一超限即拒绝 */
    public void checkLogin(String clientIp, String username) {
        String ip = normalize(clientIp);
        incr(PREFIX + "login:ip:" + ip + ":" + minute(), LOGIN_IP_LIMIT, ip);
        String userKey = username == null || username.isBlank() ? "anonymous" : sha256(username);
        incr(PREFIX + "login:user:" + userKey + ":" + minute(), LOGIN_USER_LIMIT, ip);
    }

    /** 登录成功后清除该用户名本分钟的失败计数，避免正常重复登录被误伤 */
    public void resetLoginFailures(String username) {
        String userKey = username == null || username.isBlank() ? "anonymous" : sha256(username);
        try {
            redis.delete(PREFIX + "login:user:" + userKey + ":" + minute());
        } catch (RuntimeException e) {
            log.warn("认证限流失败计数清理失败（不影响登录）: {}", e.getMessage());
        }
    }

    /** 微信 code 登录：按 IP 限制上游调用放大 */
    public void checkWechat(String clientIp) {
        String ip = normalize(clientIp);
        incr(PREFIX + "wechat:ip:" + ip + ":" + minute(), WECHAT_IP_LIMIT, ip);
    }

    private void incr(String key, int limit, String ip) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofMinutes(2));
            }
            if (count != null && count > limit) {
                throw new BusinessException(ErrorCode.RATE_LIMITED);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("认证限流计数失败，按 fail-open 放行 ip={}: {}", ip, e.getMessage());
        }
    }

    private long minute() {
        return System.currentTimeMillis() / 60_000L;
    }

    private String normalize(String clientIp) {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
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
