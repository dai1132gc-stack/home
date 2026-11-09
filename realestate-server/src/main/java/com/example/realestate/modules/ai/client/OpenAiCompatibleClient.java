package com.example.realestate.modules.ai.client;

import com.example.realestate.config.AiProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容网关实现（Spring RestClient，不新增 HTTP/SDK 依赖）。
 * 连接/读取超时由 app.ai.timeout-ms 控制（10~15 秒），不重试；
 * 429/5xx/超时统一抛 AiUpstreamException，由调用方决定降级或 502。
 * API Key 只出现在 Authorization 头，禁止写日志。
 */
@Component
public class OpenAiCompatibleClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);
    private static final String PROVIDER = "openai-compatible";

    private final AiProperties properties;
    private final RestClient restClient; // enabled=false 时为 null

    public OpenAiCompatibleClient(AiProperties properties) {
        this.properties = properties;
        if (properties.enabled()) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofMillis(properties.timeoutMs()));
            factory.setReadTimeout(Duration.ofMillis(properties.timeoutMs()));
            this.restClient = RestClient.builder()
                    .requestFactory(factory)
                    .baseUrl(properties.normalizedBaseUrl())
                    .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                    .build();
        } else {
            this.restClient = null;
        }
    }

    @Override
    public boolean available() {
        return properties.enabled() && restClient != null;
    }

    @Override
    public String chatModel() {
        return properties.chatModel();
    }

    @Override
    public String embeddingModel() {
        return properties.embeddingModel();
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public ChatCompletion chat(String systemPrompt, String userPrompt, int maxOutputTokens) {
        ensureAvailable();
        Map<String, Object> body = Map.of(
                "model", properties.chatModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "max_tokens", maxOutputTokens,
                "temperature", 0
        );
        ChatResponse response = post("/chat/completions", body, ChatResponse.class);
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().get(0).message() == null
                || response.choices().get(0).message().content() == null) {
            log.warn("AI chat 返回空响应");
            throw new AiUpstreamException(AiUpstreamException.Kind.SERVER_ERROR, "AI chat 空响应");
        }
        Integer promptTokens = response.usage() == null ? null : response.usage().promptTokens();
        Integer completionTokens = response.usage() == null ? null : response.usage().completionTokens();
        return new ChatCompletion(response.choices().get(0).message().content(), promptTokens, completionTokens);
    }

    @Override
    public List<float[]> embed(List<String> inputs) {
        ensureAvailable();
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        Map<String, Object> body = Map.of(
                "model", properties.embeddingModel(),
                "input", inputs
        );
        EmbeddingResponse response = post("/embeddings", body, EmbeddingResponse.class);
        if (response == null || response.data() == null || response.data().size() != inputs.size()) {
            log.warn("AI embedding 返回数量不匹配");
            throw new AiUpstreamException(AiUpstreamException.Kind.SERVER_ERROR, "AI embedding 响应不完整");
        }
        return response.data().stream()
                .map(item -> {
                    List<Double> vec = item.embedding();
                    if (vec == null || vec.isEmpty()) {
                        throw new AiUpstreamException(AiUpstreamException.Kind.SERVER_ERROR, "AI embedding 空向量");
                    }
                    float[] floats = new float[vec.size()];
                    for (int i = 0; i < vec.size(); i++) {
                        floats[i] = vec.get(i).floatValue();
                    }
                    return floats;
                })
                .toList();
    }

    private void ensureAvailable() {
        if (!available()) {
            throw new IllegalStateException("AI 未配置");
        }
    }

    private <T> T post(String path, Object body, Class<T> type) {
        try {
            return restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(type);
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.value() == 429) {
                log.warn("AI 上游限流 path={}", path);
                throw new AiUpstreamException(AiUpstreamException.Kind.RATE_LIMITED, "AI 上游限流", e);
            }
            log.warn("AI 上游错误 path={} status={}", path, status.value());
            throw new AiUpstreamException(AiUpstreamException.Kind.SERVER_ERROR, "AI 上游错误 " + status.value(), e);
        } catch (RestClientException e) {
            if (isTimeout(e)) {
                log.warn("AI 上游超时 path={}", path);
                throw new AiUpstreamException(AiUpstreamException.Kind.TIMEOUT, "AI 上游超时", e);
            }
            log.warn("AI 调用失败 path={} type={}", path, e.getClass().getSimpleName());
            throw new AiUpstreamException(AiUpstreamException.Kind.UNKNOWN, "AI 调用失败", e);
        }
    }

    private static boolean isTimeout(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    // ---- OpenAI 兼容响应结构（仅解析所需字段） ----

    record ChatResponse(List<Choice> choices, Usage usage) {
        record Choice(Message message) {
        }
        record Message(String content) {
        }
        record Usage(@JsonProperty("prompt_tokens") Integer promptTokens,
                     @JsonProperty("completion_tokens") Integer completionTokens) {
        }
    }

    record EmbeddingResponse(List<EmbeddingItem> data) {
        record EmbeddingItem(List<Double> embedding) {
        }
    }
}
