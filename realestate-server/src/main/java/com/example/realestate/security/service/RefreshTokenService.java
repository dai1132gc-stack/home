package com.example.realestate.security.service;

import com.example.realestate.common.enums.ClientType;
import com.example.realestate.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Set;

import static com.example.realestate.common.enums.ErrorCode.REFRESH_REPLAY;
import static com.example.realestate.common.enums.ErrorCode.TOKEN_INVALID;

/**
 * Refresh Token 会话服务：Redis 只保存令牌 SHA-256 哈希摘要，不保存明文。
 * 每次刷新轮换令牌；检测到旧令牌重放时撤销整个会话族。
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final String TOKEN_KEY = "auth:rt:";
    private static final String FAMILY_KEY = "auth:family:";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_ROTATED = "ROTATED";
    private static final String STATUS_REVOKED = "REVOKED";
    /** 轮换/撤销后旧令牌摘要保留的窗口，用于重放检测 */
    private static final Duration STALE_KEEP = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RefreshTokenService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /** Redis 中的会话摘要（JSON） */
    public record RefreshSession(Long userId, String role, ClientType client, String familyId, String status) {
        RefreshSession withStatus(String newStatus) {
            return new RefreshSession(userId, role, client, familyId, newStatus);
        }
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
     * 刷新：校验旧令牌并轮换。
     * 旧令牌已被轮换（ROTATED）→ 判定重放，撤销整个会话族；
     * 旧令牌不存在/已撤销/过期 → TOKEN_INVALID。
     */
    public RotatedToken refresh(String presentedToken, Duration ttl) {
        String hash = sha256(presentedToken);
        RefreshSession session = loadSession(hash);
        if (session == null) {
            throw new BusinessException(TOKEN_INVALID);
        }
        switch (session.status()) {
            case STATUS_ROTATED -> {
                log.warn("检测到 Refresh Token 重放，撤销会话族 familyId={} userId={}",
                        session.familyId(), session.userId());
                revokeFamily(session.familyId());
                throw new BusinessException(REFRESH_REPLAY);
            }
            case STATUS_REVOKED -> throw new BusinessException(TOKEN_INVALID);
            case STATUS_ACTIVE -> {
                mark(hash, session, STATUS_ROTATED);
                return rotate(session.userId(), session.role(), session.client(), session.familyId(), ttl);
            }
            default -> throw new BusinessException(TOKEN_INVALID);
        }
    }

    /** 退出登录：撤销当前令牌 */
    public void revoke(String presentedToken) {
        String hash = sha256(presentedToken);
        RefreshSession session = loadSession(hash);
        if (session != null && STATUS_ACTIVE.equals(session.status())) {
            mark(hash, session, STATUS_REVOKED);
            log.info("撤销 Refresh Token 会话 familyId={} userId={}", session.familyId(), session.userId());
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

    private void mark(String hash, RefreshSession session, String status) {
        saveSession(hash, session.withStatus(status), STALE_KEEP);
    }

    private void saveSession(String hash, RefreshSession session, Duration ttl) {
        try {
            redis.opsForValue().set(TOKEN_KEY + hash, objectMapper.writeValueAsString(session), ttl);
        } catch (Exception e) {
            throw new IllegalStateException("Redis 会话写入失败", e);
        }
    }

    private RefreshSession loadSession(String hash) {
        String raw = redis.opsForValue().get(TOKEN_KEY + hash);
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, RefreshSession.class);
        } catch (Exception e) {
            throw new IllegalStateException("Redis 会话解析失败", e);
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
