package com.example.realestate.modules.calculator.service.impl;

import com.example.realestate.common.enums.ErrorCode;
import com.example.realestate.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 公开测算接口限流：每 IP 每分钟 60 次，Redis 计数（key 含分钟窗口）。
 * Redis 异常时 fail-open 并记录告警，避免限流组件故障拖垮公开工具；
 * 正常路径超限返回 429。
 */
@Component
public class CalculatorRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(CalculatorRateLimiter.class);
    private static final int LIMIT_PER_MINUTE = 60;
    private static final String KEY_PREFIX = "ratelimit:calculator:";

    private final StringRedisTemplate redis;

    public CalculatorRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void check(String clientIp) {
        String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        String key = KEY_PREFIX + ip + ":" + (System.currentTimeMillis() / 60_000L);
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofMinutes(2));
            }
            if (count != null && count > LIMIT_PER_MINUTE) {
                throw new BusinessException(ErrorCode.RATE_LIMITED);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("测算限流计数失败，按 fail-open 放行 ip={}: {}", ip, e.getMessage());
        }
    }
}
