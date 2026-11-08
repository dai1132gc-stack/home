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
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * 单元测试：用内存 kvStore 模拟 REFRESH_SCRIPT/REVOKE_SCRIPT 的 Lua 语义
 * （原子 GET→条件标记），验证刷新轮换、重放撤销、登出等行为。
 * 脚本在真实 Redis 内原子执行；并发“恰 1 成功 1 401”由 docs/smoke-auth-hardening.sh 运行时验证。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenServiceTest {

    private static final Duration TTL = Duration.ofDays(7);
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
        service = new RefreshTokenService(redis, MAPPER);

        doAnswer(inv -> {
            kvStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), any(Duration.class));

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
        when(valueOps.get(anyString())).thenAnswer(inv -> kvStore.get(inv.getArgument(0)));

        // 撤销：单 key + 单参数
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString()))
                .thenAnswer(inv -> {
                    List<String> keys = inv.getArgument(1);
                    String raw = kvStore.get(keys.get(0));
                    return emulateMark(raw, "REVOKED") ? 1L : 0L;
                });

        // 刷新：旧 key + 新 key + 族 key；ARGV: staleMs/newTtlMs/newHash/familyTtlMs
        when(redis.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    List<String> keys = inv.getArgument(1);
                    String oldKey = keys.get(0);
                    String newKey = keys.get(1);
                    String familyKey = keys.get(2);
                    String raw = kvStore.get(oldKey);
                    if (raw == null) {
                        return null;
                    }
                    RefreshTokenService.RefreshSession s = readSession(raw);
                    if (s == null) {
                        return List.of("INVALID", "");
                    }
                    if (!"ACTIVE".equals(s.status())) {
                        return List.of(s.status(), raw);
                    }
                    kvStore.put(oldKey, writeStatus(s, "ROTATED"));
                    kvStore.put(newKey, writeStatus(s, "ACTIVE"));
                    String newHash = inv.getArgument(4);
                    setStore.computeIfAbsent(familyKey, k -> new HashSet<>()).add(newHash);
                    return List.of("ACTIVE", raw);
                });
    }

    private RefreshTokenService.RefreshSession readSession(String raw) {
        try {
            return MAPPER.readValue(raw, RefreshTokenService.RefreshSession.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 模拟 Lua 内的原子条件标记；仅 ACTIVE 可迁移，返回是否成功 */
    private boolean emulateMark(String raw, String targetStatus) {
        RefreshTokenService.RefreshSession s = raw == null ? null : readSession(raw);
        if (s == null || !"ACTIVE".equals(s.status())) {
            return false;
        }
        try {
            String key = "auth:rt:" + RefreshTokenService.sha256(
                    // 从 kvStore 反查 key 不可行；由调用方按同一 raw 定位
                    "");
            // 不反查 key：直接按值匹配（测试内唯一）
            kvStore.entrySet().stream()
                    .filter(e -> e.getValue().equals(raw))
                    .findFirst()
                    .ifPresent(e -> e.setValue(writeStatus(s, targetStatus)));
            return true;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String writeStatus(RefreshTokenService.RefreshSession s, String status) {
        try {
            return MAPPER.writeValueAsString(new RefreshTokenService.RefreshSession(
                    s.userId(), s.role(), s.client(), s.familyId(), status));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
    @DisplayName("同一令牌第二次刷新（等价并发败者）：按重放撤销会话族，新令牌一并失效")
    void secondRefreshTreatedAsReplay() {
        RefreshTokenService.RotatedToken created =
                service.createSession(1L, "ADMIN", ClientType.WEB, TTL);
        RefreshTokenService.RotatedToken rotated = service.refresh(created.refreshToken(), TTL);

        // 第二路使用同一旧令牌：Lua 原子读到 ROTATED → 重放
        assertThatThrownBy(() -> service.refresh(created.refreshToken(), TTL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重放");

        // 会话族撤销后，第一路刚签发的新令牌也不可用（安全优先，见技术方案 21.2）
        assertThatThrownBy(() -> service.refresh(rotated.refreshToken(), TTL))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("未知令牌 → TOKEN_INVALID（Lua 返回 nil）")
    void unknownTokenRejected() {
        assertThatThrownBy(() -> service.refresh("not-exist-token", TTL))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("退出登录：当前令牌原子标记 REVOKED，再次刷新失败；重复登出为空操作")
    void logoutRevokesToken() {
        RefreshTokenService.RotatedToken created =
                service.createSession(1L, "CUSTOMER", ClientType.MINI, TTL);
        service.revoke(created.refreshToken());

        String session = kvStore.get("auth:rt:" + RefreshTokenService.sha256(created.refreshToken()));
        assertThat(session).contains("REVOKED");
        assertThatThrownBy(() -> service.refresh(created.refreshToken(), TTL))
                .isInstanceOf(BusinessException.class);

        // 重复登出不抛异常（仅 ACTIVE → REVOKED）
        service.revoke(created.refreshToken());
    }
}
