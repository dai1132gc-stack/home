package com.example.realestate.security.service;

import com.example.realestate.common.enums.ClientType;
import com.example.realestate.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import static com.example.realestate.common.enums.ErrorCode.REFRESH_REPLAY;
import static com.example.realestate.common.enums.ErrorCode.TOKEN_INVALID;

/**
 * Refresh Token 会话服务：Redis 只保存令牌 SHA-256 哈希摘要，不保存明文。
 * 每次刷新轮换令牌；检测到旧令牌重放时撤销整个会话族。
 *
 * 原子性（V6.0.1 修复 F1）：「读取状态 + 条件标记 ROTATED/REVOKED」在 Redis 端由 Lua
 * 脚本原子完成，不存在 GET→SET 竞态窗口；同一 token 的两路并发刷新恰好一路成功、
 * 另一路命中 ROTATED 并按重放处理（撤销会话族）。客户端必须单飞刷新：
 * 并发重试同一旧 token 会被视为重放并要求重新登录（见技术方案 21.2）。
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final String TOKEN_KEY = "auth:rt:";
    private static final String FAMILY_KEY = "auth:family:";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_ROTATED = "ROTATED";
    /** 轮换/撤销后旧令牌摘要保留的窗口，用于重放检测 */
    private static final Duration STALE_KEEP = Duration.ofMinutes(10);

    /**
     * 原子刷新 + 轮换：仅当旧令牌为 ACTIVE 时，在同一脚本内完成
     * 「旧令牌标记 ROTATED → 写入新令牌 → 新令牌加入会话族」。
     * 这样败者按 ROTATED 撤销族时，胜者的新令牌必然已在族集合中，不会漏删。
     * 返回 nil 表示旧令牌不存在；返回 {'INVALID',''} 表示会话 JSON 无法解析。
     */
    static final DefaultRedisScript<List> REFRESH_SCRIPT = new DefaultRedisScript<>(
            """
            local raw = redis.call('GET', KEYS[1])
            if not raw then return nil end
            local ok, s = pcall(cjson.decode, raw)
            if not ok or type(s) ~= 'table' then return {'INVALID', ''} end
            local status = s['status'] or ''
            if status ~= 'ACTIVE' then return {status, raw} end
            s['status'] = 'ROTATED'
            redis.call('SET', KEYS[1], cjson.encode(s), 'PX', ARGV[1])
            local ns = {
              userId = s['userId'], role = s['role'], client = s['client'],
              familyId = s['familyId'], status = 'ACTIVE'
            }
            redis.call('SET', KEYS[2], cjson.encode(ns), 'PX', ARGV[2])
            redis.call('SADD', KEYS[3], ARGV[3])
            redis.call('PEXPIRE', KEYS[3], ARGV[4])
            return {'ACTIVE', raw}
            """,
            List.class);

    /** 原子撤销：仅 ACTIVE → REVOKED；成功返回 1，否则 0。 */
    static final DefaultRedisScript<Long> REVOKE_SCRIPT = new DefaultRedisScript<>(
            """
            local raw = redis.call('GET', KEYS[1])
            if not raw then return 0 end
            local ok, s = pcall(cjson.decode, raw)
            if not ok or type(s) ~= 'table' then return 0 end
            if s['status'] ~= 'ACTIVE' then return 0 end
            s['status'] = 'REVOKED'
            redis.call('SET', KEYS[1], cjson.encode(s), 'PX', ARGV[1])
            return 1
            """,
            Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RefreshTokenService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /** Redis 中的会话摘要（JSON） */
    public record RefreshSession(Long userId, String role, ClientType client, String familyId, String status) {
    }

    /** 轮换结果：新的 Refresh Token 明文（仅返回给客户端）+ 会话身份 */
    public record RotatedToken(String refreshToken, String familyId,
                               Long userId, String role, ClientType client) {
    }

    /** 登录：创建新会话族并签发首个 Refresh Token */
    public RotatedToken createSession(Long userId, String role, ClientType client, Duration ttl) {
        String familyId = newFamilyId();
        return rotate(userId, role, client, familyId, ttl);
    }

    /**
     * 刷新：Lua 原子校验旧令牌并标记 ROTATED，再签发新令牌。
     * 旧令牌已被轮换（ROTATED）→ 判定重放，撤销整个会话族；
     * 旧令牌不存在/已撤销/已过期/无法解析 → TOKEN_INVALID。
     */
    public RotatedToken refresh(String presentedToken, Duration ttl) {
        String hash = sha256(presentedToken);
        // 先读取 familyId 仅用于构造 Redis key；状态判断仍以 Lua 内的读取为准
        RefreshSession current = loadSession(hash);
        if (current == null) {
            throw new BusinessException(TOKEN_INVALID);
        }
        String newToken = newTokenValue();
        String newHash = sha256(newToken);
        List<?> result = redis.execute(REFRESH_SCRIPT,
                List.of(TOKEN_KEY + hash, TOKEN_KEY + newHash, FAMILY_KEY + current.familyId()),
                String.valueOf(STALE_KEEP.toMillis()),
                String.valueOf(ttl.toMillis()),
                newHash,
                String.valueOf(ttl.plus(STALE_KEEP).toMillis()));
        if (result == null || result.isEmpty()) {
            throw new BusinessException(TOKEN_INVALID);
        }
        String status = String.valueOf(result.get(0));
        if (!STATUS_ACTIVE.equals(status)) {
            if (STATUS_ROTATED.equals(status) && result.size() > 1) {
                RefreshSession stale = parse(String.valueOf(result.get(1)));
                if (stale != null) {
                    log.warn("检测到 Refresh Token 重放，撤销会话族 familyId={} userId={}",
                            stale.familyId(), stale.userId());
                    revokeFamily(stale.familyId());
                    throw new BusinessException(REFRESH_REPLAY);
                }
            }
            throw new BusinessException(TOKEN_INVALID);
        }
        RefreshSession session = parse(String.valueOf(result.get(1)));
        if (session == null) {
            // 极端情况：新令牌已写入但旧会话无法解析，主动删除新令牌，避免孤儿会话
            redis.delete(TOKEN_KEY + newHash);
            throw new BusinessException(TOKEN_INVALID);
        }
        return new RotatedToken(newToken, session.familyId(),
                session.userId(), session.role(), session.client());
    }

    /** 退出登录：Lua 原子撤销当前令牌（仅 ACTIVE → REVOKED，避免与刷新竞态） */
    public void revoke(String presentedToken) {
        String hash = sha256(presentedToken);
        Long revoked = redis.execute(REVOKE_SCRIPT,
                List.of(TOKEN_KEY + hash), String.valueOf(STALE_KEEP.toMillis()));
        if (revoked != null && revoked == 1L) {
            log.info("撤销 Refresh Token 会话 hash={}...", hash.substring(0, 8));
        }
    }

    private RotatedToken rotate(Long userId, String role, ClientType client, String familyId, Duration ttl) {
        String token = newTokenValue();
        String hash = sha256(token);
        saveSession(hash, new RefreshSession(userId, role, client, familyId, STATUS_ACTIVE), ttl);
        redis.opsForSet().add(FAMILY_KEY + familyId, hash);
        redis.expire(FAMILY_KEY + familyId, ttl.plus(STALE_KEEP));
        return new RotatedToken(token, familyId, userId, role, client);
    }

    private RefreshSession loadSession(String hash) {
        String raw = redis.opsForValue().get(TOKEN_KEY + hash);
        return raw == null ? null : parse(raw);
    }

    private void saveSession(String hash, RefreshSession session, Duration ttl) {
        try {
            redis.opsForValue().set(TOKEN_KEY + hash, objectMapper.writeValueAsString(session), ttl);
        } catch (Exception e) {
            throw new IllegalStateException("Redis 会话写入失败", e);
        }
    }

    private RefreshSession parse(String raw) {
        try {
            return objectMapper.readValue(raw, RefreshSession.class);
        } catch (Exception e) {
            log.warn("Redis 会话解析失败（按无效令牌处理）: {}", e.getMessage());
            return null;
        }
    }

    private void revokeFamily(String familyId) {
        Set<String> hashes = redis.opsForSet().members(FAMILY_KEY + familyId);
        if (hashes != null && !hashes.isEmpty()) {
            hashes.stream().map(TOKEN_KEY::concat).forEach(redis::delete);
        }
        redis.delete(FAMILY_KEY + familyId);
    }

    private String newTokenValue() {
        return java.util.UUID.randomUUID().toString().replace("-", "")
                + java.util.UUID.randomUUID().toString().replace("-", "");
    }

    private String newFamilyId() {
        return java.util.UUID.randomUUID().toString();
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
