package com.example.realestate.modules.user.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 登录审计日志：仅记录登录/刷新/退出事件元数据，不保存任何令牌。
 */
@Data
@NoArgsConstructor
@TableName("auth_login_log")
public class AuthLoginLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    private String clientType;

    private String eventType;

    private Boolean success;

    private String failReason;

    private String traceId;

    private Instant createdAt;
}
