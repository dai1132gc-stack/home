-- ============================================================
-- V3__appointment_contract.sql — W3 预约带看、合同与交易节点
-- 约定：
--   1. 时间统一 DATETIME(3)，由应用层写 UTC Instant；租期起止用 DATE；
--   2. contract / viewing_appointment 含 version 乐观锁 + deleted 逻辑删除；
--   3. 金额 DECIMAL(12,2)；状态为受控枚举（Service 状态机校验，非法迁移 409）；
--   4. 同一房源同时最多一份 PENDING_SIGN/ACTIVE 合同：
--      生成列 active_flag（占用时非空、其余为 NULL）+ 唯一键 (house_id, active_flag)，
--      MySQL 唯一索引允许多个 NULL，因此只约束占用中的合同；
--   5. 列表与数据权限条件全部下推 SQL，索引围绕真实查询建立。
-- ============================================================

CREATE TABLE viewing_appointment (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    house_id       BIGINT       NOT NULL COMMENT '房源ID',
    customer_id    BIGINT       NOT NULL COMMENT '客户用户ID（sys_user.id, CUSTOMER）',
    preferred_time DATETIME(3)  NOT NULL COMMENT '期望带看时间(UTC)',
    contact_phone  VARCHAR(20)  NOT NULL COMMENT '联系手机号（VO 对非 ADMIN 脱敏）',
    remark         VARCHAR(500) NULL COMMENT '客户备注',
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED/COMPLETED/CANCELLED/NO_SHOW',
    version        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 已删除',
    created_at     DATETIME(3)  NOT NULL COMMENT '创建时间(UTC)',
    updated_at     DATETIME(3)  NOT NULL COMMENT '更新时间(UTC)',
    PRIMARY KEY (id),
    KEY idx_appointment_customer_status_time (customer_id, status, preferred_time),
    KEY idx_appointment_house_time (house_id, preferred_time),
    KEY idx_appointment_status_time (status, preferred_time),
    KEY idx_appointment_deleted_time (deleted, preferred_time),
    KEY idx_appointment_deleted_created (deleted, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='预约带看';

CREATE TABLE contract (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    contract_no      VARCHAR(40)   NOT NULL COMMENT '合同编号（唯一）',
    house_id         BIGINT        NOT NULL COMMENT '房源ID',
    customer_id      BIGINT        NOT NULL COMMENT '承租客户用户ID',
    owner_id         BIGINT        NULL COMMENT '房东档案ID（owner_profile.id，可为空）',
    title            VARCHAR(200)  NOT NULL COMMENT '合同标题',
    amount           DECIMAL(12,2) NOT NULL COMMENT '租金/成交金额（元）',
    deposit          DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '押金（元）',
    payment_cycle    VARCHAR(20)   NOT NULL COMMENT 'MONTHLY/QUARTERLY/YEARLY',
    start_date       DATE          NOT NULL COMMENT '租期开始',
    end_date         DATE          NOT NULL COMMENT '租期结束',
    status           VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING_SIGN/ACTIVE/EXPIRED/TERMINATED/CANCELLED',
    template_version VARCHAR(32)   NOT NULL COMMENT '合同模板版本，如 STANDARD_LEASE-v1',
    snapshot_json    JSON          NOT NULL COMMENT '创建时房源/双方/条款快照，后续房源变更不影响历史合同',
    pdf_url          VARCHAR(500)  NULL COMMENT '私有存储相对 key（不对前端暴露公网地址）',
    signed_at        DATETIME(3)   NULL COMMENT '客户确认签署时间(UTC)',
    version          INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted          TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 已删除',
    created_at       DATETIME(3)   NOT NULL COMMENT '创建时间(UTC)',
    updated_at       DATETIME(3)   NOT NULL COMMENT '更新时间(UTC)',
    active_flag      VARCHAR(20) GENERATED ALWAYS AS (
        CASE WHEN status IN ('PENDING_SIGN', 'ACTIVE') THEN 'OCCUPIED' ELSE NULL END
        ) STORED COMMENT '占用标记：PENDING_SIGN/ACTIVE 非空，其余 NULL',
    PRIMARY KEY (id),
    UNIQUE KEY uk_contract_no (contract_no),
    UNIQUE KEY uk_contract_house_active (house_id, active_flag),
    KEY idx_contract_customer_status_created (customer_id, status, created_at),
    KEY idx_contract_status_created (status, created_at),
    KEY idx_contract_house (house_id),
    KEY idx_contract_deleted_created (deleted, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='租赁合同';

CREATE TABLE contract_party (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    contract_id    BIGINT       NOT NULL COMMENT '合同ID',
    party_type     VARCHAR(20)  NOT NULL COMMENT 'LESSOR 出租方 / LESSEE 承租方 / AGENT 居间方',
    name           VARCHAR(64)  NOT NULL COMMENT '姓名/名称',
    phone          VARCHAR(20)  NULL COMMENT '联系电话（落库原文，VO 按角色脱敏）',
    id_card_masked VARCHAR(32)  NULL COMMENT '证件号（仅保存脱敏结果，前 6 后 4）',
    created_at     DATETIME(3)  NOT NULL COMMENT '创建时间(UTC)',
    PRIMARY KEY (id),
    KEY idx_contract_party_contract_type (contract_id, party_type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='合同签署方（快照）';

CREATE TABLE contract_status_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    contract_id BIGINT       NOT NULL COMMENT '合同ID',
    from_status VARCHAR(20)  NULL COMMENT '原状态（创建时为空）',
    to_status   VARCHAR(20)  NOT NULL COMMENT '新状态',
    operator_id BIGINT       NULL COMMENT '操作人（sys_user.id）',
    reason      VARCHAR(500) NULL COMMENT '原因/备注',
    created_at  DATETIME(3)  NOT NULL COMMENT '发生时间(UTC)',
    PRIMARY KEY (id),
    KEY idx_contract_status_log_contract_time (contract_id, created_at),
    KEY idx_contract_status_log_to_status (to_status, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='合同状态变更日志';

CREATE TABLE contract_template (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    code       VARCHAR(40) NOT NULL COMMENT '模板编码',
    name       VARCHAR(100) NOT NULL COMMENT '模板名称',
    version    VARCHAR(32) NOT NULL COMMENT '版本',
    content    MEDIUMTEXT  NOT NULL COMMENT '条款正文（{占位符} 在 PDF 生成时替换）',
    active     TINYINT(1)  NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间(UTC)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_contract_template_code_version (code, version),
    KEY idx_contract_template_active (active)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='合同模板（版本化）';

CREATE TABLE transaction_process (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    contract_id BIGINT       NOT NULL COMMENT '合同ID',
    node        VARCHAR(20)  NOT NULL COMMENT 'DEPOSIT 定金 / SIGN 签约 / HANDOVER 交房',
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DONE/CANCELLED',
    done_at     DATETIME(3)  NULL COMMENT '完成时间(UTC)',
    remark      VARCHAR(500) NULL COMMENT '备注',
    created_at  DATETIME(3)  NOT NULL COMMENT '创建时间(UTC)',
    updated_at  DATETIME(3)  NOT NULL COMMENT '更新时间(UTC)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_transaction_contract_node (contract_id, node),
    KEY idx_transaction_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='合同交易节点';

CREATE TABLE transaction_status_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    contract_id BIGINT       NOT NULL COMMENT '合同ID',
    node        VARCHAR(20)  NOT NULL COMMENT '交易节点',
    from_status VARCHAR(20)  NULL COMMENT '原状态',
    to_status   VARCHAR(20)  NOT NULL COMMENT '新状态',
    operator_id BIGINT       NULL COMMENT '操作人（sys_user.id）',
    reason      VARCHAR(500) NULL COMMENT '原因/备注',
    created_at  DATETIME(3)  NOT NULL COMMENT '发生时间(UTC)',
    PRIMARY KEY (id),
    KEY idx_transaction_log_contract_time (contract_id, created_at),
    KEY idx_transaction_log_node_time (contract_id, node, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='交易节点状态日志';

-- 版本化合同模板种子：{lessorName}/{lesseeName}/{address}/{amount}/{startDate}/{endDate} 由快照替换
INSERT INTO contract_template (code, name, version, content, active, created_at)
VALUES ('STANDARD_LEASE', '房屋租赁合同（标准版）', 'v1',
        '第一条 租赁房屋\n出租方将坐落于{address}的房屋出租给承租方居住使用，承租方已实地查看并确认房屋现状。\n\n第二条 租赁期限\n租赁期限自{startDate}起至{endDate}止。租赁期满，承租方享有同等条件优先续租权，续租需另行签订书面合同。\n\n第三条 租金与押金\n月租金为人民币{amount}，付款方式为{paymentCycle}。承租方应在每期开始前五日内支付当期租金。押金为人民币{deposit}，租赁期满且无违约、无欠费、房屋及附属设施无损坏的，出租方应在交房验收后七日内无息退还。\n\n第四条 费用承担\n租赁期间的水费、电费、燃气费、网络费及物业管理费由承租方承担；房屋本体维修基金、房产税由出租方承担。\n\n第五条 房屋使用与维修\n承租方应合理使用房屋及设施，不得擅自改变房屋结构与用途，不得转租、转借。因承租方使用不当造成损坏的，由承租方负责修复或赔偿；自然损耗由出租方负责维修。\n\n第六条 合同解除\n一方需提前解除合同的，应提前三十日书面通知对方；承租方逾期支付租金超过十五日的，出租方有权解除合同并追究违约责任。\n\n第七条 争议解决\n因本合同发生争议，双方应协商解决；协商不成的，可向房屋所在地人民法院提起诉讼。\n\n第八条 其他约定\n本合同自双方签字（或盖章）之日起生效，一式两份，双方各执一份，具有同等法律效力。',
        1, UTC_TIMESTAMP(3));
