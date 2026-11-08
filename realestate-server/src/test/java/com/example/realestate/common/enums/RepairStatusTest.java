package com.example.realestate.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("报修状态机：合法迁移与终态")
class RepairStatusTest {

    @Test
    @DisplayName("主流程 SUBMITTED→ACCEPTED→PROCESSING→RESOLVED→CLOSED 全部合法")
    void mainFlow() {
        assertThat(RepairStatus.SUBMITTED.canTransitionTo(RepairStatus.ACCEPTED)).isTrue();
        assertThat(RepairStatus.ACCEPTED.canTransitionTo(RepairStatus.PROCESSING)).isTrue();
        assertThat(RepairStatus.PROCESSING.canTransitionTo(RepairStatus.RESOLVED)).isTrue();
        assertThat(RepairStatus.RESOLVED.canTransitionTo(RepairStatus.CLOSED)).isTrue();
        assertThat(RepairStatus.CLOSED.canTransitionTo(RepairStatus.REOPENED)).isTrue();
        assertThat(RepairStatus.REOPENED.canTransitionTo(RepairStatus.PROCESSING)).isTrue();
    }

    @Test
    @DisplayName("撤销仅允许 SUBMITTED/ACCEPTED，CANCELLED 为终态")
    void cancelRules() {
        assertThat(RepairStatus.SUBMITTED.canTransitionTo(RepairStatus.CANCELLED)).isTrue();
        assertThat(RepairStatus.ACCEPTED.canTransitionTo(RepairStatus.CANCELLED)).isTrue();
        assertThat(RepairStatus.PROCESSING.canTransitionTo(RepairStatus.CANCELLED)).isFalse();
        assertThat(RepairStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(RepairStatus.CANCELLED.canTransitionTo(RepairStatus.SUBMITTED)).isFalse();
    }

    @Test
    @DisplayName("非法跳转（跳过受理、解决后直接处理、重复关闭）被拒绝")
    void illegalTransitions() {
        assertThat(RepairStatus.SUBMITTED.canTransitionTo(RepairStatus.PROCESSING)).isFalse();
        assertThat(RepairStatus.SUBMITTED.canTransitionTo(RepairStatus.RESOLVED)).isFalse();
        assertThat(RepairStatus.RESOLVED.canTransitionTo(RepairStatus.PROCESSING)).isFalse();
        assertThat(RepairStatus.CLOSED.canTransitionTo(RepairStatus.CLOSED)).isFalse();
        assertThat(RepairStatus.PROCESSING.canTransitionTo(RepairStatus.CLOSED)).isFalse();
    }

    @Test
    @DisplayName("字符串解析大小写不敏感，未知值返回 null")
    void parsing() {
        assertThat(RepairStatus.of(" accepted ")).isEqualTo(RepairStatus.ACCEPTED);
        assertThat(RepairStatus.of("UNKNOWN")).isNull();
        assertThat(RepairStatus.of(null)).isNull();
        assertThat(RepairStatus.SUBMITTED.label()).isEqualTo("待受理");
    }
}
