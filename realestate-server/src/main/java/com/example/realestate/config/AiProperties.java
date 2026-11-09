package com.example.realestate.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

/**
 * AI 智能服务配置（W6，独立于 AppProperties，避免 record 构造绑定问题 CFG-01）。
 * enabled=true 时 base-url/api-key/chat-model/embedding-model 必须非空，启动 fail-fast；
 * enabled=false 时允许为空，所有 AI 能力降级为确定性行为。
 * API Key 只允许出现在后端配置与 .env，禁止写入日志/响应/前端。
 */
@Validated
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String chatModel,
        String embeddingModel,
        @Min(10000) @Max(15000) int timeoutMs,
        @Min(1) @Max(200) int maxInputChars,
        @Min(1) @Max(4096) int maxOutputTokens,
        @Min(0) long dailyTokenLimit,
        @Min(1) @Max(120) int rateLimitPerMinute,
        @Min(1) @Max(8) int topK,
        double similarityThreshold,
        @Min(1) @Max(16) int concurrency
) {

    public AiProperties {
        if (enabled) {
            requireNonBlank(baseUrl, "base-url");
            requireNonBlank(apiKey, "api-key");
            requireNonBlank(chatModel, "chat-model");
            requireNonBlank(embeddingModel, "embedding-model");
            URI uri = URI.create(baseUrl);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("app.ai.base-url 仅支持 http/https: " + scheme);
            }
        }
        if (similarityThreshold < 0.0 || similarityThreshold > 1.0) {
            throw new IllegalArgumentException("app.ai.similarity-threshold 必须在 0~1 之间");
        }
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("app.ai." + name + " 在 enabled=true 时必须非空");
        }
    }

    /** 去掉末尾斜杠，供 RestClient baseUrl 使用 */
    public String normalizedBaseUrl() {
        if (baseUrl == null) {
            return null;
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
