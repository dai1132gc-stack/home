package com.example.realestate.security.jwt;

import com.example.realestate.common.enums.ClientType;
import com.example.realestate.config.AppProperties;
import com.example.realestate.security.model.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT（Access Token）生成/解析：签名密钥仅用于 Access Token；
 * Refresh Token 采用不透明随机串 + Redis 哈希会话（见 RefreshTokenService），
 * 比 JWT Refresh 更安全（客户端无法解码任何信息）且满足轮换/重放/撤销全部要求。
 * Claims 仅含 sub/jti/username/role/client/type，不保存手机号、openid、密码。
 */
@Component
public class JwtTokenProvider {

    public static final String TYPE_ACCESS = "access";

    private final SecretKey accessKey;
    private final long accessTtlSeconds;

    public JwtTokenProvider(AppProperties properties) {
        AppProperties.Jwt jwt = properties.jwt();
        this.accessKey = buildKey(jwt.accessSecret(), "JWT_ACCESS_SECRET");
        this.accessTtlSeconds = jwt.accessTtlMinutes() * 60L;
    }

    private SecretKey buildKey(String secret, String envName) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(envName + " 长度不足：HMAC-SHA256 密钥至少 32 字节");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public record AccessClaims(Long userId, String username, String role, ClientType client, String jti) {
    }

    public String generateAccessToken(LoginUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.userId()))
                .id(UUID.randomUUID().toString())
                .claim("username", user.username())
                .claim("role", user.role())
                .claim("client", user.client().name())
                .claim("type", TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
                .signWith(accessKey)
                .compact();
    }

    /** 解析并校验 Access Token（签名/有效期/type），失败抛 JwtException */
    public AccessClaims parseAccessToken(String token) {
        Claims claims = parse(token, accessKey);
        requireType(claims, TYPE_ACCESS);
        return new AccessClaims(
                Long.valueOf(claims.getSubject()),
                claims.get("username", String.class),
                claims.get("role", String.class),
                ClientType.valueOf(claims.get("client", String.class)),
                claims.getId());
    }

    private Claims parse(String token, SecretKey key) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private void requireType(Claims claims, String expectedType) {
        String type = claims.get("type", String.class);
        if (!expectedType.equals(type)) {
            throw new JwtException("token type mismatch");
        }
    }

    public long accessTtlSeconds() {
        return accessTtlSeconds;
    }
}
