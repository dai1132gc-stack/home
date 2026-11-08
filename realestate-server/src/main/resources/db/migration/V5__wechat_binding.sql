-- ============================================================
-- V5__wechat_binding.sql — W5 微信 code 登录绑定
-- 范围：仅新增 user_wechat_binding，不修改 sys_user，不改 V1～V4。
-- 约定：
--   1. openid 唯一：同一微信身份只能绑定一个系统用户；
--   2. 不保存 session_key（微信会话密钥只在登录瞬间使用，不落库、不写日志）；
--   3. 首次登录创建 CUSTOMER 的系统账号仍写 sys_user，绑定关系独立成表；
--   4. 时间统一 DATETIME(3)，应用层写 UTC Instant。
-- ============================================================

CREATE TABLE user_wechat_binding (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT      NOT NULL COMMENT 'sys_user.id',
    openid     VARCHAR(64) NOT NULL COMMENT '微信小程序 openid（唯一，不对外输出）',
    unionid    VARCHAR(64) NULL COMMENT '微信 unionid（可空，仅记录）',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间(UTC)',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间(UTC)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wechat_binding_openid (openid),
    KEY idx_wechat_binding_user (user_id),
    KEY idx_wechat_binding_unionid (unionid)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='微信小程序账号绑定';
