package com.njydsz.pmis.execution.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 结项状态机测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ClosureStatus 结项状态机")
class ClosureStatusTest {

    @Test
    @DisplayName("终态")
    void terminal() {
        assertThat(ClosureStatus.ARCHIVED.isTerminal()).isTrue();
        assertThat(ClosureStatus.DRAFT.isTerminal()).isFalse();
        assertThat(ClosureStatus.APPROVED.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("DRAFT->SUBMITTED")
    void draft2Sub() {
        assertThat(ClosureStatus.DRAFT.canTransitTo(ClosureStatus.SUBMITTED)).isTrue();
        assertThat(ClosureStatus.DRAFT.canTransitTo(ClosureStatus.UNDER_REVIEW)).isFalse();
    }

    @Test
    @DisplayName("SUBMITTED->UNDER_REVIEW/REJECTED")
    void submitted() {
        assertThat(ClosureStatus.SUBMITTED.canTransitTo(ClosureStatus.UNDER_REVIEW)).isTrue();
        assertThat(ClosureStatus.SUBMITTED.canTransitTo(ClosureStatus.REJECTED)).isTrue();
    }

    @Test
    @DisplayName("APPROVED->ARCHIVED")
    void approvedArchived() {
        assertThat(ClosureStatus.APPROVED.canTransitTo(ClosureStatus.ARCHIVED)).isTrue();
        assertThat(ClosureStatus.APPROVED.canTransitTo(ClosureStatus.REJECTED)).isFalse();
    }

    @Test
    @DisplayName("REJECTED->SUBMITTED/DRAFT")
    void rejected() {
        assertThat(ClosureStatus.REJECTED.canTransitTo(ClosureStatus.SUBMITTED)).isTrue();
        assertThat(ClosureStatus.REJECTED.canTransitTo(ClosureStatus.DRAFT)).isTrue();
    }

    @Test
    @DisplayName("ARCHIVED 终态")
    void archived() {
        assertThat(ClosureStatus.ARCHIVED.canTransitTo(ClosureStatus.DRAFT)).isFalse();
    }

    @Test
    @DisplayName("fromCode")
    void fromCode() {
        assertThat(ClosureStatus.fromCode("DRAFT")).isEqualTo(ClosureStatus.DRAFT);
        assertThat(ClosureStatus.fromCode(null)).isNull();
    }
}
