package com.njydsz.pmis.execution.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SimulationStatus 测算状态机")
class SimulationStatusTest {

    @Test
    @DisplayName("fromCode 容错")
    void fromCode() {
        assertThat(SimulationStatus.fromCode("draft")).isEqualTo(SimulationStatus.DRAFT);
        assertThat(SimulationStatus.fromCode("APPROVED")).isEqualTo(SimulationStatus.APPROVED);
        assertThat(SimulationStatus.fromCode(null)).isNull();
        assertThat(SimulationStatus.fromCode("X")).isNull();
    }

    @Test
    @DisplayName("DRAFT → SUBMITTED 合法")
    void draftToSubmitted() {
        assertThat(SimulationStatus.DRAFT.canTransitTo(SimulationStatus.SUBMITTED)).isTrue();
        assertThat(SimulationStatus.DRAFT.canTransitTo(SimulationStatus.REJECTED)).isTrue();
        assertThat(SimulationStatus.DRAFT.canTransitTo(SimulationStatus.APPROVED)).isFalse();
    }

    @Test
    @DisplayName("SUBMITTED → APPROVED/REJECTED 合法")
    void submittedToApproved() {
        assertThat(SimulationStatus.SUBMITTED.canTransitTo(SimulationStatus.APPROVED)).isTrue();
        assertThat(SimulationStatus.SUBMITTED.canTransitTo(SimulationStatus.REJECTED)).isTrue();
        assertThat(SimulationStatus.SUBMITTED.canTransitTo(SimulationStatus.ARCHIVED)).isFalse();
    }

    @Test
    @DisplayName("APPROVED → ARCHIVED 合法")
    void approvedToArchived() {
        assertThat(SimulationStatus.APPROVED.canTransitTo(SimulationStatus.ARCHIVED)).isTrue();
        assertThat(SimulationStatus.APPROVED.canTransitTo(SimulationStatus.REJECTED)).isFalse();
    }

    @Test
    @DisplayName("REJECTED → DRAFT/SUBMITTED 回退")
    void rejectedReturn() {
        assertThat(SimulationStatus.REJECTED.canTransitTo(SimulationStatus.DRAFT)).isTrue();
        assertThat(SimulationStatus.REJECTED.canTransitTo(SimulationStatus.SUBMITTED)).isTrue();
    }

    @Test
    @DisplayName("终态 APPROVED/ARCHIVED 不能跨状态")
    void terminalBlock() {
        assertThat(SimulationStatus.ARCHIVED.canTransitTo(SimulationStatus.DRAFT)).isFalse();
        assertThat(SimulationStatus.ARCHIVED.canTransitTo(SimulationStatus.APPROVED)).isFalse();
    }

    @Test
    @DisplayName("isTerminal")
    void isTerminalCheck() {
        assertThat(SimulationStatus.APPROVED.isTerminal()).isTrue();
        assertThat(SimulationStatus.ARCHIVED.isTerminal()).isTrue();
        assertThat(SimulationStatus.REJECTED.isTerminal()).isTrue();
        assertThat(SimulationStatus.DRAFT.isTerminal()).isFalse();
        assertThat(SimulationStatus.SUBMITTED.isTerminal()).isFalse();
    }
}
