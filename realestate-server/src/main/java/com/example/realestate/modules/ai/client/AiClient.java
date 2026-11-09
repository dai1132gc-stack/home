package com.example.realestate.modules.ai.client;

import java.util.List;

/**
 * AI 网关接口：Service 不直接拼 HTTP，便于 stub/单测。
 * 所有实现必须：超时有限、不重试、Key/prompt/answer 不写日志。
 */
public interface AiClient {

    /** 是否已配置可用（enabled 且凭据齐全） */
    boolean available();

    String chatModel();

    String embeddingModel();

    /** 提供方标识（仅用于统计，不含 base-url/Key） */
    String provider();

    /**
     * 对话补全（OpenAI 兼容 /chat/completions）。
     * 超时/429/5xx 抛出 AiUpstreamException；调用方负责降级或映射 502。
     */
    ChatCompletion chat(String systemPrompt, String userPrompt, int maxOutputTokens);

    /** 批量向量化（OpenAI 兼容 /embeddings） */
    List<float[]> embed(List<String> inputs);

    record ChatCompletion(String content, Integer promptTokens, Integer completionTokens) {
    }
}
