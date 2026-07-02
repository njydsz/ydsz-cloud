package com.njydsz.pmis.user.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LeaveStatus 状态机测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("LeaveStatus 状态机")
class LeaveStatusTest {

    @Test
    @DisplayName("fromCode 正常解析")
    void fromCode() {
        assertThat(LeaveStatus.fromCode("DRAFT")).isEqualTo(LeaveStatus.DRAFT);
        assertThat(LeaveStatus.fromCode("approved")).isEqualTo(LeaveStatus.APPROVED);
        assertThat(LeaveStatus.fromCode(null)).isNull();
        assertThat(LeaveStatus.fromCode("WRONG")).isNull();
    }

    @Test
    @DisplayName("DRAFT 可流转到 SUBMITTED/CANCELLED")
    void draftTransitions() {
        assertThat(LeaveStatus.DRAFT.canTransitTo(LeaveStatus.SUBMITTED)).isTrue();
        assertThat(LeaveStatus.DRAFT.canTransitTo(LeaveStatus.CANCELLED)).isTrue();
        assertThat(LeaveStatus.DRAFT.canTransitTo(LeaveStatus.APPROVED)).isFalse();
        assertThat(LeaveStatus.DRAFT.canTransitTo(LeaveStatus.REJECTED)).isFalse();
    }

    @Test
    @DisplayName("SUBMITTED 可流转到 APPROVED/REJECTED")
    void submittedTransitions() {
        assertThat(LeaveStatus.SUBMITTED.canTransitTo(LeaveStatus.APPROVED)).isTrue();
        assertThat(LeaveStatus.SUBMITTED.canTransitTo(LeaveStatus.REJECTED)).isTrue();
        assertThat(LeaveStatus.SUBMITTED.canTransitTo(LeaveStatus.DRAFT)).isFalse();
    }

    @Test
    @DisplayName("REJECTED 可回退到 DRAFT/SUBMITTED")
    void rejectedTransitions() {
        assertThat(LeaveStatus.REJECTED.canTransitTo(LeaveStatus.DRAFT)).isTrue();
        assertThat(LeaveStatus.REJECTED.canTransitTo(LeaveStatus.SUBMITTED)).isTrue();
        assertThat(LeaveStatus.REJECTED.canTransitTo(LeaveStatus.APPROVED)).isFalse();
    }

    @Test
    @DisplayName("终态不可流转")
    void terminalState() {
        assertThat(LeaveStatus.APPROVED.canTransitTo(LeaveStatus.DRAFT)).isFalse();
        assertThat(LeaveStatus.CANCELLED.canTransitTo(LeaveStatus.DRAFT)).isFalse();
        assertThat(LeaveStatus.APPROVED.isTerminal()).isTrue();
        assertThat(LeaveStatus.CANCELLED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("自身不能流转到自己")
    void selfTransition() {
        assertThat(LeaveStatus.DRAFT.canTransitTo(LeaveStatus.DRAFT)).isFalse();
    }
}
