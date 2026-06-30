package com.njydsz.pmis.execution.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApprovalStatus 通用审批状态机测试")
class ApprovalStatusTest {

    @Test
    @DisplayName("终态")
    void isTerminal() {
        assertThat(ApprovalStatus.APPROVED.isTerminal()).isTrue();
        assertThat(ApprovalStatus.REJECTED.isTerminal()).isTrue();
        assertThat(ApprovalStatus.PAID.isTerminal()).isTrue();
        assertThat(ApprovalStatus.DRAFT.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("合法迁移")
    void canTransit() {
        assertThat(ApprovalStatus.DRAFT.canTransitTo(ApprovalStatus.SUBMITTED)).isTrue();
        assertThat(ApprovalStatus.SUBMITTED.canTransitTo(ApprovalStatus.APPROVED)).isTrue();
        assertThat(ApprovalStatus.SUBMITTED.canTransitTo(ApprovalStatus.REJECTED)).isTrue();
        assertThat(ApprovalStatus.REJECTED.canTransitTo(ApprovalStatus.DRAFT)).isTrue();
        assertThat(ApprovalStatus.APPROVED.canTransitTo(ApprovalStatus.PAID)).isTrue();
    }

    @Test
    @DisplayName("非法迁移")
    void cannotTransit() {
        assertThat(ApprovalStatus.DRAFT.canTransitTo(ApprovalStatus.APPROVED)).isFalse();
        assertThat(ApprovalStatus.PAID.canTransitTo(ApprovalStatus.APPROVED)).isFalse();
    }
}
