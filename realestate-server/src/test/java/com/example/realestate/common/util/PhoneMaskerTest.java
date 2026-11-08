package com.example.realestate.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("手机号展示脱敏")
class PhoneMaskerTest {

    @Test
    @DisplayName("11 位手机号保留前 3 后 2")
    void masksMiddleDigits() {
        assertThat(PhoneMasker.mask("13800138000")).isEqualTo("138******00");
        assertThat(PhoneMasker.mask("13812345678")).isEqualTo("138******78");
    }

    @Test
    @DisplayName("空值与空白返回 null，过短返回 ***")
    void handlesEdgeCases() {
        assertThat(PhoneMasker.mask(null)).isNull();
        assertThat(PhoneMasker.mask("   ")).isNull();
        assertThat(PhoneMasker.mask("123")).isEqualTo("***");
        assertThat(PhoneMasker.mask(" 13812345678 ")).isEqualTo("138******78");
    }
}
