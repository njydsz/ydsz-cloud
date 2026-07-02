package com.njydsz.pmis.project.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ContractStatus 合同状态机单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ContractStatus 合同状态机测试")
class ContractStatusTest {

    @Test
    @DisplayName("EXPIRED 与 TERMINATED 是终态")
    void terminalStates() {
        assertThat(ContractStatus.EXPIRED.isTerminal()).isTrue();
        assertThat(ContractStatus.TERMINATED.isTerminal()).isTrue();
        assertThat(ContractStatus.DRAFT.isTerminal()).isFalse();
        assertThat(ContractStatus.ACTIVE.isTerminal()).isFalse();
        assertThat(ContractStatus.SUSPENDED.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("DRAFT -> SUBMITTED")
    void draftTransition() {
        assertThat(ContractStatus.DRAFT.canTransitTo(ContractStatus.SUBMITTED)).isTrue();
        assertThat(ContractStatus.DRAFT.canTransitTo(ContractStatus.APPROVING)).isFalse();
        assertThat(ContractStatus.DRAFT.canTransitTo(ContractStatus.ACTIVE)).isFalse();
    }

    @Test
    @DisplayName("SUBMITTED -> APPROVING")
    void submittedTransition() {
        assertThat(ContractStatus.SUBMITTED.canTransitTo(ContractStatus.APPROVING)).isTrue();
        assertThat(ContractStatus.SUBMITTED.canTransitTo(ContractStatus.ACTIVE)).isFalse();
    }

    @Test
    @DisplayName("APPROVING -> ACTIVE 或 DRAFT")
    void approvingTransition() {
        assertThat(ContractStatus.APPROVING.canTransitTo(ContractStatus.ACTIVE)).isTrue();
        assertThat(ContractStatus.APPROVING.canTransitTo(ContractStatus.DRAFT)).isTrue();
        assertThat(ContractStatus.APPROVING.canTransitTo(ContractStatus.SUBMITTED)).isFalse();
    }

    @Test
    @DisplayName("ACTIVE 可到 SUSPENDED/EXPIRED/TERMINATED")
    void activeTransition() {
        assertThat(ContractStatus.ACTIVE.canTransitTo(ContractStatus.SUSPENDED)).isTrue();
        assertThat(ContractStatus.ACTIVE.canTransitTo(ContractStatus.EXPIRED)).isTrue();
        assertThat(ContractStatus.ACTIVE.canTransitTo(ContractStatus.TERMINATED)).isTrue();
        assertThat(ContractStatus.ACTIVE.canTransitTo(ContractStatus.DRAFT)).isFalse();
    }

    @Test
    @DisplayName("SUSPENDED 可到 ACTIVE/TERMINATED")
    void suspendedTransition() {
        assertThat(ContractStatus.SUSPENDED.canTransitTo(ContractStatus.ACTIVE)).isTrue();
        assertThat(ContractStatus.SUSPENDED.canTransitTo(ContractStatus.TERMINATED)).isTrue();
        assertThat(ContractStatus.SUSPENDED.canTransitTo(ContractStatus.EXPIRED)).isFalse();
    }

    @Test
    @DisplayName("终态不能迁移到其他状态")
    void terminalNoTransition() {
        for (ContractStatus target : ContractStatus.values()) {
            if (target == ContractStatus.EXPIRED) continue;  // 自身允许
            if (target == ContractStatus.TERMINATED) continue;  // 自身允许
            assertThat(ContractStatus.EXPIRED.canTransitTo(target)).isFalse();
            assertThat(ContractStatus.TERMINATED.canTransitTo(target)).isFalse();
        }
    }

    @Test
    @DisplayName("自身迁移允许")
    void selfTransition() {
        for (ContractStatus s : ContractStatus.values()) {
            assertThat(s.canTransitTo(s)).isTrue();
        }
    }

    @Test
    @DisplayName("canTransitTo null 应返回 false")
    void nullTarget() {
        assertThat(ContractStatus.DRAFT.canTransitTo(null)).isFalse();
    }

    @Test
    @DisplayName("fromCode 解析")
    void fromCode() {
        assertThat(ContractStatus.fromCode("DRAFT")).isEqualTo(ContractStatus.DRAFT);
        assertThat(ContractStatus.fromCode("draft")).isEqualTo(ContractStatus.DRAFT);
        assertThat(ContractStatus.fromCode("UNKNOWN")).isNull();
        assertThat(ContractStatus.fromCode(null)).isNull();
    }
}
