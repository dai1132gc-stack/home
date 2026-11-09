package com.example.realestate.modules.ai.service;

import com.example.realestate.modules.ai.mapper.AiInteractionLogMapper;
import com.example.realestate.modules.ai.model.entity.AiInteractionLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * AI 调用统计写入（W6）：只记录指标，不记录原始问题/答案/prompt；
 * 写日志失败只 warn，不影响主流程。
 */
@Component
public class AiInteractionLogger {

    private static final Logger log = LoggerFactory.getLogger(AiInteractionLogger.class);

    public static final String SCENE_SMART_SEARCH = "SMART_SEARCH";
    public static final String SCENE_POLICY_QA = "POLICY_QA";
    public static final String SCENE_INDEX = "INDEX";

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_DEGRADED = "DEGRADED";
    public static final String STATUS_FAILED = "FAILED";

    private final AiInteractionLogMapper mapper;

    public AiInteractionLogger(AiInteractionLogMapper mapper) {
        this.mapper = mapper;
    }

    public void record(String scene, Long userId, String clientType, String status,
                       String provider, String model, Integer latencyMs,
                       Integer promptTokens, Integer completionTokens, String degradedReason) {
        try {
            AiInteractionLog entity = new AiInteractionLog();
            entity.setScene(scene);
            entity.setUserId(userId);
            entity.setClientType(clientType == null || clientType.isBlank() ? "ANON" : clientType);
            entity.setStatus(status);
            entity.setProvider(provider);
            entity.setModel(model);
            entity.setLatencyMs(latencyMs);
            entity.setPromptTokens(promptTokens);
            entity.setCompletionTokens(completionTokens);
            entity.setDegradedReason(degradedReason);
            String traceId = MDC.get("traceId");
            entity.setTraceId(traceId == null || traceId.isBlank() ? null : traceId);
            entity.setCreatedAt(Instant.now());
            mapper.insert(entity);
        } catch (RuntimeException e) {
            log.warn("AI 调用统计写入失败（不影响主流程）: {}", e.getMessage());
        }
    }
}
