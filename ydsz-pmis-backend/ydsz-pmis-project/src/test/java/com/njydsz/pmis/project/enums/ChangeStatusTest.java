package com.njydsz.pmis.project.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 变更状态机测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ChangeStatus 变更状态机")
class ChangeStatusTest {

    @Test
    @DisplayName("终态判断")
    void terminal() {
        assertThat(ChangeStatus.EXECUTED.isTerminal()).isTrue();
        assertThat(ChangeStatus.REJECTED.isTerminal()).isTrue();
        assertThat(ChangeStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(ChangeStatus.DRAFT.isTerminal()).isFalse();
        assertThat(ChangeStatus.APPROVED.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("fromCode 解析")
    void fromCode() {
        assertThat(ChangeStatus.fromCode("DRAFT")).isEqualTo(ChangeStatus.DRAFT);
        assertThat(ChangeStatus.fromCode("draft")).isEqualTo(ChangeStatus.DRAFT);
        assertThat(ChangeStatus.fromCode(null)).isNull();
        assertThat(ChangeStatus.fromCode("XXX")).isNull();
    }

    @Test
    @DisplayName("DRAFT->SUBMITTED/CANCELLED")
    void draftTransitions() {
        assertThat(ChangeStatus.DRAFT.canTransitTo(ChangeStatus.SUBMITTED)).isTrue();
        assertThat(ChangeStatus.DRAFT.canTransitTo(ChangeStatus.CANCELLED)).isTrue();
        assertThat(ChangeStatus.DRAFT.canTransitTo(ChangeStatus.APPROVED)).isFalse();
    }

    @Test
    @DisplayName("SUBMITTED->UNDER_REVIEW/CANCELLED")
    void submitted() {
        assertThat(ChangeStatus.SUBMITTED.canTransitTo(ChangeStatus.UNDER_REVIEW)).isTrue();
        assertThat(ChangeStatus.SUBMITTED.canTransitTo(ChangeStatus.CANCELLED)).isTrue();
        assertThat(ChangeStatus.SUBMITTED.canTransitTo(ChangeStatus.DRAFT)).isFalse();
    }

    @Test
    @DisplayName("终态不可迁移")
    void terminalNoTrans() {
        assertThat(ChangeStatus.EXECUTED.canTransitTo(ChangeStatus.DRAFT)).isFalse();
        assertThat(ChangeStatus.REJECTED.canTransitTo(ChangeStatus.SUBMITTED)).isFalse();
    }

    @Test
    @DisplayName("自身到自身")
    void selfTransition() {
        assertThat(ChangeStatus.DRAFT.canTransitTo(ChangeStatus.DRAFT)).isTrue();
    }

    @Test
    @DisplayName("目标为 null")
    void nullTarget() {
        assertThat(ChangeStatus.DRAFT.canTransitTo(null)).isFalse();
    }
}
