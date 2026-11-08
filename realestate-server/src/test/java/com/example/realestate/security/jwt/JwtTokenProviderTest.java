package com.example.realestate.security.jwt;

import com.example.realestate.common.enums.ClientType;
import com.example.realestate.config.AppProperties;
import com.example.realestate.security.model.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String ACCESS_SECRET = "unit-test-access-secret-0123456789abcdef0123456789abcdef";
    private static final List<String> ORIGINS = List.of("http://localhost:5174");

    private JwtTokenProvider provider;
    private JwtTokenProvider expiredProvider;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt(ACCESS_SECRET, "another-refresh-secret-0123456789abcdef01234567", 15, 7),
                new AppProperties.Cors(ORIGINS),
                new AppProperties.Bootstrap(null, null));
        provider = new JwtTokenProvider(properties);
        // TTL 1 分钟的 provider，用于构造过期场景验证
        expiredProvider = new JwtTokenProvider(new AppProperties(
                new AppProperties.Jwt(ACCESS_SECRET, "another-refresh-secret-0123456789abcdef01234567", 1, 7),
                new AppProperties.Cors(ORIGINS),
                new AppProperties.Bootstrap(null, null)));
    }

    private LoginUser user() {
        return new LoginUser(42L, "admin", "ADMIN", ClientType.WEB);
    }

    @Test
    @DisplayName("生成并解析 Access Token：claims 完整且不含敏感信息")
    void generateAndParse() {
        String token = provider.generateAccessToken(user());
        JwtTokenProvider.AccessClaims claims = provider.parseAccessToken(token);

        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.username()).isEqualTo("admin");
        assertThat(claims.role()).isEqualTo("ADMIN");
        assertThat(claims.client()).isEqualTo(ClientType.WEB);
        assertThat(claims.jti()).isNotBlank();
        // payload 不含手机号/openid/密码
        String payload = token.split("\\.")[1];
        assertThat(payload).doesNotContain("phone").doesNotContain("openid").doesNotContain("password");
    }

    @Test
    @DisplayName("篡改 token → 校验失败")
    void tamperedTokenRejected() {
        String token = provider.generateAccessToken(user());
        String tampered = token.substring(0, token.length() - 3) + "abc";
        assertThatThrownBy(() -> provider.parseAccessToken(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("过期 token → ExpiredJwtException")
    void expiredTokenRejected() throws InterruptedException {
        String token = expiredProvider.generateAccessToken(user());
        // TTL=1 分钟，手工签发已过期令牌验证解析器拒绝
        String expired = Jwts.builder()
                .subject("42")
                .id("jti-1")
                .claim("username", "admin")
                .claim("role", "ADMIN")
                .claim("client", "WEB")
                .claim("type", "access")
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(ACCESS_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThatThrownBy(() -> provider.parseAccessToken(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("密钥过短 → fail-fast")
    void shortSecretRejected() {
        AppProperties bad = new AppProperties(
                new AppProperties.Jwt("short", "another-refresh-secret-0123456789abcdef01234567", 15, 7),
                new AppProperties.Cors(ORIGINS),
                new AppProperties.Bootstrap(null, null));
        assertThatThrownBy(() -> new JwtTokenProvider(bad))
                .isInstanceOf(IllegalStateException.class);
    }
}
