package com.example.realestate.modules.ai.controller;

import com.example.realestate.common.api.ApiResponse;
import com.example.realestate.modules.ai.service.AiMetricsService;
import com.example.realestate.modules.ai.service.PolicyIndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * AI 管理与统计（W6，ADMIN）：
 * - /admin/ai/metrics 调用统计聚合；/admin/ai/reindex 全量重建；
 * - /admin/policies/{id}/reindex 单条重建。均认证 + @PreAuthorize，白名单不含这些路径。
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "智能服务管理", description = "AI 调用统计与政策索引重建（ADMIN）")
public class AdminAiController {

    private final AiMetricsService metricsService;
    private final PolicyIndexService indexService;

    public AdminAiController(AiMetricsService metricsService, PolicyIndexService indexService) {
        this.metricsService = metricsService;
        this.indexService = indexService;
    }

    @GetMapping("/ai/metrics")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "AI 调用统计（默认最近 7 天，最大 90 天；不含原始内容）")
    public ApiResponse<AiMetricsService.AiMetrics> metrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponse.ok(metricsService.metrics(from, to));
    }

    @PostMapping("/ai/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "全量重建政策索引（幂等）")
    public ApiResponse<Map<String, Long>> reindexAll() {
        long[] result = indexService.reindexAll();
        return ApiResponse.ok(Map.of(
                "policies", result[0],
                "chunks", result[1],
                "failed", result[2]));
    }

    @PostMapping("/policies/{id}/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "重建单条政策索引（幂等）")
    public ApiResponse<Map<String, Long>> reindexOne(@PathVariable Long id) {
        return ApiResponse.ok(Map.of("chunks", (long) indexService.reindex(id)));
    }
}
