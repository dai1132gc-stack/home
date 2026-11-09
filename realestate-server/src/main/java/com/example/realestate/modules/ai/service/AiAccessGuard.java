package com.example.realestate.modules.ai.service;

import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * AI 公开接口准入闸门（W6）：
 * - 每 IP 每分钟限流（默认 20），Redis 故障 fail-closed（503 AI_50302），与登录/测算 fail-open 不同；
 * - 每日 token 预算（key 带日期 + TTL），超限 429，不静默降级；
 * - 并发信号量（默认 4），获取超时 1s，失败 429，finally 释放。
 * token 估算：provider 返回 usage 用真实值，否则按字符数/4 的可解释估算。
 */
@Component
public class AiAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(AiAccessGuard.class);
    private static final String RATE_PREFIX = "ratelimit:ai:";
    private static final String TOKEN_PREFIX = "ai:tokens:";

    private final StringRedisTemplate redis;
    private final AiProperties properties;
    private final Semaphore semaphore;

    public AiAccessGuard(StringRedisTemplate redis, AiProperties properties) {
        this.redis = redis;
        this.properties = properties;
        this.semaphore = new Semaphore(properties.concurrency());
    }

    /** 每 IP 每分钟限流；Redis 故障抛 503（fail-closed） */
    public void checkRateLimit(String clientIp) {
        String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        String key = RATE_PREFIX + "ip:" + ip + ":" + (System.currentTimeMillis() / 60_000L);
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofMinutes(2));
            }
            if (count != null && count > properties.rateLimitPerMinute()) {
                throw new BusinessException(ErrorCode.RATE_LIMITED);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("AI 限流组件不可用（fail-closed）: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AI_LIMITER_UNAVAILABLE);
        }
    }

    /** 检查并预留每日 token 预算（估算值）；超限 429；Redis 故障 fail-closed */
    public void checkDailyTokenBudget(int estimatedTokens) {
        if (properties.dailyTokenLimit() <= 0) {
            return;
        }
        String key = TOKEN_PREFIX + LocalDate.now(ZoneOffset.UTC);
        try {
            Long total = redis.opsForValue().increment(key, estimatedTokens);
            if (total != null && total == estimatedTokens) {
                redis.expire(key, Duration.ofDays(2));
            }
            if (total != null && total > properties.dailyTokenLimit()) {
                throw new BusinessException(ErrorCode.RATE_LIMITED, "智能服务今日额度已用完");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("AI 日限额组件不可用（fail-closed）: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AI_LIMITER_UNAVAILABLE);
        }
    }

    /** 获取并发许可，1s 内未获取到返回 false；调用方须在 finally 中 release */
    public boolean tryAcquire() {
        try {
            return semaphore.tryAcquire(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void release() {
        semaphore.release();
    }

    /** provider 未返回 usage 时的可解释估算：约 4 字符/token */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }
}
