package com.example.realestate.infrastructure.lock;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 基于 Redis 的轻量分布式锁（单实例定时任务防重复执行）。
 * 只提供 SET NX + TTL 与「比对 token 后删除」，不实现可重入与续期，足够覆盖每日到期任务。
 */
@Component
public class RedisDistributedLock {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redis;

    public RedisDistributedLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * @return 获取成功返回 token（解锁时必须回传）；已被占用返回 null
     */
    public String tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString().replace("-", "");
        Boolean acquired = redis.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(acquired) ? token : null;
    }

    public void unlock(String key, String token) {
        if (token == null) {
            return;
        }
        try {
            redis.execute(UNLOCK_SCRIPT, List.of(key), token);
        } catch (RuntimeException ignored) {
            // 解锁失败不阻塞业务：锁会在 TTL 后自动失效
        }
    }
}
