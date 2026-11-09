package com.example.realestate.modules.ai.model.vo;

import java.util.List;

/** 政策问答响应（W6）：citations 只来自本次召回片段；无任何有效引用按 insufficient 处理。 */
public record PolicyAnswerResponse(
        String answer,
        List<Citation> citations,
        boolean insufficient,
        boolean degraded,
        String model,
        long latencyMs,
        String disclaimer
) {

    public record Citation(
            Long policyId,
            String title,
            String docNo,
            String source,
            int chunkIndex,
            String snippet
    ) {
    }
}
