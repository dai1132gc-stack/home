package com.example.realestate.modules.ai.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AI 工具：敏感串清理、JSON 提取、向量编解码、分块")
class AiUtilsTest {

    @Test
    @DisplayName("清理手机号/身份证号/邮箱")
    void sanitize() {
        String out = SensitiveTextSanitizer.sanitize("电话13812345678，邮箱a@b.com，身份证11010119900307777X");
        assertThat(out).doesNotContain("13812345678").doesNotContain("a@b.com").doesNotContain("11010119900307777X");
        assertThat(out).contains("[手机号]").contains("[邮箱]").contains("[证件号]");
        assertThat(SensitiveTextSanitizer.sanitize(null)).isNull();
    }

    @Test
    @DisplayName("剥离 ```json 围栏与前后说明")
    void extractJson() {
        assertThat(ModelJsonExtractor.extractJsonObject("```json\n{\"a\":1}\n```")).isEqualTo("{\"a\":1}");
        assertThat(ModelJsonExtractor.extractJsonObject("说明文字 {\"a\":1} 尾部")).isEqualTo("{\"a\":1}");
        assertThat(ModelJsonExtractor.extractJsonObject("{\"a\":1}")).isEqualTo("{\"a\":1}");
        assertThat(ModelJsonExtractor.extractJsonObject("无JSON")).isNull();
        assertThat(ModelJsonExtractor.extractJsonObject(null)).isNull();
    }

    @Test
    @DisplayName("embedding 编解码往返一致")
    void codecRoundTrip() {
        float[] v = {0.1f, -0.2f, 3.25f};
        float[] back = EmbeddingCodec.decode(EmbeddingCodec.encode(v));
        assertThat(back).isEqualTo(v);
        assertThat(EmbeddingCodec.decode("not-json")).isNull();
        assertThat(EmbeddingCodec.decode(null)).isNull();
    }

    @Test
    @DisplayName("余弦相似度与异常输入")
    void cosine() {
        assertThat(EmbeddingCodec.cosine(new float[]{1, 0}, new float[]{1, 0})).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(EmbeddingCodec.cosine(new float[]{1, 0}, new float[]{0, 1})).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(EmbeddingCodec.cosine(new float[]{1}, new float[]{1, 2})).isEqualTo(-1);
        assertThat(EmbeddingCodec.cosine(new float[]{0, 0}, new float[]{1, 0})).isEqualTo(-1);
    }

    @Test
    @DisplayName("分块：空文本、短文本单块、长文本多块且重叠 80 字")
    void chunking() {
        assertThat(PolicyChunker.chunk(null)).isEmpty();
        assertThat(PolicyChunker.chunk("  ")).isEmpty();
        List<String> shortChunks = PolicyChunker.chunk("第一条 内容。");
        assertThat(shortChunks).hasSize(1);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("第").append(i).append("条规定内容如下。");
        }
        List<String> chunks = PolicyChunker.chunk(sb.toString());
        assertThat(chunks.size()).isGreaterThan(1);
        for (String c : chunks) {
            assertThat(c.length()).isLessThanOrEqualTo(PolicyChunker.MAX);
        }
        // 重叠校验：后一块的开头应包含前一块结尾的部分文字
        String tail = chunks.get(0).substring(chunks.get(0).length() - 20);
        assertThat(chunks.get(1)).contains(tail.substring(0, 10));
    }
}
