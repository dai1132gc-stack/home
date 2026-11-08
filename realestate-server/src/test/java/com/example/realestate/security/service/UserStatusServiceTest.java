package com.example.realestate.security.service;

import com.example.realestate.modules.user.mapper.SysUserMapper;
import com.example.realestate.modules.user.model.entity.SysUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 账号状态缓存（V6.0.1 F3）：ACTIVE 放行、DISABLED/不存在拒绝、
 * 缓存命中只查一次库、evict 后回源、Redis 异常回退查库、查库异常 fail-open。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserStatusServiceTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private SysUserMapper sysUserMapper;

    private UserStatusService service;
    private final Map<String, String> kv = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new UserStatusService(redis, sysUserMapper);
        when(valueOps.get(anyString())).thenAnswer(inv -> kv.get(inv.getArgument(0)));
        doAnswer(inv -> {
            kv.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), any(Duration.class));
        doAnswer(inv -> {
            kv.remove(inv.getArgument(0));
            return null;
        }).when(redis).delete(anyString());
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    private SysUser user(long id, String status) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setStatus(status);
        return u;
    }

    @Test
    @DisplayName("ACTIVE 账号放行；DISABLED 与不存在账号拒绝")
    void activeAndDisabled() {
        when(sysUserMapper.selectById(1L)).thenReturn(user(1L, SysUser.STATUS_ACTIVE));
        when(sysUserMapper.selectById(2L)).thenReturn(user(2L, "DISABLED"));
        when(sysUserMapper.selectById(3L)).thenReturn(null);

        assertThat(service.isActive(1L)).isTrue();
        assertThat(service.isActive(2L)).isFalse();
        assertThat(service.isActive(3L)).isFalse();
    }

    @Test
    @DisplayName("缓存命中：两次校验只查一次库")
    void cacheHitAvoidsSecondDbQuery() {
        when(sysUserMapper.selectById(1L)).thenReturn(user(1L, SysUser.STATUS_ACTIVE));
        assertThat(service.isActive(1L)).isTrue();
        assertThat(service.isActive(1L)).isTrue();
        verify(sysUserMapper, times(1)).selectById(1L);
    }

    @Test
    @DisplayName("evict 后下次校验回源查库，禁用立即生效")
    void evictForcesReload() {
        SysUser u = user(1L, SysUser.STATUS_ACTIVE);
        when(sysUserMapper.selectById(1L)).thenReturn(u);
        assertThat(service.isActive(1L)).isTrue();

        u.setStatus("DISABLED");
        service.evict(1L);
        assertThat(service.isActive(1L)).isFalse();
        verify(sysUserMapper, times(2)).selectById(1L);
    }

    @Test
    @DisplayName("Redis 异常：回退查库，不影响校验结果")
    void redisFailureFallsBackToDb() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));
        when(sysUserMapper.selectById(1L)).thenReturn(user(1L, SysUser.STATUS_ACTIVE));
        when(sysUserMapper.selectById(2L)).thenReturn(user(2L, "DISABLED"));

        assertThat(service.isActive(1L)).isTrue();
        assertThat(service.isActive(2L)).isFalse();
    }

    @Test
    @DisplayName("查库异常：fail-open 放行（避免数据库瞬断全站 401）")
    void dbFailureFailsOpen() {
        when(sysUserMapper.selectById(1L)).thenThrow(new RuntimeException("db down"));
        assertThat(service.isActive(1L)).isTrue();
    }
}
