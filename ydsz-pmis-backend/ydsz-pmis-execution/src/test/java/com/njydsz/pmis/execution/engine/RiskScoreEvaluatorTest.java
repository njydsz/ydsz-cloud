package com.njydsz.pmis.execution.engine;

import com.njydsz.pmis.execution.enums.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RiskScoreEvaluator 风险评分器测试")
class RiskScoreEvaluatorTest {

    @Test
    @DisplayName("LOW + LOW = LOW")
    void lowLow() {
        assertThat(RiskScoreEvaluator.evaluate("LOW", "LOW")).isEqualTo(RiskLevel.LOW);
    }

    @Test
    @DisplayName("HIGH + HIGH = HIGH")
    void highHigh() {
        assertThat(RiskScoreEvaluator.evaluate("HIGH", "HIGH")).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    @DisplayName("MEDIUM + HIGH = HIGH (6)")
    void mediumHigh() {
        assertThat(RiskScoreEvaluator.evaluate("MEDIUM", "HIGH")).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    @DisplayName("MEDIUM + MEDIUM = MEDIUM (4)")
    void mediumMedium() {
        assertThat(RiskScoreEvaluator.evaluate("MEDIUM", "MEDIUM")).isEqualTo(RiskLevel.MEDIUM);
    }

    @Test
    @DisplayName("null 默认 MEDIUM")
    void nullDefault() {
        assertThat(RiskScoreEvaluator.evaluate(null, "HIGH")).isEqualTo(RiskLevel.HIGH);
        assertThat(RiskScoreEvaluator.evaluate("HIGH", null)).isEqualTo(RiskLevel.HIGH);
    }

    @Test
    @DisplayName("大小写无关")
    void caseInsensitive() {
        assertThat(RiskScoreEvaluator.evaluate("low", "high")).isEqualTo(RiskLevel.MEDIUM);
    }
}
