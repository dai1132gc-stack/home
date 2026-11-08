package com.example.realestate.infrastructure.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogMaskerTest {

    @Test
    @DisplayName("手机号脱敏：保留前3后2")
    void maskPhone() {
        assertThat(LogMasker.maskPhone("13812345678")).isEqualTo("138******78");
        assertThat(LogMasker.maskPhone("123")).isEqualTo("***");
        assertThat(LogMasker.maskPhone(null)).isEqualTo("***");
    }

    @Test
    @DisplayName("IP 脱敏：IPv4 保留前两段")
    void maskIp() {
        assertThat(LogMasker.maskIp("192.168.1.100")).isEqualTo("192.168.*.*");
        assertThat(LogMasker.maskIp("unknown")).isEqualTo("remote");
    }
}
