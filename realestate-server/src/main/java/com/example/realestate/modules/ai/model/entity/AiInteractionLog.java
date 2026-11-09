package com.example.realestate.modules.ai.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/** AI 调用统计（W6）：只记录指标，不记录 query/answer/prompt/片段原文。 */
@Data
@TableName("ai_interaction_log")
public class AiInteractionLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** SMART_SEARCH/POLICY_QA/INDEX */
    private String scene;
    private Long userId;
    /** WEB/MINI/ANON */
    private String clientType;
    /** SUCCESS/DEGRADED/FAILED */
    private String status;
    private String provider;
    private String model;
    private Integer latencyMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private String degradedReason;
    private String traceId;
    private Instant createdAt;
}
