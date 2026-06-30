package com.njydsz.pmis.execution.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RiskStatus 风险状态机测试")
class RiskStatusTest {

    @Test
    @DisplayName("终态")
    void isTerminal() {
        assertThat(RiskStatus.CLOSED.isTerminal()).isTrue();
        assertThat(RiskStatus.OPEN.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("合法迁移")
    void canTransit() {
        assertThat(RiskStatus.OPEN.canTransitTo(RiskStatus.MITIGATING)).isTrue();
        assertThat(RiskStatus.OPEN.canTransitTo(RiskStatus.OCCURRED)).isTrue();
        assertThat(RiskStatus.OPEN.canTransitTo(RiskStatus.CLOSED)).isTrue();
        assertThat(RiskStatus.MITIGATING.canTransitTo(RiskStatus.CLOSED)).isTrue();
        assertThat(RiskStatus.OCCURRED.canTransitTo(RiskStatus.MITIGATING)).isTrue();
    }

    @Test
    @DisplayName("非法迁移")
    void cannotTransit() {
        assertThat(RiskStatus.CLOSED.canTransitTo(RiskStatus.OPEN)).isFalse();
        assertThat(RiskStatus.OCCURRED.canTransitTo(RiskStatus.OPEN)).isFalse();
    }
}
