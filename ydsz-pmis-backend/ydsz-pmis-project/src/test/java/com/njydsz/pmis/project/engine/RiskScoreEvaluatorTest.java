package com.njydsz.pmis.project.engine;

import com.njydsz.pmis.project.entity.RiskDO;
import com.njydsz.pmis.project.enums.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("风险评分引擎测试")
class RiskScoreEvaluatorTest {

    @Test
    @DisplayName("LOW概率 × LOW影响 = LOW风险")
    void shouldReturnLowWhenBothLow() {
        RiskLevel result = RiskScoreEvaluator.evaluate("LOW", "LOW");
        assertEquals(RiskLevel.LOW, result);
    }

    @Test
    @DisplayName("LOW概率 × HIGH影响 = LOW风险 (score=3)")
    void shouldReturnLowWhenLowProbHighImpact() {
        RiskLevel result = RiskScoreEvaluator.evaluate("LOW", "HIGH");
        assertEquals(RiskLevel.LOW, result);
    }

    @Test
    @DisplayName("MEDIUM概率 × MEDIUM影响 = MEDIUM风险 (score=4)")
    void shouldReturnMediumWhenBothMedium() {
        RiskLevel result = RiskScoreEvaluator.evaluate("MEDIUM", "MEDIUM");
        assertEquals(RiskLevel.MEDIUM, result);
    }

    @Test
    @DisplayName("HIGH概率 × HIGH影响 = HIGH风险 (score=9)")
    void shouldReturnHighWhenBothHigh() {
        RiskLevel result = RiskScoreEvaluator.evaluate("HIGH", "HIGH");
        assertEquals(RiskLevel.HIGH, result);
    }

    @Test
    @DisplayName("MEDIUM概率 × HIGH影响 = HIGH风险 (score=6)")
    void shouldReturnHighWhenMediumProbHighImpact() {
        RiskLevel result = RiskScoreEvaluator.evaluate("MEDIUM", "HIGH");
        assertEquals(RiskLevel.HIGH, result);
    }

    @Test
    @DisplayName("null 参数应返回 MEDIUM 权重(2)的处理")
    void shouldHandleNullParameters() {
        // null 在 weightOf 中返回 2（MEDIUM），2 × 2 = 4 → MEDIUM
        RiskLevel result = RiskScoreEvaluator.evaluate(null, null);
        assertEquals(RiskLevel.MEDIUM, result);
    }

    @Test
    @DisplayName("通过 RiskDO 实体评估")
    void shouldEvaluateFromRiskEntity() {
        RiskDO risk = new RiskDO();
        risk.setProbability("HIGH");
        risk.setImpact("HIGH");
        RiskLevel result = RiskScoreEvaluator.evaluate(risk);
        assertEquals(RiskLevel.HIGH, result);
    }

    @Test
    @DisplayName("RiskDO 为 null 时返回 LOW")
    void shouldReturnLowWhenRiskIsNull() {
        RiskLevel result = RiskScoreEvaluator.evaluate((RiskDO) null);
        assertEquals(RiskLevel.LOW, result);
    }
}