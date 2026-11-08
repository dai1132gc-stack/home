package com.example.realestate.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("证件号脱敏")
class IdCardMaskerTest {

    @Test
    @DisplayName("18 位身份证保留前 6 后 4")
    void masksEighteenDigitId() {
        assertThat(IdCardMasker.mask("310101199001011234")).isEqualTo("310101********1234");
    }

    @Test
    @DisplayName("短号码与空值安全处理")
    void handlesShortAndNull() {
        assertThat(IdCardMasker.mask(null)).isNull();
        assertThat(IdCardMasker.mask("  ")).isNull();
        assertThat(IdCardMasker.mask("12345")).isEqualTo("***");
    }
}
