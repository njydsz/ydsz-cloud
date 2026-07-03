package com.njydsz.pmis.literule.ai;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleEngineStats;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.config.LiteRuleProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RuleHealthScoreService 单元测试
 */
@DisplayName("规则健康度评分服务测试")
class RuleHealthScoreServiceTest {

    private LiteRuleProperties.Ai aiConfig;
    private RuleHealthScoreService service;

    @BeforeEach
    void setUp() {
        aiConfig = new LiteRuleProperties.Ai();
        aiConfig.setHealthHitRateWeight(0.3);
        aiConfig.setHealthErrorRateWeight(0.3);
        aiConfig.setHealthComplexityWeight(0.2);
        aiConfig.setHealthCoverageWeight(0.2);
        aiConfig.setHealthComplexityThreshold(80);
        service = new RuleHealthScoreService(aiConfig);
    }

    @Test
    @DisplayName("无统计时所有维度满分")
    void shouldFullScoreWhenNoStats() {
        RuleDefinition rule = RuleDefinition.builder()
                .code("R001")
                .name("测试规则")
                .conditionExpression("amount > 1000")
                .defaultSeverity(RuleSeverity.YELLOW)
                .build();
        RuleHealthScore score = service.score(rule, null);
        assertNotNull(score);
        // 命中率（样本不足 → 100）+ 错误率（0 → 100）+ 复杂度（短表达式 → 100）+ 覆盖率（无声明 → 100）= 100
        assertTrue(score.getScore() >= 99.0, "无统计应得近满分，实际 " + score.getScore());
        assertEquals(RuleHealthScore.HealthLevel.EXCELLENT, score.getLevel());
    }

    @Test
    @DisplayName("错误率 100% 应触发 0 分错误率分项")
    void shouldZeroErrorScoreWhenAllError() {
        RuleDefinition rule = RuleDefinition.builder()
                .code("R002").name("坏规则")
                .conditionExpression("amount > 1000")
                .build();
        RuleEngineStats stats = RuleEngineStats.builder()
                .totalEvaluations(100).totalErrors(100).totalTriggered(0)
                .perRuleStats(new java.util.HashMap<>())
                .build();
        stats.getPerRuleStats().put("R002", RuleEngineStats.RuleStat.builder()
                .executions(100).triggered(0).errors(100).build());
        RuleHealthScore score = service.score(rule, stats);
        assertEquals(0.0, score.getErrorRateScore(), "错误率 100% 应为 0");
    }

    @Test
    @DisplayName("健康命中率 5%-30% 区间应得满分")
    void shouldFullScoreForHealthyHitRate() {
        RuleDefinition rule = RuleDefinition.builder()
                .code("R003").name("健康规则")
                .conditionExpression("amount > 1000")
                .build();
        // 200 次评估，触发 30 次 = 15% 命中率
        RuleEngineStats stats = RuleEngineStats.builder()
                .perRuleStats(new java.util.HashMap<>())
                .build();
        stats.getPerRuleStats().put("R003", RuleEngineStats.RuleStat.builder()
                .executions(200).triggered(30).errors(0).build());
        RuleHealthScore score = service.score(rule, stats);
        assertEquals(100.0, score.getHitRateScore(), "健康命中区间应为 100");
    }

    @Test
    @DisplayName("样本不足 30 次不评估命中率")
    void shouldIgnoreHitRateWhenSampleTooSmall() {
        RuleDefinition rule = RuleDefinition.builder()
                .code("R004").name("样本不足")
                .conditionExpression("amount > 1000")
                .build();
        RuleEngineStats stats = RuleEngineStats.builder()
                .perRuleStats(new java.util.HashMap<>())
                .build();
        stats.getPerRuleStats().put("R004", RuleEngineStats.RuleStat.builder()
                .executions(10).triggered(0).errors(0).build());
        RuleHealthScore score = service.score(rule, stats);
        assertEquals(100.0, score.getHitRateScore(), "样本不足时按 100 算");
    }

    @Test
    @DisplayName("超长表达式应被扣分")
    void shouldPenalizeLongExpression() {
        StringBuilder sb = new StringBuilder("amount > ");
        for (int i = 0; i < 200; i++) {
            sb.append("a").append(i).append(" + ");
        }
        sb.append("1");
        RuleDefinition rule = RuleDefinition.builder()
                .code("R005").name("长表达式")
                .conditionExpression(sb.toString())
                .build();
        RuleHealthScore score = service.score(rule, null);
        assertTrue(score.getComplexityScore() < 50, "超长表达式应被严重扣分");
    }

    @Test
    @DisplayName("EXCELLENT/GOOD/WARN/BAD 等级边界正确")
    void shouldMapLevelCorrectly() {
        assertEquals(RuleHealthScore.HealthLevel.EXCELLENT, RuleHealthScore.HealthLevel.of(95));
        assertEquals(RuleHealthScore.HealthLevel.GOOD, RuleHealthScore.HealthLevel.of(80));
        assertEquals(RuleHealthScore.HealthLevel.WARN, RuleHealthScore.HealthLevel.of(65));
        assertEquals(RuleHealthScore.HealthLevel.BAD, RuleHealthScore.HealthLevel.of(40));
    }

    @Test
    @DisplayName("表达式 token 数按空白分隔计算")
    void shouldCountTokensByWhitespace() {
        int n = service.countExpressionTokens("a > 1 && b < 2");
        assertEquals(7, n);
        assertEquals(0, service.countExpressionTokens(""));
        assertEquals(0, service.countExpressionTokens(null));
    }

    @Test
    @DisplayName("批量评分与逐条评分结果一致")
    void shouldBatchScoreConsistently() {
        RuleDefinition r1 = RuleDefinition.builder().code("R1").name("R1").conditionExpression("x>1").build();
        RuleDefinition r2 = RuleDefinition.builder().code("R2").name("R2").conditionExpression("y>2").build();
        RuleHealthScore s1 = service.score(r1, null);
        RuleHealthScore s2 = service.score(r2, null);
        var batch = service.scoreBatch(java.util.Arrays.asList(r1, r2), Collections.emptyMap());
        assertEquals(2, batch.size());
        assertEquals(s1.getScore(), batch.get(0).getScore(), 0.01);
        assertEquals(s2.getScore(), batch.get(1).getScore(), 0.01);
    }

    @Test
    @DisplayName("空 owner 时应生成建议")
    void shouldSuggestOwnerWhenMissing() {
        RuleDefinition rule = RuleDefinition.builder()
                .code("R6").name("无 owner")
                .conditionExpression("x > 1")
                .build();
        RuleHealthScore score = service.score(rule, null);
        boolean hasOwnerSuggestion = score.getSuggestions().stream()
                .anyMatch(s -> s.contains("Owner"));
        assertTrue(hasOwnerSuggestion, "无 owner 时应给出补充建议");
    }
}
