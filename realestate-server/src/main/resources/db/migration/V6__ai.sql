-- W6 智能增强：政策向量分块扩展 + AI 调用统计
-- 仅增列/建新表，不修改 V1~V5 既有结构；policy_document_chunk 保留 embedding_ref 不删。

ALTER TABLE policy_document_chunk
    ADD COLUMN embedding       MEDIUMTEXT   NULL COMMENT '向量(JSON数组，W6)' AFTER embedding_ref,
    ADD COLUMN embedding_model VARCHAR(80)  NULL COMMENT '生成向量的模型名' AFTER embedding,
    ADD COLUMN updated_at      DATETIME(3)  NULL COMMENT '更新时间(UTC)' AFTER embedding_model;

CREATE TABLE ai_interaction_log (
    id                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    scene             VARCHAR(20) NOT NULL COMMENT '场景: SMART_SEARCH/POLICY_QA/INDEX',
    user_id           BIGINT      NULL COMMENT '用户ID(可空，匿名)',
    client_type       VARCHAR(10) NOT NULL DEFAULT 'ANON' COMMENT 'WEB/MINI/ANON',
    status            VARCHAR(10) NOT NULL COMMENT 'SUCCESS/DEGRADED/FAILED',
    provider          VARCHAR(40) NULL COMMENT '提供方标识(不含base-url)',
    model             VARCHAR(80) NULL COMMENT '模型名',
    latency_ms        INT         NULL COMMENT '耗时(毫秒)',
    prompt_tokens     INT         NULL COMMENT '输入token估算',
    completion_tokens INT         NULL COMMENT '输出token估算',
    degraded_reason   VARCHAR(40) NULL COMMENT '降级原因',
    trace_id          VARCHAR(64) NULL COMMENT '请求追踪ID',
    created_at        DATETIME(3) NOT NULL COMMENT '创建时间(UTC)',
    PRIMARY KEY (id),
    KEY idx_ai_log_scene_created (scene, created_at),
    KEY idx_ai_log_status_created (status, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='AI 调用统计（不含原始内容）';
