package com.njydsz.pmis.literule.ai;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleEngineStats;
import com.njydsz.pmis.literule.config.LiteRuleProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RuleHealthScoreService} 单元测试。
 *
 * <p>覆盖规则健康度评分能力，包括命中率/错误率/复杂度/覆盖率四个分项评分、
 * 加权总分计算、健康度等级判定、改进建议生成，含边界条件与异常场景。
 *
 * <p>该服务无外部依赖（仅依赖配置对象），无需 Mockito。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("规则健康度评分服务测试")
class RuleHealthScoreServiceTest {

    private LiteRuleProperties.Ai aiConfig;
    private RuleHealthScoreService service;

    @BeforeEach
    void setUp() {
        aiConfig = new LiteRuleProperties.Ai();
        // 权重：命中率 0.3 / 错误率 0.3 / 复杂度 0.2 / 覆盖率 0.2
        service = new RuleHealthScoreService(aiConfig);
    }

    private RuleDefinition buildRule(String code, String condition, String owner) {
        return RuleDefinition.builder()
                .code(code)
                .name("规则-" + code)
                .conditionExpression(condition)
                .owner(owner)
                .build();
    }

    private RuleEngineStats buildStats(long total, long triggered, long errors) {
        return RuleEngineStats.builder()
                .totalEvaluations(total)
                .totalTriggered(triggered)
                .totalErrors(errors)
                .perRuleStats(Map.of())
                .build();
    }

    private RuleEngineStats buildStatsWithPerRule(String ruleCode, long executions, long triggered, long errors) {
        RuleEngineStats.RuleStat stat = RuleEngineStats.RuleStat.builder()
                .executions(executions).triggered(triggered).errors(errors).build();
        return RuleEngineStats.builder()
                .totalEvaluations(executions)
                .totalTriggered(triggered)
                .totalErrors(errors)
                .perRuleStats(Map.of(ruleCode, stat))
                .build();
    }

    // ==================== score ====================

    @Nested
    @DisplayName("单条规则评分：score")
    class ScoreTest {

        @Test
        @DisplayName("异常场景：rule 为 null 抛异常")
        void shouldThrowWhenRuleNull() {
            assertThatThrownBy(() -> service.score(null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("rule");
        }

        @Test
        @DisplayName("正常场景：stats 为 null 时各分项按默认值计算")
        void shouldScoreWithNullStats() {
            RuleDefinition rule = buildRule("R001", "amount > 1000", "owner1");

            RuleHealthScore score = service.score(rule, null);

            assertThat(score.getRuleCode()).isEqualTo("R001");
            assertThat(score.getRuleName()).isEqualTo("规则-R001");
            assertThat(score.getTotalEvaluations()).isEqualTo(0);
            assertThat(score.getHitRate()).isEqualTo(0.0);
            assertThat(score.getErrorRate()).isEqualTo(0.0);
            // 样本 < 30 → hitRateScore = 100
            assertThat(score.getHitRateScore()).isEqualTo(100.0);
            // errorRate = 0 → errorRateScore = 100
            assertThat(score.getErrorRateScore()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("正常场景：样本不足 30 时命中率分项为 100")
        void shouldReturn100HitRateScoreWhenSampleInsufficient() {
            RuleDefinition rule = buildRule("R001", "amount > 1000", "owner1");
            RuleEngineStats stats = buildStats(10, 5, 0);

            RuleHealthScore score = service.score(rule, stats);

            assertThat(score.getHitRateScore()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("正常场景：命中率在 5%~30% 之间得满分")
        void shouldReturn100HitRateScoreWhenRateInRange() {
            RuleDefinition rule = buildRule("R001", "amount > 1000", "owner1");
            // 50 次执行，10 次触发，命中率 20%
            RuleEngineStats stats = buildStatsWithPerRule("R001", 50, 10, 0);

            RuleHealthScore score = service.score(rule, stats);

            assertThat(score.getHitRate()).isEqualTo(0.2);
            assertThat(score.getHitRateScore()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("正常场景：错误率为 0 时错误率分项为 100")
        void shouldReturn100ErrorRateScoreWhenNoErrors() {
            RuleDefinition rule = buildRule("R001", "amount > 1000", "owner1");
            RuleEngineStats stats = buildStatsWithPerRule("R001", 100, 10, 0);

            RuleHealthScore score = service.score(rule, stats);

            assertThat(score.getErrorRate()).isEqualTo(0.0);
            assertThat(score.getErrorRateScore()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("正常场景：错误率 >= 50% 时错误率分项为 0")
        void shouldReturn0ErrorRateScoreWhenErrorRateHigh() {
            RuleDefinition rule = buildRule("R001", "amount > 1000", "owner1");
            RuleEngineStats stats = buildStatsWithPerRule("R001", 100, 10, 60);

            RuleHealthScore score = service.score(rule, stats);

            assertThat(score.getErrorRate()).isEqualTo(0.6);
            assertThat(score.getErrorRateScore()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("正常场景：表达式 token 数远低于阈值时复杂度分项为 100")
        void shouldReturn100ComplexityScoreWhenTokenLow() {
            RuleDefinition rule = buildRule("R001", "amount > 1000", "owner1");
            // 表达式 "amount > 1000" 仅 3 个 token，远低于阈值 80 的 30%（24）
            RuleHealthScore score = service.score(rule, null);

            assertThat(score.getExpressionTokenCount()).isEqualTo(3);
            assertThat(score.getComplexityScore()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("正常场景：表达式 token 数超过阈值时复杂度分项为 0")
        void shouldReturn0ComplexityScoreWhenTokenExceedsThreshold() {
            // 构造超过阈值的表达式（80+ token）
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                if (i > 0) sb.append(" && ");
                sb.append("var").append(i).append(" > ").append(i);
            }
            RuleDefinition rule = buildRule("R001", sb.toString(), "owner1");

            RuleHealthScore score = service.score(rule, null);

            assertThat(score.getComplexityScore()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("正常场景：阈值为 0 时复杂度分项为 100")
        void shouldReturn100ComplexityScoreWhenThresholdZero() {
            aiConfig.setHealthComplexityThreshold(0);
            RuleDefinition rule = buildRule("R001", "amount > 1000", "owner1");

            RuleHealthScore score = service.score(rule, null);

            assertThat(score.getComplexityScore()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("正常场景：无声明变量时覆盖率为 1.0")
        void shouldReturn1CoverageWhenNoDeclaredVariables() {
            RuleDefinition rule = buildRule("R001", "amount > 1000", "owner1");

            RuleHealthScore score = service.score(rule, null);

            assertThat(score.getVariableCoverage()).isEqualTo(1.0);
            assertThat(score.getCoverageScore()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("正常场景：总分在 0~100 之间")
        void shouldReturnScoreBetween0And100() {
            RuleDefinition rule = buildRule("R001", "amount > 1000", "owner1");
            RuleEngineStats stats = buildStatsWithPerRule("R001", 100, 10, 5);

            RuleHealthScore score = service.score(rule, stats);

            assertThat(score.getScore()).isBetween(0.0, 100.0);
            assertThat(score.getLevel()).isNotNull();
        }

        @Test
        @DisplayName("正常场景：权重全为 0 时总分为 0")
        void shouldReturn0ScoreWhenAllWeightsZero() {
            aiConfig.setHealthHitRateWeight(0);
            aiConfig.setHealthErrorRateWeight(0);
            aiConfig.setHealthComplexityWeight(0);
            aiConfig.setHealthCoverageWeight(0);
            RuleDefinition rule = buildRule("R001", "amount > 1000", "owner1");

            RuleHealthScore score = service.score(rule, null);

            assertThat(score.getScore()).isEqualTo(0.0);
        }
    }

    // ==================== 改进建议 ====================

    @Nested
    @DisplayName("改进建议生成")
    class SuggestionTest {

        @Test
        @DisplayName("正常场景：错误率 >= 20% 添加错误率建议")
        void shouldAddErrorRateSuggestion() {
            RuleDefinition rule = buildRule("R001", "amount > 1000", "owner1");
            RuleEngineStats stats = buildStatsWithPerRule("R001", 100, 10, 25);

            RuleHealthScore score = service.score(rule, stats);

            assertThat(score.getSuggestions()).anyMatch(s -> s.contains("错误率"));
        }

        @Test
        @DisplayName("正常场景：样本 >= 30 且命中率 < 1% 添加命中率过低建议")
        void shouldAddLowHitRateSuggestion() {
            RuleDefinition rule = buildRule("R001", "amount > 1000", "owner1");
            // 100 次执行，0 次触发
            RuleEngineStats stats = buildStatsWithPerRule("R001", 100, 0, 0);

            RuleHealthScore score = service.score(rule, stats);

            assertThat(score.getSuggestions()).anyMatch(s -> s.contains("命中率长期低于 1%"));
        }

        @Test
        @DisplayName("正常场景：样本 >= 30 且命中率 > 60% 添加命中率过高建议")
        void shouldAddHighHitRateSuggestion() {
            RuleDefinition rule = buildRule("R001", "amount > 1000", "owner1");
            // 100 次执行，70 次触发
            RuleEngineStats stats = buildStatsWithPerRule("R001", 100, 70, 0);

            RuleHealthScore score = service.score(rule, stats);

            assertThat(score.getSuggestions()).anyMatch(s -> s.contains("命中率超过 60%"));
        }

        @Test
        @DisplayName("正常场景：表达式 token 数超过阈值添加复杂度建议")
        void shouldAddComplexitySuggestion() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                if (i > 0) sb.append(" && ");
                sb.append("var").append(i).append(" > ").append(i);
            }
            RuleDefinition rule = buildRule("R001", sb.toString(), "owner1");

            RuleHealthScore score = service.score(rule, null);

            assertThat(score.getSuggestions()).anyMatch(s -> s.contains("表达式偏长"));
        }

        @Test
        @DisplayName("正常场景：owner 为 null 添加责任人建议")
        void shouldAddOwnerSuggestionWhenOwnerNull() {
            RuleDefinition rule = buildRule("R001", "amount > 1000", null);

            RuleHealthScore score = service.score(rule, null);

            assertThat(score.getSuggestions()).anyMatch(s -> s.contains("责任人"));
        }

        @Test
        @DisplayName("正常场景：owner 为空字符串添加责任人建议")
        void shouldAddOwnerSuggestionWhenOwnerEmpty() {
            RuleDefinition rule = buildRule("R001", "amount > 1000", "");

            RuleHealthScore score = service.score(rule, null);

            assertThat(score.getSuggestions()).anyMatch(s -> s.contains("责任人"));
        }

        @Test
        @DisplayName("正常场景：所有维度健康时添加良好建议")
        void shouldAddGoodSuggestionWhenHealthy() {
            RuleDefinition rule = buildRule("R001", "amount > 1000", "owner1");

            RuleHealthScore score = service.score(rule, null);

            assertThat(score.getSuggestions()).anyMatch(s -> s.contains("健康度良好"));
        }
    }

    // ==================== scoreBatch ====================

    @Nested
    @DisplayName("批量评分：scoreBatch")
    class ScoreBatchTest {

        @Test
        @DisplayName("边界条件：null 入参返回空列表")
        void shouldReturnEmptyWhenRulesNull() {
            List<RuleHealthScore> result = service.scoreBatch(null, null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("边界条件：空列表返回空列表")
        void shouldReturnEmptyWhenRulesEmpty() {
            List<RuleHealthScore> result = service.scoreBatch(List.of(), null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("正常场景：批量评分多条规则")
        void shouldScoreMultipleRules() {
            RuleDefinition rule1 = buildRule("R001", "amount > 1000", "owner1");
            RuleDefinition rule2 = buildRule("R002", "score < 800", "owner2");
            Map<String, RuleEngineStats> stats = Map.of(
                    "R001", buildStatsWithPerRule("R001", 100, 10, 0),
                    "R002", buildStatsWithPerRule("R002", 50, 5, 0));

            List<RuleHealthScore> result = service.scoreBatch(List.of(rule1, rule2), stats);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getRuleCode()).isEqualTo("R001");
            assertThat(result.get(1).getRuleCode()).isEqualTo("R002");
        }

        @Test
        @DisplayName("正常场景：stats 为 null 时各规则按默认值评分")
        void shouldScoreWithNullStatsMap() {
            RuleDefinition rule1 = buildRule("R001", "amount > 1000", "owner1");

            List<RuleHealthScore> result = service.scoreBatch(List.of(rule1), null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTotalEvaluations()).isEqualTo(0);
        }

        @Test
        @DisplayName("正常场景：stats 中无对应规则编码时按默认值评分")
        void shouldScoreWithMissingStatsEntry() {
            RuleDefinition rule1 = buildRule("R001", "amount > 1000", "owner1");
            Map<String, RuleEngineStats> stats = Map.of("R999", buildStats(100, 10, 0));

            List<RuleHealthScore> result = service.scoreBatch(List.of(rule1), stats);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getTotalEvaluations()).isEqualTo(0);
        }
    }
}
