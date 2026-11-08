package com.example.realestate.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("预约状态机")
class AppointmentStatusTest {

    @Test
    @DisplayName("PENDING 可确认或取消，不能直接完成")
    void pendingTransitions() {
        assertThat(AppointmentStatus.PENDING.canTransitionTo(AppointmentStatus.CONFIRMED)).isTrue();
        assertThat(AppointmentStatus.PENDING.canTransitionTo(AppointmentStatus.CANCELLED)).isTrue();
        assertThat(AppointmentStatus.PENDING.canTransitionTo(AppointmentStatus.COMPLETED)).isFalse();
        assertThat(AppointmentStatus.PENDING.canTransitionTo(AppointmentStatus.NO_SHOW)).isFalse();
        assertThat(AppointmentStatus.PENDING.canTransitionTo(AppointmentStatus.PENDING)).isFalse();
    }

    @Test
    @DisplayName("CONFIRMED 可完成、取消或爽约")
    void confirmedTransitions() {
        assertThat(AppointmentStatus.CONFIRMED.canTransitionTo(AppointmentStatus.COMPLETED)).isTrue();
        assertThat(AppointmentStatus.CONFIRMED.canTransitionTo(AppointmentStatus.CANCELLED)).isTrue();
        assertThat(AppointmentStatus.CONFIRMED.canTransitionTo(AppointmentStatus.NO_SHOW)).isTrue();
        assertThat(AppointmentStatus.CONFIRMED.canTransitionTo(AppointmentStatus.PENDING)).isFalse();
    }

    @Test
    @DisplayName("终态不可再迁移")
    void terminalStatesAreFrozen() {
        for (AppointmentStatus terminal : new AppointmentStatus[]{
                AppointmentStatus.COMPLETED, AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW}) {
            assertThat(terminal.isTerminal()).isTrue();
            for (AppointmentStatus target : AppointmentStatus.values()) {
                assertThat(terminal.canTransitionTo(target)).isFalse();
            }
        }
    }

    @Test
    @DisplayName("非法字符串解析为 null，大小写不敏感")
    void ofHandlesInvalid() {
        assertThat(AppointmentStatus.of("confirmed")).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(AppointmentStatus.of("NO_SHOW")).isEqualTo(AppointmentStatus.NO_SHOW);
        assertThat(AppointmentStatus.of("UNKNOWN")).isNull();
        assertThat(AppointmentStatus.of(" ")).isNull();
    }
}
