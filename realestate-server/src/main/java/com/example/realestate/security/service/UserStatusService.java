package com.example.realestate.security.service;

import com.example.realestate.modules.user.mapper.SysUserMapper;
import com.example.realestate.modules.user.model.entity.SysUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 账号状态缓存校验（V6.0.1 修复 F3）：禁用账号的 Access Token 最迟 30 秒内失效，
 * 不再等到 15 分钟 TTL 自然过期。
 *
 * 策略：
 * - Redis 缓存 auth:user:status:{userId}，TTL 30 秒；miss 回源 sys_user；
 * - Redis 异常：warn 并回退直接查库（不引入 500）；
 * - 查库异常：error 并 fail-open 按活跃放行（JWT 本身已通过签名校验；
 *   避免数据库瞬断导致全站 401，见技术方案 25.8 已知限制）；
 * - 未来新增「禁用账号」管理接口时，提交后必须调用 evict(userId)。
 */
@Component
public class UserStatusService {

    private static final Logger log = LoggerFactory.getLogger(UserStatusService.class);
    private static final Duration TTL = Duration.ofSeconds(30);
    private static final String KEY_PREFIX = "auth:user:status:";
    private static final String MISSING = "MISSING";

    private final StringRedisTemplate redis;
    private final SysUserMapper sysUserMapper;

    public UserStatusService(StringRedisTemplate redis, SysUserMapper sysUserMapper) {
        this.redis = redis;
        this.sysUserMapper = sysUserMapper;
    }

    /** 账号是否存在且为 ACTIVE；异常路径见类注释 */
    public boolean isActive(Long userId) {
        String key = KEY_PREFIX + userId;
        String cached = null;
        try {
            cached = redis.opsForValue().get(key);
        } catch (RuntimeException e) {
            log.warn("账号状态缓存读取失败，回退查库 userId={}: {}", userId, e.getMessage());
        }
        if (cached == null) {
            cached = loadFromDb(userId);
            if (cached == null) {
                // 查库失败：fail-open（见类注释），不写缓存
                return true;
            }
            try {
                redis.opsForValue().set(key, cached, TTL);
            } catch (RuntimeException e) {
                log.warn("账号状态缓存写入失败（不影响本次校验）userId={}: {}", userId, e.getMessage());
            }
        }
        return SysUser.STATUS_ACTIVE.equals(cached);
    }

    /** 账号状态变更后调用，强制下次请求回源 */
    public void evict(Long userId) {
        try {
            redis.delete(KEY_PREFIX + userId);
        } catch (RuntimeException e) {
            log.warn("账号状态缓存清除失败 userId={}: {}", userId, e.getMessage());
        }
    }

    /** 返回状态字符串；查库异常返回 null */
    private String loadFromDb(Long userId) {
        try {
            SysUser user = sysUserMapper.selectById(userId);
            return user == null ? MISSING : user.getStatus();
        } catch (RuntimeException e) {
            log.error("账号状态查库失败，按 fail-open 放行 userId={}: {}", userId, e.getMessage());
            return null;
        }
    }
}
