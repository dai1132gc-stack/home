package com.example.realestate.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("合同状态机")
class ContractStatusTest {

    @Test
    @DisplayName("DRAFT 只能提交待签或作废")
    void draftTransitions() {
        assertThat(ContractStatus.DRAFT.canTransitionTo(ContractStatus.PENDING_SIGN)).isTrue();
        assertThat(ContractStatus.DRAFT.canTransitionTo(ContractStatus.CANCELLED)).isTrue();
        assertThat(ContractStatus.DRAFT.canTransitionTo(ContractStatus.ACTIVE)).isFalse();
        assertThat(ContractStatus.DRAFT.canTransitionTo(ContractStatus.DRAFT)).isFalse();
    }

    @Test
    @DisplayName("PENDING_SIGN 只能生效、作废或终止")
    void pendingSignTransitions() {
        assertThat(ContractStatus.PENDING_SIGN.canTransitionTo(ContractStatus.ACTIVE)).isTrue();
        assertThat(ContractStatus.PENDING_SIGN.canTransitionTo(ContractStatus.CANCELLED)).isTrue();
        assertThat(ContractStatus.PENDING_SIGN.canTransitionTo(ContractStatus.TERMINATED)).isTrue();
        assertThat(ContractStatus.PENDING_SIGN.canTransitionTo(ContractStatus.DRAFT)).isFalse();
    }

    @Test
    @DisplayName("ACTIVE 只能终止或到期，不能回到草稿")
    void activeTransitions() {
        assertThat(ContractStatus.ACTIVE.canTransitionTo(ContractStatus.TERMINATED)).isTrue();
        assertThat(ContractStatus.ACTIVE.canTransitionTo(ContractStatus.EXPIRED)).isTrue();
        assertThat(ContractStatus.ACTIVE.canTransitionTo(ContractStatus.DRAFT)).isFalse();
        assertThat(ContractStatus.ACTIVE.canTransitionTo(ContractStatus.PENDING_SIGN)).isFalse();
    }

    @Test
    @DisplayName("只有 PENDING_SIGN/ACTIVE 占用房源")
    void occupancy() {
        assertThat(ContractStatus.PENDING_SIGN.occupiesHouse()).isTrue();
        assertThat(ContractStatus.ACTIVE.occupiesHouse()).isTrue();
        assertThat(ContractStatus.DRAFT.occupiesHouse()).isFalse();
        assertThat(ContractStatus.TERMINATED.occupiesHouse()).isFalse();
        assertThat(ContractStatus.EXPIRED.occupiesHouse()).isFalse();
        assertThat(ContractStatus.CANCELLED.occupiesHouse()).isFalse();
    }

    @Test
    @DisplayName("终态不可迁移")
    void terminalStatesAreFrozen() {
        for (ContractStatus terminal : new ContractStatus[]{
                ContractStatus.EXPIRED, ContractStatus.TERMINATED, ContractStatus.CANCELLED}) {
            assertThat(terminal.isTerminal()).isTrue();
            for (ContractStatus target : ContractStatus.values()) {
                assertThat(terminal.canTransitionTo(target)).isFalse();
            }
        }
    }

    @Test
    @DisplayName("非法字符串解析为 null")
    void ofHandlesInvalid() {
        assertThat(ContractStatus.of("active")).isEqualTo(ContractStatus.ACTIVE);
        assertThat(ContractStatus.of("SIGNED")).isNull();
        assertThat(ContractStatus.of(null)).isNull();
    }
}
