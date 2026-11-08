package com.example.realestate.security.service;

import com.example.realestate.common.enums.ClientType;
import com.example.realestate.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenServiceTest {

    private static final Duration TTL = Duration.ofDays(7);

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private SetOperations<String, String> setOps;

    private RefreshTokenService service;
    private final Map<String, String> kvStore = new HashMap<>();
    private final Map<String, Set<String>> setStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(redis, new ObjectMapper());

        doAnswer(inv -> {
            kvStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), any(Duration.class));
        when(valueOps.get(anyString())).thenAnswer(inv -> kvStore.get(inv.getArgument(0)));

        doAnswer(inv -> {
            setStore.computeIfAbsent(inv.getArgument(0), k -> new HashSet<>()).add(inv.getArgument(1));
            return 1L;
        }).when(setOps).add(anyString(), anyString());
        when(setOps.members(anyString())).thenAnswer(inv -> {
            Set<String> members = setStore.get(inv.getArgument(0));
            return members == null ? Set.of() : members;
        });

        doAnswer(inv -> {
            kvStore.remove(inv.getArgument(0));
            return null;
        }).when(redis).delete(anyString());
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(true);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForSet()).thenReturn(setOps);
    }

    @Test
    @DisplayName("登录创建会话：Redis 仅保存哈希摘要，不保存明文")
    void createSessionStoresHashOnly() {
        RefreshTokenService.RotatedToken created =
                service.createSession(1L, "ADMIN", ClientType.WEB, TTL);

        assertThat(created.refreshToken()).isNotBlank();
        assertThat(kvStore).doesNotContainValue(created.refreshToken());
        assertThat(kvStore.keySet()).anyMatch(k -> k.endsWith(RefreshTokenService.sha256(created.refreshToken())));
    }

    @Test
    @DisplayName("刷新轮换：旧令牌标记 ROTATED，签发新令牌")
    void refreshRotatesToken() {
        RefreshTokenService.RotatedToken created =
                service.createSession(1L, "CUSTOMER", ClientType.MINI, TTL);
        RefreshTokenService.RotatedToken rotated = service.refresh(created.refreshToken(), TTL);

        assertThat(rotated.refreshToken()).isNotEqualTo(created.refreshToken());
        assertThat(rotated.userId()).isEqualTo(1L);
        assertThat(rotated.client()).isEqualTo(ClientType.MINI);

        // 旧令牌会话被标记 ROTATED
        String oldSession = kvStore.get("auth:rt:" + RefreshTokenService.sha256(created.refreshToken()));
        assertThat(oldSession).contains("ROTATED");
        // 新令牌 ACTIVE
        String newSession = kvStore.get("auth:rt:" + RefreshTokenService.sha256(rotated.refreshToken()));
        assertThat(newSession).contains("ACTIVE");
    }

    @Test
    @DisplayName("重放旧令牌：撤销整个会话族，新令牌一并失效")
    void replayRevokesWholeFamily() {
        RefreshTokenService.RotatedToken created =
                service.createSession(1L, "ADMIN", ClientType.WEB, TTL);
        RefreshTokenService.RotatedToken rotated = service.refresh(created.refreshToken(), TTL);

        // 重放第一次使用的旧令牌
        assertThatThrownBy(() -> service.refresh(created.refreshToken(), TTL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重放");

        // 会话族撤销后，新令牌也不可用
        assertThatThrownBy(() -> service.refresh(rotated.refreshToken(), TTL))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("未知令牌 → TOKEN_INVALID")
    void unknownTokenRejected() {
        assertThatThrownBy(() -> service.refresh("not-exist-token", TTL))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("退出登录：当前令牌标记 REVOKED，再次刷新失败")
    void logoutRevokesToken() {
        RefreshTokenService.RotatedToken created =
                service.createSession(1L, "CUSTOMER", ClientType.MINI, TTL);
        service.revoke(created.refreshToken());

        String session = kvStore.get("auth:rt:" + RefreshTokenService.sha256(created.refreshToken()));
        assertThat(session).contains("REVOKED");
        assertThatThrownBy(() -> service.refresh(created.refreshToken(), TTL))
                .isInstanceOf(BusinessException.class);
    }
}
