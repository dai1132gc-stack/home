package com.example.realestate.modules.ai.service;

import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.config.AiProperties;
import com.example.realestate.modules.ai.mapper.AiInteractionLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@DisplayName("AI 准入闸门（fail-closed）与统计聚合")
class AiGuardAndMetricsTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private AiInteractionLogMapper logMapper;

    private final AiProperties properties = new AiProperties(true, "http://x/v1", "k", "chat", "embed",
            12000, 200, 800, 1000, 20, 4, 0.30, 2);

    private AiAccessGuard newGuard() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        return new AiAccessGuard(redis, properties);
    }

    @Test
    @DisplayName("每 IP 每分钟超限：429")
    void rateLimited() {
        AiAccessGuard guard = newGuard();
        when(valueOps.increment(anyString())).thenReturn(21L);
        assertThatThrownBy(() -> guard.checkRateLimit("1.2.3.4"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    @DisplayName("Redis 故障：AI 限流 fail-closed 503 AI_50302（不同于登录 fail-open）")
    void redisDownFailClosed() {
        AiAccessGuard guard = newGuard();
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("redis down"));
        assertThatThrownBy(() -> guard.checkRateLimit("1.2.3.4"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.AI_LIMITER_UNAVAILABLE);
    }

    @Test
    @DisplayName("日 token 预算超限：429；Redis 故障 fail-closed")
    void dailyBudget() {
        AiAccessGuard guard = newGuard();
        when(valueOps.increment(anyString(), anyLong())).thenReturn(2000L);
        assertThatThrownBy(() -> guard.checkDailyTokenBudget(100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.RATE_LIMITED);
        when(valueOps.increment(anyString(), anyLong())).thenThrow(new RuntimeException("redis down"));
        assertThatThrownBy(() -> guard.checkDailyTokenBudget(100))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(ErrorCode.AI_LIMITER_UNAVAILABLE);
    }

    @Test
    @DisplayName("并发信号量：占满后获取失败且可释放")
    void semaphore() {
        AiAccessGuard guard = newGuard();
        assertThat(guard.tryAcquire()).isTrue();
        assertThat(guard.tryAcquire()).isTrue();
        assertThat(guard.tryAcquire()).isFalse(); // concurrency=2
        guard.release();
        assertThat(guard.tryAcquire()).isTrue();
    }

    @Test
    @DisplayName("token 估算：4 字符/token")
    void estimate() {
        assertThat(AiAccessGuard.estimateTokens(null)).isZero();
        assertThat(AiAccessGuard.estimateTokens("abcd")).isEqualTo(1);
        assertThat(AiAccessGuard.estimateTokens("a".repeat(100))).isEqualTo(25);
    }

    @Test
    @DisplayName("metrics 聚合：状态分组、token 汇总、P95 计算")
    void metricsAggregate() {
        when(logMapper.countBySceneAndStatus(any(), any())).thenReturn(List.of(
                new AiInteractionLogMapper.SceneStatusCount("SMART_SEARCH", "SUCCESS", 8, 800, 200),
                new AiInteractionLogMapper.SceneStatusCount("SMART_SEARCH", "DEGRADED", 2, 0, 0),
                new AiInteractionLogMapper.SceneStatusCount("POLICY_QA", "FAILED", 1, 0, 0)));
        when(logMapper.selectLatencies(any(), any())).thenReturn(List.of(10, 20, 30, 40, 100));
        when(logMapper.selectRecent(any(), any())).thenReturn(List.of());

        AiMetricsService metricsService = new AiMetricsService(logMapper);
        AiMetricsService.AiMetrics m = metricsService.metrics(
                Instant.now().minusSeconds(86400), Instant.now());

        assertThat(m.total()).isEqualTo(11);
        assertThat(m.success()).isEqualTo(8);
        assertThat(m.degraded()).isEqualTo(2);
        assertThat(m.failed()).isEqualTo(1);
        assertThat(m.promptTokens()).isEqualTo(800);
        assertThat(m.completionTokens()).isEqualTo(200);
        assertThat(m.avgLatencyMs()).isEqualTo(40.0);
        assertThat(m.p95LatencyMs()).isEqualTo(100);
        assertThat(m.byScene()).hasSize(2);
        assertThat(m.byScene().get(0).scene).isEqualTo("SMART_SEARCH");
        assertThat(m.byScene().get(0).total).isEqualTo(10);
    }
}
