package com.example.realestate.modules.calculator.service.impl;

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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("测算限流：每 IP 每分钟 60 次")
class CalculatorRateLimiterTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private CalculatorRateLimiter limiter;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOperations);
        limiter = new CalculatorRateLimiter(redis);
    }

    @Test
    @DisplayName("首次请求写入计数并设置 2 分钟过期")
    void firstRequestSetsExpiry() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        limiter.check("203.0.113.1");

        verify(redis).expire(anyString(), eq(Duration.ofMinutes(2)));
    }

    @Test
    @DisplayName("第 61 次请求返回 429")
    void rejectsOverLimit() {
        when(valueOperations.increment(anyString())).thenReturn(61L);

        assertThatThrownBy(() -> limiter.check("203.0.113.1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    @DisplayName("Redis 故障时 fail-open，不阻塞公开测算")
    void failsOpenWhenRedisDown() {
        when(valueOperations.increment(anyString())).thenThrow(new IllegalStateException("redis down"));

        assertThatCode(() -> limiter.check("203.0.113.1")).doesNotThrowAnyException();
    }
}
