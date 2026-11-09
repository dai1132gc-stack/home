package com.example.realestate.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AI 配置：enabled=true 缺凭据 fail-fast，enabled=false 允许为空")
class AiPropertiesTest {

    @Test
    @DisplayName("enabled=true 缺 api-key 启动失败")
    void enabledMissingKeyFails() {
        assertThatThrownBy(() -> new AiProperties(true, "https://api.example.com/v1", null,
                "chat", "embed", 12000, 200, 800, 1000, 20, 4, 0.30, 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("api-key");
    }

    @Test
    @DisplayName("enabled=true base-url 非 http/https 启动失败")
    void enabledBadSchemeFails() {
        assertThatThrownBy(() -> new AiProperties(true, "ftp://x", "k",
                "chat", "embed", 12000, 200, 800, 1000, 20, 4, 0.30, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("enabled=false 允许为空")
    void disabledAllowsEmpty() {
        AiProperties props = new AiProperties(false, null, null, null, null,
                12000, 200, 800, 1000, 20, 4, 0.30, 4);
        assertThat(props.enabled()).isFalse();
    }

    @Test
    @DisplayName("阈值越界启动失败")
    void thresholdOutOfRangeFails() {
        assertThatThrownBy(() -> new AiProperties(false, null, null, null, null,
                12000, 200, 800, 1000, 20, 4, 1.5, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("base-url 末尾斜杠归一化")
    void normalizedBaseUrl() {
        AiProperties props = new AiProperties(true, "http://127.0.0.1:8090/v1/", "k",
                "chat", "embed", 12000, 200, 800, 1000, 20, 4, 0.30, 4);
        assertThat(props.normalizedBaseUrl()).isEqualTo("http://127.0.0.1:8090/v1");
    }
}
