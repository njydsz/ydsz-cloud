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

/**
 * {@link RuleRecommendationService} 单元测试。
 *
 * <p>覆盖规则推荐能力，包括字段补全、重复检测、变体衍生、拆分建议四种推荐类型，
 * 含边界条件与异常场景。该服务无外部依赖（仅依赖配置对象），无需 Mockito。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("规则推荐服务测试")
class RuleRecommendationServiceTest {

    private LiteRuleProperties.Ai aiConfig;
    private RuleRecommendationService service;

    @BeforeEach
    void setUp() {
        aiConfig = new LiteRuleProperties.Ai();
        aiConfig.setRecommendTopN(10);
        service = new RuleRecommendationService(aiConfig);
    }

    private RuleDefinition buildRule(String code, String condition) {
        return RuleDefinition.builder()
                .code(code)
                .name("规则-" + code)
                .conditionExpression(condition)
                .build();
    }

    private RuleEngineStats buildStats(String ruleCode, long executions, long triggered, long errors) {
        RuleEngineStats.RuleStat stat = RuleEngineStats.RuleStat.builder()
                .executions(executions).triggered(triggered).errors(errors).build();
        return RuleEngineStats.builder()
                .totalEvaluations(executions)
                .totalTriggered(triggered)
                .totalErrors(errors)
                .perRuleStats(Map.of(ruleCode, stat))
                .build();
    }

    // ==================== recommend ====================

    @Nested
    @DisplayName("主入口：recommend")
    class RecommendTest {

        @Test
        @DisplayName("边界条件：source 为 null 返回空列表")
        void shouldReturnEmptyWhenSourceNull() {
            List<RuleRecommendation> result = service.recommend(null, List.of(), Map.of());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("正常场景：无上下文规则时仅返回变体与拆分建议")
        void shouldReturnOnlyVariantAndSplitWhenNoContext() {
            RuleDefinition source = buildRule("R001", "amount > 1000 && score < 800");
            RuleEngineStats stats = buildStats("R001", 200, 0, 50);
            // evaluations=200, triggered=0 → hitRate=0 < 1% → 生成变体建议
            // errorRate=50/200=25% >= 10% && 表达式含 && → 生成拆分建议

            List<RuleRecommendation> result = service.recommend(source, List.of(),
                    Map.of("R001", stats));

            // 应同时包含变体建议和拆分建议
            assertThat(result).isNotEmpty();
            assertThat(result).anyMatch(r -> r.getType() == RuleRecommendation.RecommendationType.VARIANT);
            assertThat(result).anyMatch(r -> r.getType() == RuleRecommendation.RecommendationType.SPLIT_SUGGESTION);
        }

        @Test
        @DisplayName("正常场景：结果按 score 降序排序")
        void shouldSortByScoreDesc() {
            RuleDefinition source = buildRule("R001", "amount > 1000 && score < 800");
            RuleEngineStats stats = buildStats("R001", 200, 0, 50);

            List<RuleRecommendation> result = service.recommend(source, List.of(),
                    Map.of("R001", stats));

            for (int i = 1; i < result.size(); i++) {
                assertThat(result.get(i - 1).getScore()).isGreaterThanOrEqualTo(result.get(i).getScore());
            }
        }

        @Test
        @DisplayName("正常场景：超过 topN 时截断")
        void shouldTruncateToTopN() {
            aiConfig.setRecommendTopN(1);
            RuleDefinition source = buildRule("R001", "amount > 1000 && score < 800");
            RuleEngineStats stats = buildStats("R001", 200, 0, 50);

            List<RuleRecommendation> result = service.recommend(source, List.of(),
                    Map.of("R001", stats));

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("正常场景：stats 为 null 时不生成变体与拆分建议")
        void shouldNotGenerateVariantAndSplitWhenStatsNull() {
            RuleDefinition source = buildRule("R001", "amount > 1000");

            List<RuleRecommendation> result = service.recommend(source, List.of(), null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("正常场景：all 为 null 时视为空上下文")
        void shouldHandleNullAll() {
            RuleDefinition source = buildRule("R001", "amount > 1000");

            List<RuleRecommendation> result = service.recommend(source, null, Map.of());

            assertThat(result).isNotNull();
        }
    }

    // ==================== fieldCompletion ====================

    @Nested
    @DisplayName("字段补全：fieldCompletion")
    class FieldCompletionTest {

        @Test
        @DisplayName("边界条件：context 为空返回空列表")
        void shouldReturnEmptyWhenContextEmpty() {
            RuleDefinition source = buildRule("R001", "amount > 1000");

            List<RuleRecommendation> result = service.fieldCompletion(source, List.of());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("正常场景：高频字段未在 source 中引用时生成补全建议")
        void shouldGenerateFieldCompletionSuggestion() {
            RuleDefinition source = buildRule("R001", "amount > 1000");
            // 3 条规则都引用了 score 字段，但 source 未引用
            List<RuleDefinition> context = List.of(
                    buildRule("R002", "score > 800"),
                    buildRule("R003", "score < 500 && amount > 100"),
                    buildRule("R004", "score == 600"));

            List<RuleRecommendation> result = service.fieldCompletion(source, context);

            assertThat(result).isNotEmpty();
            assertThat(result).anyMatch(r -> r.getType() == RuleRecommendation.RecommendationType.FIELD_COMPLETION
                    && r.getSuggestedExpression().contains("score"));
        }

        @Test
        @DisplayName("正常场景：字段引用次数 < 3 时不生成建议")
        void shouldNotGenerateWhenCountLessThan3() {
            RuleDefinition source = buildRule("R001", "amount > 1000");
            List<RuleDefinition> context = List.of(
                    buildRule("R002", "score > 800"),
                    buildRule("R003", "score < 500"));

            List<RuleRecommendation> result = service.fieldCompletion(source, context);

            assertThat(result).isEmpty();
        }
    }

    // ==================== duplicationDetection ====================

    @Nested
    @DisplayName("重复检测：duplicationDetection")
    class DuplicationDetectionTest {

        @Test
        @DisplayName("边界条件：source 变量数 < 3 返回空列表")
        void shouldReturnEmptyWhenSourceVarsLessThan3() {
            RuleDefinition source = buildRule("R001", "amount > 1000");

            List<RuleRecommendation> result = service.duplicationDetection(source, List.of());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("正常场景：与其他规则共享 >=3 变量时生成重复建议")
        void shouldGenerateDuplicationSuggestion() {
            RuleDefinition source = buildRule("R001", "amount > 1000 && score < 800 && count > 5");
            RuleDefinition other = buildRule("R002", "amount < 500 && score > 900 && count == 10");

            List<RuleRecommendation> result = service.duplicationDetection(source, List.of(other));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getType()).isEqualTo(RuleRecommendation.RecommendationType.PATTERN_DUPLICATION);
            assertThat(result.get(0).getRationale()).contains("R002");
        }

        @Test
        @DisplayName("正常场景：不与自身比较")
        void shouldNotCompareWithSelf() {
            RuleDefinition source = buildRule("R001", "amount > 1000 && score < 800 && count > 5");

            List<RuleRecommendation> result = service.duplicationDetection(source, List.of(source));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("正常场景：共享变量 < 3 时不生成建议")
        void shouldNotGenerateWhenOverlapLessThan3() {
            RuleDefinition source = buildRule("R001", "amount > 1000 && score < 800 && count > 5");
            RuleDefinition other = buildRule("R002", "amount < 500 && price > 100");

            List<RuleRecommendation> result = service.duplicationDetection(source, List.of(other));

            assertThat(result).isEmpty();
        }
    }

    // ==================== variantSuggestion ====================

    @Nested
    @DisplayName("变体衍生：variantSuggestion")
    class VariantSuggestionTest {

        @Test
        @DisplayName("边界条件：stats 为 null 返回空列表")
        void shouldReturnEmptyWhenStatsNull() {
            RuleDefinition source = buildRule("R001", "amount > 1000");

            List<RuleRecommendation> result = service.variantSuggestion(source, null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("边界条件：evaluations < 100 返回空列表")
        void shouldReturnEmptyWhenEvaluationsLessThan100() {
            RuleDefinition source = buildRule("R001", "amount > 1000");
            RuleEngineStats stats = buildStats("R001", 50, 0, 0);

            List<RuleRecommendation> result = service.variantSuggestion(source, stats);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("正常场景：命中率 < 1% 时生成宽松变体建议")
        void shouldGenerateVariantWhenHitRateLow() {
            RuleDefinition source = buildRule("R001", "amount > 1000");
            // 200 次执行，0 次触发，命中率 0%
            RuleEngineStats stats = buildStats("R001", 200, 0, 0);

            List<RuleRecommendation> result = service.variantSuggestion(source, stats);

            assertThat(result).hasSize(1);
            RuleRecommendation rec = result.get(0);
            assertThat(rec.getType()).isEqualTo(RuleRecommendation.RecommendationType.VARIANT);
            assertThat(rec.getSuggestedExpression()).contains(">");
            assertThat(rec.getRationale()).contains("命中率");
        }

        @Test
        @DisplayName("正常场景：命中率 >= 1% 时不生成变体建议")
        void shouldNotGenerateWhenHitRateAboveThreshold() {
            RuleDefinition source = buildRule("R001", "amount > 1000");
            // 200 次执行，5 次触发，命中率 2.5%
            RuleEngineStats stats = buildStats("R001", 200, 5, 0);

            List<RuleRecommendation> result = service.variantSuggestion(source, stats);

            assertThat(result).isEmpty();
        }
    }

    // ==================== splitSuggestion ====================

    @Nested
    @DisplayName("拆分建议：splitSuggestion")
    class SplitSuggestionTest {

        @Test
        @DisplayName("边界条件：stats 为 null 返回空列表")
        void shouldReturnEmptyWhenStatsNull() {
            RuleDefinition source = buildRule("R001", "amount > 1000 && score < 800");

            List<RuleRecommendation> result = service.splitSuggestion(source, null);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("边界条件：evaluations < 30 返回空列表")
        void shouldReturnEmptyWhenEvaluationsLessThan30() {
            RuleDefinition source = buildRule("R001", "amount > 1000 && score < 800");
            RuleEngineStats stats = buildStats("R001", 20, 0, 10);

            List<RuleRecommendation> result = service.splitSuggestion(source, stats);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("正常场景：错误率 >= 10% 且表达式含 && 时生成拆分建议")
        void shouldGenerateSplitSuggestion() {
            RuleDefinition source = buildRule("R001", "amount > 1000 && score < 800");
            // 100 次执行，20 次错误，错误率 20%
            RuleEngineStats stats = buildStats("R001", 100, 10, 20);

            List<RuleRecommendation> result = service.splitSuggestion(source, stats);

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(r -> r.getType() == RuleRecommendation.RecommendationType.SPLIT_SUGGESTION);
            assertThat(result.get(0).getSuggestedExpression()).isEqualTo("amount > 1000");
            assertThat(result.get(1).getSuggestedExpression()).isEqualTo("score < 800");
        }

        @Test
        @DisplayName("正常场景：错误率 < 10% 时不生成拆分建议")
        void shouldNotGenerateWhenErrorRateLow() {
            RuleDefinition source = buildRule("R001", "amount > 1000 && score < 800");
            RuleEngineStats stats = buildStats("R001", 100, 10, 5);

            List<RuleRecommendation> result = service.splitSuggestion(source, stats);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("正常场景：表达式无 && 时不生成拆分建议")
        void shouldNotGenerateWhenNoAndOperator() {
            RuleDefinition source = buildRule("R001", "amount > 1000");
            RuleEngineStats stats = buildStats("R001", 100, 10, 50);

            List<RuleRecommendation> result = service.splitSuggestion(source, stats);

            assertThat(result).isEmpty();
        }
    }
}
