package com.example.realestate.modules.ai.model.vo;

/** AI 状态（公开）：不含 Key、base-url、内部错误细节。 */
public record AiStatusResponse(
        boolean enabled,
        String chatModel,
        String embeddingModel,
        long indexedChunks,
        String disclaimer,
        String provider
) {
}
