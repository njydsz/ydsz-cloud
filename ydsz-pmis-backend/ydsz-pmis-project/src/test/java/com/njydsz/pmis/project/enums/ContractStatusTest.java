package com.njydsz.pmis.project.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ContractStatus 合同状态机测试")
class ContractStatusTest {

    @Test
    @DisplayName("终态判定")
    void isTerminal() {
        assertThat(ContractStatus.EXPIRED.isTerminal()).isTrue();
        assertThat(ContractStatus.TERMINATED.isTerminal()).isTrue();
        assertThat(ContractStatus.ACTIVE.isTerminal()).isFalse();
        assertThat(ContractStatus.SUSPENDED.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("合法状态迁移")
    void canTransit() {
        assertThat(ContractStatus.DRAFT.canTransitTo(ContractStatus.SUBMITTED)).isTrue();
        assertThat(ContractStatus.SUBMITTED.canTransitTo(ContractStatus.APPROVING)).isTrue();
        assertThat(ContractStatus.APPROVING.canTransitTo(ContractStatus.ACTIVE)).isTrue();
        assertThat(ContractStatus.ACTIVE.canTransitTo(ContractStatus.SUSPENDED)).isTrue();
        assertThat(ContractStatus.SUSPENDED.canTransitTo(ContractStatus.ACTIVE)).isTrue();
        assertThat(ContractStatus.ACTIVE.canTransitTo(ContractStatus.EXPIRED)).isTrue();
    }

    @Test
    @DisplayName("非法迁移")
    void cannotTransit() {
        assertThat(ContractStatus.DRAFT.canTransitTo(ContractStatus.ACTIVE)).isFalse();
        assertThat(ContractStatus.EXPIRED.canTransitTo(ContractStatus.ACTIVE)).isFalse();
        assertThat(ContractStatus.TERMINATED.canTransitTo(ContractStatus.DRAFT)).isFalse();
    }

    @Test
    @DisplayName("fromCode 容错")
    void fromCode() {
        assertThat(ContractStatus.fromCode("active")).isEqualTo(ContractStatus.ACTIVE);
        assertThat(ContractStatus.fromCode(null)).isNull();
        assertThat(ContractStatus.fromCode("UNKNOWN")).isNull();
    }
}
