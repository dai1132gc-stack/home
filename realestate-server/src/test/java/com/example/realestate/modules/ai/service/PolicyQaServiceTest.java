package com.example.realestate.modules.ai.service;

import com.example.realestate.common.exception.BusinessException;
import com.example.realestate.config.AiProperties;
import com.example.realestate.modules.ai.client.AiClient;
import com.example.realestate.modules.ai.client.AiUpstreamException;
import com.example.realestate.modules.ai.model.dto.PolicyAnswerRequest;
import com.example.realestate.modules.ai.model.vo.PolicyAnswerResponse;
import com.example.realestate.modules.ai.util.EmbeddingCodec;
import com.example.realestate.modules.content.mapper.PolicyDocumentChunkMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@DisplayName("政策问答：召回阈值、拒答、引用越界与错误口径")
class PolicyQaServiceTest {

    @Mock
    private AiClient aiClient;
    @Mock
    private AiAccessGuard guard;
    @Mock
    private AiInteractionLogger interactionLogger;
    @Mock
    private PolicyIndexService indexService;

    private final AiProperties properties = new AiProperties(true, "http://x/v1", "k", "chat", "embed",
            12000, 200, 800, 100000, 20, 4, 0.30, 4);
    private PolicyQaService service;

    @BeforeEach
    void setUp() {
        service = new PolicyQaService(aiClient, guard, interactionLogger, indexService, properties);
        lenient().when(guard.tryAcquire()).thenReturn(true);
        lenient().when(aiClient.available()).thenReturn(true);
        lenient().when(aiClient.chatModel()).thenReturn("chat");
        lenient().when(aiClient.embeddingModel()).thenReturn("embed");
        lenient().when(aiClient.provider()).thenReturn("stub");
        lenient().when(aiClient.embed(any())).thenReturn(List.of(new float[]{1, 0}));
    }

    private PolicyAnswerRequest req() {
        return new PolicyAnswerRequest("购房契税是多少？");
    }

    private PolicyDocumentChunkMapper.IndexedChunkRow row(long id, int index, float[] vec) {
        return new PolicyDocumentChunkMapper.IndexedChunkRow(id, 10L, index, "政策正文片段" + index,
                EmbeddingCodec.encode(vec), "embed", "契税政策", "DOC-1", "住建局");
    }

    @Test
    @DisplayName("AI 未配置：503 AI_50301")
    void notConfigured() {
        when(aiClient.available()).thenReturn(false);
        assertThatThrownBy(() -> service.answer(req(), "1.1.1.1", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置");
    }

    @Test
    @DisplayName("无召回片段：insufficient=true 且不调用 chat")
    void insufficientWithoutChat() {
        when(indexService.indexedChunks()).thenReturn(List.of());
        PolicyAnswerResponse resp = service.answer(req(), "1.1.1.1", null, "WEB");
        assertThat(resp.insufficient()).isTrue();
        assertThat(resp.citations()).isEmpty();
        verify(aiClient, never()).chat(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("相似度低于阈值：insufficient=true")
    void belowThreshold() {
        when(indexService.indexedChunks()).thenReturn(List.of(row(1, 0, new float[]{0, 1}))); // 与问题正交
        PolicyAnswerResponse resp = service.answer(req(), "1.1.1.1", null, null);
        assertThat(resp.insufficient()).isTrue();
        verify(aiClient, never()).chat(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("引用越界剔除：全部越界按 insufficient")
    void citationOutOfBounds() {
        when(indexService.indexedChunks()).thenReturn(List.of(row(1, 0, new float[]{1, 0})));
        when(aiClient.chat(anyString(), anyString(), anyInt()))
                .thenReturn(new AiClient.ChatCompletion("{\"answer\":\"答案\",\"citations\":[5,-1]}", null, null));
        PolicyAnswerResponse resp = service.answer(req(), "1.1.1.1", null, null);
        assertThat(resp.insufficient()).isTrue();
        assertThat(resp.citations()).isEmpty();
    }

    @Test
    @DisplayName("正常作答：引用 1-based 映射 + snippet 截断 + disclaimer")
    void successWithCitations() {
        when(indexService.indexedChunks()).thenReturn(List.of(
                row(1, 0, new float[]{1, 0}), row(2, 1, new float[]{0.9f, 0.1f})));
        when(aiClient.chat(anyString(), anyString(), anyInt()))
                .thenReturn(new AiClient.ChatCompletion("{\"answer\":\"契税为1%\",\"citations\":[1,2,99]}", 50, 10));
        PolicyAnswerResponse resp = service.answer(req(), "1.1.1.1", 3L, "MINI");
        assertThat(resp.insufficient()).isFalse();
        assertThat(resp.answer()).isEqualTo("契税为1%");
        assertThat(resp.citations()).hasSize(2); // 99 越界剔除
        assertThat(resp.citations().get(0).title()).isEqualTo("契税政策");
        assertThat(resp.citations().get(0).policyId()).isEqualTo(10L);
        assertThat(resp.disclaimer()).contains("不构成法律意见");
        assertThat(resp.model()).isEqualTo("chat");
    }

    @Test
    @DisplayName("上游超时/5xx：502 AI_50201")
    void upstreamError() {
        when(indexService.indexedChunks()).thenReturn(List.of(row(1, 0, new float[]{1, 0})));
        when(aiClient.chat(anyString(), anyString(), anyInt()))
                .thenThrow(new AiUpstreamException(AiUpstreamException.Kind.TIMEOUT, "timeout"));
        assertThatThrownBy(() -> service.answer(req(), "1.1.1.1", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂时不可用");
    }
}
