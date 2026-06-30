package com.njydsz.pmis.project.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InitiationStage 状态机测试
 */
@DisplayName("InitiationStage 立项阶段状态机测试")
class InitiationStageTest {

    @Test
    @DisplayName("REJECTED 与 CLOSED 是终态")
    void terminalStates() {
        assertThat(InitiationStage.REJECTED.isTerminal()).isTrue();
        assertThat(InitiationStage.CLOSED.isTerminal()).isTrue();
        assertThat(InitiationStage.PRE_INITIATION.isTerminal()).isFalse();
        assertThat(InitiationStage.SUBMITTED.isTerminal()).isFalse();
        assertThat(InitiationStage.APPROVING.isTerminal()).isFalse();
        assertThat(InitiationStage.APPROVED.isTerminal()).isFalse();
        assertThat(InitiationStage.EXECUTING.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("PRE_INITIATION 仅可迁移到 SUBMITTED")
    void preInitiationTransition() {
        assertThat(InitiationStage.PRE_INITIATION.canTransitTo(InitiationStage.SUBMITTED)).isTrue();
        assertThat(InitiationStage.PRE_INITIATION.canTransitTo(InitiationStage.APPROVING)).isFalse();
        assertThat(InitiationStage.PRE_INITIATION.canTransitTo(InitiationStage.APPROVED)).isFalse();
        assertThat(InitiationStage.PRE_INITIATION.canTransitTo(InitiationStage.REJECTED)).isFalse();
        assertThat(InitiationStage.PRE_INITIATION.canTransitTo(InitiationStage.EXECUTING)).isFalse();
    }

    @Test
    @DisplayName("SUBMITTED 可迁移到 APPROVING 或 REJECTED")
    void submittedTransition() {
        assertThat(InitiationStage.SUBMITTED.canTransitTo(InitiationStage.APPROVING)).isTrue();
        assertThat(InitiationStage.SUBMITTED.canTransitTo(InitiationStage.REJECTED)).isTrue();
        assertThat(InitiationStage.SUBMITTED.canTransitTo(InitiationStage.APPROVED)).isFalse();
    }

    @Test
    @DisplayName("APPROVING 可迁移到 APPROVED 或 REJECTED")
    void approvingTransition() {
        assertThat(InitiationStage.APPROVING.canTransitTo(InitiationStage.APPROVED)).isTrue();
        assertThat(InitiationStage.APPROVING.canTransitTo(InitiationStage.REJECTED)).isTrue();
        assertThat(InitiationStage.APPROVING.canTransitTo(InitiationStage.SUBMITTED)).isFalse();
    }

    @Test
    @DisplayName("APPROVED 可迁移到 EXECUTING 或 CLOSED")
    void approvedTransition() {
        assertThat(InitiationStage.APPROVED.canTransitTo(InitiationStage.EXECUTING)).isTrue();
        assertThat(InitiationStage.APPROVED.canTransitTo(InitiationStage.CLOSED)).isTrue();
        assertThat(InitiationStage.APPROVED.canTransitTo(InitiationStage.SUBMITTED)).isFalse();
    }

    @Test
    @DisplayName("EXECUTING 仅可迁移到 CLOSED")
    void executingTransition() {
        assertThat(InitiationStage.EXECUTING.canTransitTo(InitiationStage.CLOSED)).isTrue();
        assertThat(InitiationStage.EXECUTING.canTransitTo(InitiationStage.APPROVED)).isFalse();
    }

    @Test
    @DisplayName("REJECTED 仅可回到 PRE_INITIATION")
    void rejectedTransition() {
        assertThat(InitiationStage.REJECTED.canTransitTo(InitiationStage.PRE_INITIATION)).isTrue();
        assertThat(InitiationStage.REJECTED.canTransitTo(InitiationStage.SUBMITTED)).isFalse();
        assertThat(InitiationStage.REJECTED.canTransitTo(InitiationStage.APPROVED)).isFalse();
    }

    @Test
    @DisplayName("CLOSED 是完全终态，不能迁移")
    void closedTerminal() {
        for (InitiationStage s : InitiationStage.values()) {
            if (s == InitiationStage.CLOSED) continue;
            assertThat(InitiationStage.CLOSED.canTransitTo(s))
                    .as("CLOSED should not transit to %s", s)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("canTransitTo 自身总是允许")
    void selfTransition() {
        for (InitiationStage s : InitiationStage.values()) {
            assertThat(s.canTransitTo(s)).isTrue();
        }
    }

    @Test
    @DisplayName("canTransitTo null 应返回 false")
    void nullTarget() {
        assertThat(InitiationStage.PRE_INITIATION.canTransitTo(null)).isFalse();
    }

    @Test
    @DisplayName("fromCode 大小写不敏感")
    void fromCode_caseInsensitive() {
        assertThat(InitiationStage.fromCode("PRE_INITIATION")).isEqualTo(InitiationStage.PRE_INITIATION);
        assertThat(InitiationStage.fromCode("pre_initiation")).isEqualTo(InitiationStage.PRE_INITIATION);
        assertThat(InitiationStage.fromCode("Pre_InitiAtion")).isEqualTo(InitiationStage.PRE_INITIATION);
    }

    @Test
    @DisplayName("fromCode 未知/空/null 应返回 null")
    void fromCode_invalid() {
        assertThat(InitiationStage.fromCode("UNKNOWN")).isNull();
        assertThat(InitiationStage.fromCode("")).isNull();
        assertThat(InitiationStage.fromCode(null)).isNull();
    }

    @Test
    @DisplayName("getCode / getDesc 正确性")
    void getCodeAndDesc() {
        assertThat(InitiationStage.PRE_INITIATION.getCode()).isEqualTo("PRE_INITIATION");
        assertThat(InitiationStage.PRE_INITIATION.getDesc()).isEqualTo("预立项");
        assertThat(InitiationStage.CLOSED.getCode()).isEqualTo("CLOSED");
        assertThat(InitiationStage.CLOSED.getDesc()).isEqualTo("已结项");
    }
}
