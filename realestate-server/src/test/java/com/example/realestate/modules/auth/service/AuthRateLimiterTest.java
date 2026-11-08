package com.example.realestate.modules.auth.service;

import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * 认证限流（V6.0.1 F4）：登录按 IP+用户名哈希双维度、微信登录按 IP；
 * 超限 429；不同用户名互不影响；Redis 异常 fail-open。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthRateLimiterTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;

    private AuthRateLimiter limiter;
    private final Map<String, Long> counters = new HashMap<>();

    @BeforeEach
    void setUp() {
        limiter = new AuthRateLimiter(redis);
        doAnswer(inv -> {
            String key = inv.getArgument(0);
            long next = counters.getOrDefault(key, 0L) + 1;
            counters.put(key, next);
            return next;
        }).when(valueOps).increment(anyString());
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(true);
        when(redis.delete(anyString())).thenAnswer(inv -> {
            counters.remove(inv.getArgument(0));
            return true;
        });
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("登录：同一 IP 第 10 次放行，第 11 次 429")
    void loginIpLimitedAt11th() {
        for (int i = 0; i < 10; i++) {
            int n = i;
            // 每次换用户名，排除用户名维度干扰
            assertThatCode(() -> limiter.checkLogin("10.0.0.1", "user" + n))
                    .doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> limiter.checkLogin("10.0.0.1", "user-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    @DisplayName("登录：同一用户名第 5 次放行，第 6 次 429；不同用户名互不影响")
    void loginUsernameLimitedAt6th() {
        for (int i = 0; i < 5; i++) {
            // 每次换 IP，排除 IP 维度干扰
            String ip = "10.0.1." + i;
            assertThatCode(() -> limiter.checkLogin(ip, "victim"))
                    .doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> limiter.checkLogin("10.0.1.99", "victim"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
        assertThatCode(() -> limiter.checkLogin("10.0.1.99", "other"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("微信登录：同一 IP 第 11 次 429；与登录计数互相独立")
    void wechatLimitedIndependently() {
        for (int i = 0; i < 10; i++) {
            String username = "user" + i;
            assertThatCode(() -> limiter.checkWechat("10.0.2.1")).doesNotThrowAnyException();
            assertThatCode(() -> limiter.checkLogin("10.0.2.1", username)).doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> limiter.checkWechat("10.0.2.1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    @DisplayName("成功登录清零用户名失败计数，正常重复登录不被误伤")
    void successResetsUsernameFailures() {
        for (int i = 0; i < 5; i++) {
            String ip = "10.0.5." + i;
            limiter.checkLogin(ip, "repeat-user");
        }
        assertThatThrownBy(() -> limiter.checkLogin("10.0.5.99", "repeat-user"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);

        limiter.resetLoginFailures("repeat-user");
        assertThatCode(() -> limiter.checkLogin("10.0.5.99", "repeat-user"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Redis 异常：fail-open 放行并仅告警")
    void redisFailureFailsOpen() {
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("redis down"));
        assertThatCode(() -> limiter.checkLogin("10.0.3.1", "admin")).doesNotThrowAnyException();
        assertThatCode(() -> limiter.checkWechat("10.0.3.1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("用户名不写入 Redis key（仅 SHA-256 摘要）")
    void usernameNotStoredInPlaintext() {
        limiter.checkLogin("10.0.4.1", "plain-username");
        assertThatCode(() -> {
            boolean leaked = counters.keySet().stream().anyMatch(k -> k.contains("plain-username"));
            if (leaked) {
                throw new IllegalStateException("username leaked into redis key");
            }
        }).doesNotThrowAnyException();
    }
}
