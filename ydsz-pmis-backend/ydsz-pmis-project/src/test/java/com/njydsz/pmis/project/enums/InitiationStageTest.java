package com.njydsz.pmis.project.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InitiationStage 立项阶段状态机测试")
class InitiationStageTest {

    @Test
    @DisplayName("终态判定")
    void isTerminal() {
        assertThat(InitiationStage.REJECTED.isTerminal()).isTrue();
        assertThat(InitiationStage.CLOSED.isTerminal()).isTrue();
        assertThat(InitiationStage.PRE_INITIATION.isTerminal()).isFalse();
        assertThat(InitiationStage.EXECUTING.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("合法状态迁移")
    void canTransit() {
        assertThat(InitiationStage.PRE_INITIATION.canTransitTo(InitiationStage.SUBMITTED)).isTrue();
        assertThat(InitiationStage.SUBMITTED.canTransitTo(InitiationStage.APPROVING)).isTrue();
        assertThat(InitiationStage.APPROVING.canTransitTo(InitiationStage.APPROVED)).isTrue();
        assertThat(InitiationStage.APPROVED.canTransitTo(InitiationStage.EXECUTING)).isTrue();
        assertThat(InitiationStage.EXECUTING.canTransitTo(InitiationStage.CLOSED)).isTrue();
    }

    @Test
    @DisplayName("驳回可重新发起")
    void rejectedRestart() {
        assertThat(InitiationStage.REJECTED.canTransitTo(InitiationStage.PRE_INITIATION)).isTrue();
    }

    @Test
    @DisplayName("非法迁移")
    void cannotTransit() {
        assertThat(InitiationStage.CLOSED.canTransitTo(InitiationStage.EXECUTING)).isFalse();
        assertThat(InitiationStage.PRE_INITIATION.canTransitTo(InitiationStage.APPROVED)).isFalse();
        assertThat(InitiationStage.SUBMITTED.canTransitTo(InitiationStage.EXECUTING)).isFalse();
    }

    @Test
    @DisplayName("fromCode 兼容大小写")
    void fromCode() {
        assertThat(InitiationStage.fromCode("approved")).isEqualTo(InitiationStage.APPROVED);
        assertThat(InitiationStage.fromCode(null)).isNull();
        assertThat(InitiationStage.fromCode("UNKNOWN")).isNull();
    }
}
