package com.example.realestate.modules.ai.service;

import com.example.realestate.modules.ai.mapper.AiInteractionLogMapper;
import com.example.realestate.modules.ai.model.entity.AiInteractionLog;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 调用统计聚合（W6）：默认最近 7 天，最大 90 天；P95 在 Java 侧计算。
 * 只输出指标，不含任何原始内容。
 */
@Service
public class AiMetricsService {

    private static final long MAX_RANGE_DAYS = 90;

    private final AiInteractionLogMapper mapper;

    public AiMetricsService(AiInteractionLogMapper mapper) {
        this.mapper = mapper;
    }

    public AiMetrics metrics(Instant from, Instant to) {
        Instant effectiveTo = to == null ? Instant.now() : to;
        Instant effectiveFrom = from == null ? effectiveTo.minus(7, ChronoUnit.DAYS) : from;
        if (effectiveFrom.isBefore(effectiveTo.minus(MAX_RANGE_DAYS, ChronoUnit.DAYS))) {
            effectiveFrom = effectiveTo.minus(MAX_RANGE_DAYS, ChronoUnit.DAYS);
        }

        List<AiInteractionLogMapper.SceneStatusCount> counts =
                mapper.countBySceneAndStatus(effectiveFrom, effectiveTo);
        List<Integer> latencies = mapper.selectLatencies(effectiveFrom, effectiveTo);
        List<AiInteractionLog> recent = mapper.selectRecent(effectiveFrom, effectiveTo);

        long total = 0, success = 0, degraded = 0, failed = 0;
        long promptTokens = 0, completionTokens = 0;
        Map<String, SceneMetrics> byScene = new LinkedHashMap<>();
        for (AiInteractionLogMapper.SceneStatusCount c : counts) {
            total += c.cnt();
            promptTokens += c.promptTokens();
            completionTokens += c.completionTokens();
            SceneMetrics scene = byScene.computeIfAbsent(c.scene(), k -> new SceneMetrics(c.scene()));
            scene.total += c.cnt();
            switch (c.status()) {
                case AiInteractionLogger.STATUS_SUCCESS -> {
                    success += c.cnt();
                    scene.success += c.cnt();
                }
                case AiInteractionLogger.STATUS_DEGRADED -> {
                    degraded += c.cnt();
                    scene.degraded += c.cnt();
                }
                default -> {
                    failed += c.cnt();
                    scene.failed += c.cnt();
                }
            }
        }

        Double avg = null;
        Integer p95 = null;
        if (!latencies.isEmpty()) {
            avg = latencies.stream().mapToLong(Integer::longValue).average().orElse(0);
            List<Integer> sorted = new ArrayList<>(latencies);
            sorted.sort(Integer::compareTo);
            int index = (int) Math.ceil(0.95 * sorted.size()) - 1;
            p95 = sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
        }

        List<RecentItem> recentItems = recent.stream()
                .map(r -> new RecentItem(r.getScene(), r.getStatus(), r.getModel(), r.getLatencyMs(),
                        r.getDegradedReason(), r.getClientType(), r.getCreatedAt()))
                .toList();

        return new AiMetrics(total, success, degraded, failed,
                total == 0 ? null : (double) success / total,
                avg, p95, promptTokens, completionTokens,
                List.copyOf(byScene.values()), recentItems, effectiveFrom, effectiveTo);
    }

    public record AiMetrics(long total, long success, long degraded, long failed,
                            Double successRate, Double avgLatencyMs, Integer p95LatencyMs,
                            long promptTokens, long completionTokens,
                            List<SceneMetrics> byScene, List<RecentItem> recent,
                            Instant from, Instant to) {
    }

    public static class SceneMetrics {
        public final String scene;
        public long total;
        public long success;
        public long degraded;
        public long failed;

        SceneMetrics(String scene) {
            this.scene = scene;
        }
    }

    public record RecentItem(String scene, String status, String model, Integer latencyMs,
                             String degradedReason, String clientType, Instant createdAt) {
    }
}
