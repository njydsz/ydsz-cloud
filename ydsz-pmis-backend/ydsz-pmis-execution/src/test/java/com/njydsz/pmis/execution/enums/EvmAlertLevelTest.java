package com.njydsz.pmis.execution.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EvmAlertLevel EVM 告警等级")
class EvmAlertLevelTest {

    @Test
    @DisplayName("fromCode 大小写不敏感")
    void fromCode() {
        assertThat(EvmAlertLevel.fromCode("normal")).isEqualTo(EvmAlertLevel.NORMAL);
        assertThat(EvmAlertLevel.fromCode("YELLOW")).isEqualTo(EvmAlertLevel.YELLOW);
        assertThat(EvmAlertLevel.fromCode(null)).isNull();
        assertThat(EvmAlertLevel.fromCode("X")).isNull();
    }

    @Test
    @DisplayName("评估 CPI 跌破红色 → RED")
    void evaluateCpiRed() {
        EvmAlertLevel l = EvmAlertLevel.evaluate(0.80, 1.0, 0.95, 0.85, 0.95, 0.85);
        assertThat(l).isEqualTo(EvmAlertLevel.RED);
    }

    @Test
    @DisplayName("评估 SPI 黄色 → YELLOW")
    void evaluateSpiYellow() {
        EvmAlertLevel l = EvmAlertLevel.evaluate(1.0, 0.93, 0.95, 0.85, 0.95, 0.85);
        assertThat(l).isEqualTo(EvmAlertLevel.YELLOW);
    }

    @Test
    @DisplayName("评估均健康 → NORMAL")
    void evaluateNormal() {
        EvmAlertLevel l = EvmAlertLevel.evaluate(1.0, 1.0, 0.95, 0.85, 0.95, 0.85);
        assertThat(l).isEqualTo(EvmAlertLevel.NORMAL);
    }
}
