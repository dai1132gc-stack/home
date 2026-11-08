-- ============================================================
-- V4__repair_content_calculator.sql — W4 租后服务与工具
-- 范围：报修工单/图片/状态日志、内容（教程/网签指引/公告）、
--      政策文档与 W6 预留分块表、贷款利率与税费规则配置。
-- 约定：
--   1. 时间统一 DATETIME(3)，由应用层写 UTC Instant；政策日期用 DATE；
--   2. repair_ticket / guide_article / policy_document 含 version 乐观锁 + deleted 逻辑删除；
--   3. 金额 DECIMAL(12,2)，利率/税率 DECIMAL(6,4)；
--   4. 状态为受控枚举，非法迁移由 Service 状态机拒绝并返回 409；
--   5. customer_follow 已由 V2__house_owner.sql 建表，本迁移不重建、不修改其结构；
--   6. 种子数据均为“示例/非实时政策”，仅用于本地演示，不代表官方口径。
-- ============================================================

CREATE TABLE repair_ticket (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    ticket_no           VARCHAR(40)  NOT NULL COMMENT '报修单号（唯一）',
    house_id            BIGINT       NOT NULL COMMENT '房源ID',
    contract_id         BIGINT       NULL COMMENT '关联合同ID（可空）',
    customer_id         BIGINT       NOT NULL COMMENT '提交客户用户ID',
    category            VARCHAR(20)  NOT NULL COMMENT 'PLUMBING/ELECTRICAL/APPLIANCE/STRUCTURE/OTHER',
    urgency             VARCHAR(20)  NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/URGENT',
    title               VARCHAR(120) NOT NULL COMMENT '报修标题',
    description         TEXT         NOT NULL COMMENT '问题描述',
    contact_phone       VARCHAR(20)  NOT NULL COMMENT '联系电话（非 ADMIN 输出脱敏）',
    preferred_visit_time DATETIME(3) NULL COMMENT '期望上门时间(UTC)',
    status              VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED'
        COMMENT 'SUBMITTED/ACCEPTED/PROCESSING/RESOLVED/CLOSED/CANCELLED/REOPENED',
    version             INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted             TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 已删除',
    created_at          DATETIME(3)  NOT NULL COMMENT '创建时间(UTC)',
    updated_at          DATETIME(3)  NOT NULL COMMENT '更新时间(UTC)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_repair_ticket_no (ticket_no),
    KEY idx_repair_customer_status_created (customer_id, status, created_at),
    KEY idx_repair_status_created (status, created_at),
    KEY idx_repair_house (house_id),
    KEY idx_repair_contract (contract_id),
    KEY idx_repair_deleted_created (deleted, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='报修工单';

CREATE TABLE repair_media (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    ticket_id  BIGINT       NOT NULL COMMENT '报修工单ID',
    url        VARCHAR(500) NOT NULL COMMENT '私有存储相对 key（不暴露公开 URL）',
    media_type VARCHAR(10)  NOT NULL DEFAULT 'IMAGE' COMMENT 'IMAGE',
    sort       INT          NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
    created_at DATETIME(3)  NOT NULL COMMENT '创建时间(UTC)',
    PRIMARY KEY (id),
    KEY idx_repair_media_ticket_sort (ticket_id, sort, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='报修图片（私有存储）';

CREATE TABLE repair_status_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    ticket_id   BIGINT       NOT NULL COMMENT '报修工单ID',
    from_status VARCHAR(20)  NULL COMMENT '原状态（创建时为空）',
    to_status   VARCHAR(20)  NOT NULL COMMENT '新状态',
    operator_id BIGINT       NULL COMMENT '操作人（sys_user.id）',
    reason      VARCHAR(500) NULL COMMENT '原因/备注',
    created_at  DATETIME(3)  NOT NULL COMMENT '发生时间(UTC)',
    PRIMARY KEY (id),
    KEY idx_repair_log_ticket_time (ticket_id, created_at),
    KEY idx_repair_log_status_time (to_status, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='报修状态变更日志';

CREATE TABLE guide_article (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    title      VARCHAR(200) NOT NULL COMMENT '标题',
    category   VARCHAR(20)  NOT NULL COMMENT 'GUIDE/NETWORK_GUIDE/NOTICE',
    summary    VARCHAR(500) NULL COMMENT '摘要',
    content    MEDIUMTEXT   NOT NULL COMMENT '纯文本正文（按换行渲染，不存富文本）',
    status     VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/OFFLINE',
    sort       INT          NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
    version    INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 已删除',
    created_at DATETIME(3)  NOT NULL COMMENT '创建时间(UTC)',
    updated_at DATETIME(3)  NOT NULL COMMENT '更新时间(UTC)',
    PRIMARY KEY (id),
    KEY idx_guide_status_category_sort (status, category, sort),
    KEY idx_guide_deleted_updated (deleted, updated_at),
    KEY idx_guide_category_sort (category, sort)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='教程/网签指引/公告';

CREATE TABLE policy_document (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    title          VARCHAR(200) NOT NULL COMMENT '标题',
    doc_no         VARCHAR(80)  NULL COMMENT '文号',
    doc_type       VARCHAR(20)  NOT NULL DEFAULT 'POLICY' COMMENT 'POLICY/NOTICE',
    source         VARCHAR(200) NULL COMMENT '来源（必须标注示例/非实时政策）',
    publish_date   DATE         NULL COMMENT '发布日期',
    effective_date DATE         NULL COMMENT '生效日期',
    content        MEDIUMTEXT   NOT NULL COMMENT '纯文本正文',
    attachment_key VARCHAR(500) NULL COMMENT '附件私有存储相对 key',
    status         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/OFFLINE',
    version        INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    deleted        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 1 已删除',
    created_at     DATETIME(3)  NOT NULL COMMENT '创建时间(UTC)',
    updated_at     DATETIME(3)  NOT NULL COMMENT '更新时间(UTC)',
    PRIMARY KEY (id),
    KEY idx_policy_status_type_publish (status, doc_type, publish_date),
    KEY idx_policy_deleted_updated (deleted, updated_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='政策文档';

-- W6 政策问答预留：本阶段仅建表，不写入、不做向量检索
CREATE TABLE policy_document_chunk (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    policy_id     BIGINT       NOT NULL COMMENT '政策文档ID',
    chunk_index   INT          NOT NULL COMMENT '分块序号（从 0 开始）',
    content       MEDIUMTEXT   NOT NULL COMMENT '分块文本',
    embedding_ref VARCHAR(500) NULL COMMENT '向量/嵌入引用（W6 使用）',
    created_at    DATETIME(3)  NOT NULL COMMENT '创建时间(UTC)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_policy_chunk_index (policy_id, chunk_index)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='政策文档分块（W6 预留，仅建表）';

CREATE TABLE loan_rate_config (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    loan_type      VARCHAR(20)  NOT NULL COMMENT 'COMMERCIAL/FUND',
    term_years     INT          NOT NULL COMMENT '贷款年限（1-30）',
    rate           DECIMAL(6,4) NOT NULL COMMENT '年利率（如 0.0385 表示 3.85%）',
    effective_date DATE         NOT NULL COMMENT '生效日期',
    source         VARCHAR(200) NOT NULL COMMENT '费率来源（示例数据必须标注非实时）',
    active         TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at     DATETIME(3)  NOT NULL COMMENT '创建时间(UTC)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_loan_rate_type_term_effective (loan_type, term_years, effective_date),
    KEY idx_loan_rate_lookup (loan_type, term_years, active, effective_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='贷款利率配置（版本化）';

CREATE TABLE tax_rule_config (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    city           VARCHAR(60)   NOT NULL COMMENT '城市（如 上海）',
    rule_type      VARCHAR(20)   NOT NULL COMMENT 'DEED_TAX/VAT/INCOME_TAX',
    min_amount     DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '金额区间下限（含）',
    max_amount     DECIMAL(12,2) NULL COMMENT '金额区间上限（不含），NULL 表示无上限',
    rate           DECIMAL(6,4)  NOT NULL COMMENT '税率',
    quick_deduction DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '速算扣除数',
    effective_date DATE          NOT NULL COMMENT '生效日期',
    source         VARCHAR(200)  NOT NULL COMMENT '规则来源（示例数据必须标注非实时）',
    active         TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at     DATETIME(3)   NOT NULL COMMENT '创建时间(UTC)',
    PRIMARY KEY (id),
    KEY idx_tax_city_type_effective (city, rule_type, effective_date),
    KEY idx_tax_lookup (city, rule_type, active, min_amount, max_amount)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='交易税费规则配置（版本化）';

-- ------------------------------------------------------------
-- 示例内容种子（全部标注“示例/非实时”，不得作为官方口径）
-- ------------------------------------------------------------
INSERT INTO guide_article (title, category, summary, content, status, sort, version, deleted, created_at, updated_at)
VALUES
('租房入住前检查清单（示例）', 'GUIDE',
 '示例内容：看房与交房时建议核对的项目，非官方标准。',
 '一、房屋本体\n1. 墙面、地面、天花板有无渗水、开裂、发霉。\n2. 门窗开合是否顺畅，锁具是否完好。\n3. 水压、排水是否正常，卫生间有无异味。\n\n二、家电家具\n1. 空调、热水器、冰箱、洗衣机逐项通电测试。\n2. 核对家具数量与合同附件是否一致。\n\n三、费用与交接\n1. 记录水、电、燃气表底数并拍照留存。\n2. 确认物业费、网络费的承担方式。\n\n说明：本清单为平台示例内容，不构成法律意见，具体以双方合同约定为准。',
 'PUBLISHED', 10, 0, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('房屋租赁网签办理指引（示例）', 'NETWORK_GUIDE',
 '示例内容：网签备案的一般流程与材料清单，具体以当地主管部门要求为准。',
 '一、办理前准备\n1. 出租方与承租方身份证明。\n2. 房屋权属证明或合法出租证明。\n3. 已签署的租赁合同。\n\n二、办理流程\n1. 登录当地住房租赁监管服务平台。\n2. 录入房屋、双方及合同信息。\n3. 双方在线确认并提交备案。\n4. 下载备案结果，按需办理居住登记等事项。\n\n三、常见问题\n1. 房屋信息与权属证明不一致时，需先更正。\n2. 合同提前解除的，应及时办理备案注销或变更。\n\n说明：本指引为平台示例内容，非官方口径，办理要求以当地主管部门最新规定为准。',
 'PUBLISHED', 20, 0, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('平台服务时间调整公告（示例）', 'NOTICE',
 '示例公告：仅用于本地演示内容发布流程。',
 '为便于系统维护，平台将于每周日 02:00-04:00 进行例行维护。\n维护期间，房源浏览、预约提交、合同查看等功能可能短时不可用。\n\n如遇紧急报修，请在维护结束后通过“我的报修”提交，或电话联系门店值班人员。\n\n说明：本公告为示例数据，不代表真实服务安排。',
 'PUBLISHED', 30, 0, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT INTO policy_document (title, doc_no, doc_type, source, publish_date, effective_date, content, attachment_key, status, version, deleted, created_at, updated_at)
VALUES
('住房租赁税费政策示例说明（非实时政策）', '示例-2026-001', 'POLICY',
 '示例数据（非官方口径，非实时政策）', '2026-01-01', '2026-01-01',
 '本文件为系统演示用示例政策，仅用于展示政策发布、检索与附件下载流程。\n\n一、适用范围\n示例城市范围内的住房租赁交易，具体适用以当地主管部门规定为准。\n\n二、税费说明\n示例：契税、增值税、个人所得税按房屋交易价格分档计算，税率与速算扣除数见测算工具配置。\n\n三、风险提示\n本示例不构成税务或法律意见，实际交易请咨询主管税务机关或专业人士。',
 NULL, 'PUBLISHED', 0, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
('关于规范租赁合同备案的示例通知（非实时政策）', '示例-2026-002', 'NOTICE',
 '示例数据（非官方口径，非实时政策）', '2026-02-01', '2026-02-15',
 '本通知为系统演示用示例内容。\n\n一、鼓励租赁双方在签约后及时办理合同备案。\n二、备案信息应与合同约定保持一致。\n三、合同解除或变更的，应及时办理相应手续。\n\n说明：本通知为示例数据，不代表任何主管部门的真实要求。',
 NULL, 'PUBLISHED', 0, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

-- 示例费率：仅用于演示测算流程，非实时银行/公积金政策
INSERT INTO loan_rate_config (loan_type, term_years, rate, effective_date, source, active, created_at)
VALUES
('COMMERCIAL', 5, 0.0345, '2026-01-01', '示例费率（非实时，仅演示）', 1, UTC_TIMESTAMP(3)),
('COMMERCIAL', 10, 0.0365, '2026-01-01', '示例费率（非实时，仅演示）', 1, UTC_TIMESTAMP(3)),
('COMMERCIAL', 20, 0.0375, '2026-01-01', '示例费率（非实时，仅演示）', 1, UTC_TIMESTAMP(3)),
('COMMERCIAL', 30, 0.0385, '2026-01-01', '示例费率（非实时，仅演示）', 1, UTC_TIMESTAMP(3)),
('FUND', 5, 0.0235, '2026-01-01', '示例费率（非实时，仅演示）', 1, UTC_TIMESTAMP(3)),
('FUND', 10, 0.0260, '2026-01-01', '示例费率（非实时，仅演示）', 1, UTC_TIMESTAMP(3)),
('FUND', 20, 0.0285, '2026-01-01', '示例费率（非实时，仅演示）', 1, UTC_TIMESTAMP(3)),
('FUND', 30, 0.0310, '2026-01-01', '示例费率（非实时，仅演示）', 1, UTC_TIMESTAMP(3));

-- 示例税费规则（上海，仅演示分档计算；非实时政策）
INSERT INTO tax_rule_config (city, rule_type, min_amount, max_amount, rate, quick_deduction, effective_date, source, active, created_at)
VALUES
('上海', 'DEED_TAX', 0.00, 900000.00, 0.0100, 0.00, '2026-01-01', '示例规则（非实时政策）', 1, UTC_TIMESTAMP(3)),
('上海', 'DEED_TAX', 900000.00, 5000000.00, 0.0150, 0.00, '2026-01-01', '示例规则（非实时政策）', 1, UTC_TIMESTAMP(3)),
('上海', 'DEED_TAX', 5000000.00, NULL, 0.0300, 0.00, '2026-01-01', '示例规则（非实时政策）', 1, UTC_TIMESTAMP(3)),
('上海', 'VAT', 0.00, 2000000.00, 0.0000, 0.00, '2026-01-01', '示例规则（非实时政策）', 1, UTC_TIMESTAMP(3)),
('上海', 'VAT', 2000000.00, NULL, 0.0500, 0.00, '2026-01-01', '示例规则（非实时政策）', 1, UTC_TIMESTAMP(3)),
('上海', 'INCOME_TAX', 0.00, 2000000.00, 0.0100, 0.00, '2026-01-01', '示例规则（非实时政策）', 1, UTC_TIMESTAMP(3)),
('上海', 'INCOME_TAX', 2000000.00, NULL, 0.0200, 0.00, '2026-01-01', '示例规则（非实时政策）', 1, UTC_TIMESTAMP(3));
