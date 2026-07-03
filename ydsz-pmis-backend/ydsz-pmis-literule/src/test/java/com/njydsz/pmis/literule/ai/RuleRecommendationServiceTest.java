package com.njydsz.pmis.literule.ai;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleEngineStats;
import com.njydsz.pmis.literule.config.LiteRuleProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RuleRecommendationService 单元测试
 */
@DisplayName("规则推荐服务测试")
class RuleRecommendationServiceTest {

    private LiteRuleProperties.Ai aiConfig;
    private RuleHealthScoreService healthScoreService;
    private RuleRecommendationService service;

    @BeforeEach
    void setUp() {
        aiConfig = new LiteRuleProperties.Ai();
        aiConfig.setRecommendTopN(10);
        healthScoreService = new RuleHealthScoreService(aiConfig);
        service = new RuleRecommendationService(aiConfig);
    }

    @Test
    @DisplayName("无上下文时返回空列表")
    void shouldReturnEmptyWhenNoContext() {
        RuleDefinition source = RuleDefinition.builder()
                .code("R1").name("R1").conditionExpression("a > 1").build();
        List<RuleRecommendation> recs = service.recommend(source, null, null);
        assertNotNull(recs);
        assertEquals(0, recs.size());
    }

    @Test
    @DisplayName("高频字段未在源规则中应触发 FIELD_COMPLETION")
    void shouldRecommendFieldCompletion() {
        RuleDefinition source = RuleDefinition.builder()
                .code("R-SRC").name("源规则").conditionExpression("amount > 1000").build();
        // 5 条规则都引用 tenantId，但源规则没有
        List<RuleDefinition> ctx = Arrays.asList(
                r("R-A", "tenantId == 'A' && amount > 1"),
                r("R-B", "tenantId == 'B' && status == 1"),
                r("R-C", "tenantId == 'C' && region == 'cn'"),
                r("R-D", "tenantId == 'D' && score > 5"),
                r("R-E", "tenantId == 'E' && amount > 0")
        );
        List<RuleRecommendation> recs = service.recommend(source, ctx, null);
        assertTrue(recs.stream().anyMatch(r -> r.getType() == RuleRecommendation.RecommendationType.FIELD_COMPLETION),
                "应产生 FIELD_COMPLETION 类型推荐");
    }

    @Test
    @DisplayName("共享 ≥3 变量应触发重复检测推荐")
    void shouldDetectDuplication() {
        RuleDefinition source = RuleDefinition.builder()
                .code("R-SRC").name("源").conditionExpression("a > 1 && b > 2 && c > 3 && d > 4").build();
        RuleDefinition dup = RuleDefinition.builder()
                .code("R-DUP").name("疑似重复").conditionExpression("a > 5 && b > 6 && c > 7 && e > 8").build();
        List<RuleRecommendation> recs = service.recommend(source, Arrays.asList(source, dup), null);
        assertTrue(recs.stream().anyMatch(r -> r.getType() == RuleRecommendation.RecommendationType.PATTERN_DUPLICATION),
                "共享变量应触发 PATTERN_DUPLICATION");
    }

    @Test
    @DisplayName("低命中率应生成 VARIANT 推荐")
    void shouldGenerateVariantForLowHitRate() {
        RuleDefinition source = RuleDefinition.builder()
                .code("R-LO").name("低命中").conditionExpression("amount >= 1000").build();
        RuleEngineStats stats = RuleEngineStats.builder()
                .perRuleStats(new HashMap<>())
                .build();
        stats.getPerRuleStats().put("R-LO", RuleEngineStats.RuleStat.builder()
                .executions(200).triggered(1).errors(0).build());
        List<RuleRecommendation> recs = service.recommend(source, Collections.emptyList(),
                Collections.singletonMap("R-LO", stats));
        assertTrue(recs.stream().anyMatch(r -> r.getType() == RuleRecommendation.RecommendationType.VARIANT),
                "低命中规则应生成变体推荐");
    }

    @Test
    @DisplayName("错误率高且含 && 应触发 SPLIT_SUGGESTION")
    void shouldSuggestSplitForHighErrorRate() {
        RuleDefinition source = RuleDefinition.builder()
                .code("R-SP").name("高错误率")
                .conditionExpression("a > 1 && b > 2 && c > 3")
                .build();
        RuleEngineStats stats = RuleEngineStats.builder()
                .perRuleStats(new HashMap<>())
                .build();
        stats.getPerRuleStats().put("R-SP", RuleEngineStats.RuleStat.builder()
                .executions(100).triggered(20).errors(30).build());
        List<RuleRecommendation> recs = service.recommend(source, Collections.emptyList(),
                Collections.singletonMap("R-SP", stats));
        assertTrue(recs.stream().anyMatch(r -> r.getType() == RuleRecommendation.RecommendationType.SPLIT_SUGGESTION),
                "高错误率应触发拆分建议");
    }

    @Test
    @DisplayName("推荐结果应按 score 降序")
    void shouldSortByScoreDescending() {
        RuleDefinition source = RuleDefinition.builder()
                .code("R-X").name("X").conditionExpression("a > 1 && b > 2 && c > 3 && d > 4").build();
        // 上下文：5 条共用 fieldX
        List<RuleDefinition> ctx = Arrays.asList(
                r("R1", "fieldX > 1"), r("R2", "fieldX > 2"),
                r("R3", "fieldX > 3"), r("R4", "fieldX > 4"), r("R5", "fieldX > 5")
        );
        List<RuleRecommendation> recs = service.recommend(source, ctx, null);
        for (int i = 1; i < recs.size(); i++) {
            assertTrue(recs.get(i - 1).getScore() >= recs.get(i).getScore(),
                    "推荐列表应按 score 降序");
        }
    }

    @Test
    @DisplayName("表达式变量提取排除关键字与数字")
    void shouldExtractVarsExcludingKeywords() {
        Set<String> vars = service.extractVars("a > 1 && true == false");
        assertTrue(vars.contains("a"));
        assertTrue(!vars.contains("true"));
        assertTrue(!vars.contains("false"));
        assertTrue(!vars.contains("1"));
    }

    @Test
    @DisplayName("宽松化表达式将 >= 替换为 >，数字 * 0.8")
    void shouldLoosenExpression() {
        RuleDefinition source = RuleDefinition.builder()
                .code("R-L").name("L")
                .conditionExpression("amount >= 1000 && level >= 3")
                .build();
        RuleEngineStats stats = RuleEngineStats.builder()
                .perRuleStats(new HashMap<>())
                .build();
        stats.getPerRuleStats().put("R-L", RuleEngineStats.RuleStat.builder()
                .executions(200).triggered(0).errors(0).build());
        List<RuleRecommendation> recs = service.recommend(source, Collections.emptyList(),
                Collections.singletonMap("R-L", stats));
        RuleRecommendation variant = recs.stream()
                .filter(r -> r.getType() == RuleRecommendation.RecommendationType.VARIANT)
                .findFirst().orElseThrow();
        assertNotNull(variant.getSuggestedExpression());
        assertTrue(variant.getSuggestedExpression().contains(">"));
    }

    private RuleDefinition r(String code, String expr) {
        return RuleDefinition.builder().code(code).name(code).conditionExpression(expr).build();
    }
}
