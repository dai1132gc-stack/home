-- ============================================================
-- V1__init_auth.sql — W1 认证核心表
-- 说明：初始账号不写死在迁移中，由启动引导（BootstrapInitializer）
--      从环境变量读取密码创建，避免弱密码入库。
-- ============================================================

CREATE TABLE sys_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(64)  NOT NULL COMMENT '登录名',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希',
    role          VARCHAR(20)  NOT NULL COMMENT 'ADMIN / CUSTOMER',
    nickname      VARCHAR(64)  NULL COMMENT '昵称',
    phone         VARCHAR(20)  NULL COMMENT '手机号（仅授权角色可见，接口脱敏）',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / DISABLED',
    created_at    DATETIME(3)  NOT NULL COMMENT '创建时间(UTC)',
    updated_at    DATETIME(3)  NOT NULL COMMENT '更新时间(UTC)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username),
    KEY idx_sys_user_role (role)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='系统用户';

CREATE TABLE auth_login_log (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT      NULL COMMENT '用户ID，登录失败且账号不存在时为空',
    username    VARCHAR(64) NOT NULL COMMENT '尝试登录的用户名',
    client_type VARCHAR(10) NOT NULL COMMENT 'WEB / MINI',
    event_type  VARCHAR(20) NOT NULL COMMENT 'LOGIN / REFRESH / LOGOUT',
    success     TINYINT(1)  NOT NULL COMMENT '是否成功',
    fail_reason VARCHAR(50) NULL COMMENT '失败原因码（不含敏感信息）',
    trace_id    VARCHAR(64) NULL COMMENT '请求追踪ID',
    created_at  DATETIME(3) NOT NULL COMMENT '事件时间(UTC)',
    PRIMARY KEY (id),
    KEY idx_login_log_username_time (username, created_at),
    KEY idx_login_log_user_time (user_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='登录审计日志';
