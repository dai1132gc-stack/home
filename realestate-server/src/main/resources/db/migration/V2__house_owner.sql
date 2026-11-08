-- ============================================================
-- V2__house_owner.sql — W2 房源、媒体、收藏与客源（房东）
-- 约定：
--   1. 时间统一 DATETIME(3)，由应用层写 UTC Instant；
--   2. house / owner_profile 使用 version 乐观锁 + deleted 逻辑删除；
--   3. 金额、面积使用 DECIMAL(12,2)；
--   4. 筛选、排序、数据权限条件均在 SQL 层完成，索引围绕真实查询建立。
-- ============================================================

CREATE TABLE house (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    title       VARCHAR(120)  NOT NULL COMMENT '房源标题',
    house_type  VARCHAR(10)   NOT NULL COMMENT 'RENT 出租 / SALE 出售',
    status      VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / PUBLISHED / OFFLINE',
    community   VARCHAR(100)  NOT NULL COMMENT '小区名称',
    region      VARCHAR(60)   NOT NULL COMMENT '区域（行政区/商圈）',
    address     VARCHAR(255)  NOT NULL COMMENT '详细地址',
    price       DECIMAL(12,2) NOT NULL COMMENT '租金(元/月)或售价(元)',
    area        DECIMAL(12,2) NOT NULL COMMENT '建筑面积(㎡)',
    rooms       INT           NOT NULL DEFAULT 1 COMMENT '室',
    bathrooms   INT           NOT NULL DEFAULT 1 COMMENT '卫',
    floor       VARCHAR(32)   NULL COMMENT '楼层描述，如 3/18',
    description TEXT          NULL COMMENT '房源描述',
    owner_id    BIGINT        NULL COMMENT '房东/客源ID（owner_profile.id）',
    version     INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted     TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 已删除',
    created_at  DATETIME(3)   NOT NULL COMMENT '创建时间(UTC)',
    updated_at  DATETIME(3)   NOT NULL COMMENT '更新时间(UTC)',
    PRIMARY KEY (id),
    KEY idx_house_status_type_region_price (status, house_type, region, price),
    KEY idx_house_status_created (status, created_at),
    KEY idx_house_owner (owner_id),
    KEY idx_house_deleted_created (deleted, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='房源';

CREATE TABLE house_media (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    house_id   BIGINT       NOT NULL COMMENT '房源ID',
    media_type VARCHAR(10)  NOT NULL DEFAULT 'IMAGE' COMMENT 'IMAGE / VIDEO',
    url        VARCHAR(500) NOT NULL COMMENT '访问地址（dev: /uploads/...；prod: OSS URL）',
    sort       INT          NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
    created_at DATETIME(3)  NOT NULL COMMENT '创建时间(UTC)',
    PRIMARY KEY (id),
    KEY idx_house_media_house_sort (house_id, sort, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='房源媒体';

CREATE TABLE house_favorite (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    house_id   BIGINT      NOT NULL COMMENT '房源ID',
    user_id    BIGINT      NOT NULL COMMENT '收藏用户ID（CUSTOMER）',
    created_at DATETIME(3) NOT NULL COMMENT '收藏时间(UTC)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_house_favorite_user_house (user_id, house_id),
    KEY idx_house_favorite_house (house_id),
    KEY idx_house_favorite_user_time (user_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='房源收藏';

CREATE TABLE owner_profile (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name       VARCHAR(64)  NOT NULL COMMENT '姓名',
    phone      VARCHAR(20)  NOT NULL COMMENT '手机号（仅 ADMIN 可见，VO 对非 ADMIN 脱敏）',
    type       VARCHAR(20)  NOT NULL DEFAULT 'LANDLORD' COMMENT 'LANDLORD 房东 / CUSTOMER_LEAD 客源',
    remark     VARCHAR(500) NULL COMMENT '备注',
    created_by BIGINT       NULL COMMENT '创建人（sys_user.id）',
    version    INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 已删除',
    created_at DATETIME(3)  NOT NULL COMMENT '创建时间(UTC)',
    updated_at DATETIME(3)  NOT NULL COMMENT '更新时间(UTC)',
    PRIMARY KEY (id),
    KEY idx_owner_type (type),
    KEY idx_owner_name (name),
    KEY idx_owner_phone (phone),
    KEY idx_owner_deleted_created (deleted, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='房东与客源档案';

CREATE TABLE customer_follow (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    owner_id       BIGINT       NOT NULL COMMENT '关联 owner_profile.id',
    content        VARCHAR(1000) NOT NULL COMMENT '跟进内容',
    follow_type    VARCHAR(20)  NOT NULL DEFAULT 'PHONE' COMMENT 'PHONE / WECHAT / VISIT / OTHER',
    next_follow_at DATETIME(3)  NULL COMMENT '下次跟进时间(UTC)',
    created_by     BIGINT       NULL COMMENT '跟进人（sys_user.id）',
    created_at     DATETIME(3)  NOT NULL COMMENT '创建时间(UTC)',
    PRIMARY KEY (id),
    KEY idx_customer_follow_owner_time (owner_id, created_at),
    KEY idx_customer_follow_next (next_follow_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='客源跟进记录（W3 启用）';
