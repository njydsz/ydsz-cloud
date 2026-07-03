package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.expr.AviatorExpressionEvaluator;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 规则灰度路由器单元测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>canaryRatio = 0 时直接拒绝路由</li>
 *   <li>条件过滤：canaryConditions 全部满足才进入分桶</li>
 *   <li>比例分桶：ratio=1.0 全量灰度；ratio=0.0 不灰度</li>
 *   <li>buildCanaryDefinition 覆盖主版本表达式</li>
 *   <li>evaluateCanary 标记 canary=true / canaryBucket=CANARY</li>
 *   <li>分桶计数正确性</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
class RuleCanaryRouterTest {

    private RuleCanaryRouter router;

    @BeforeEach
    void setUp() {
        ExpressionEvaluator evaluator = new AviatorExpressionEvaluator(false);
        router = new RuleCanaryRouter(evaluator);
    }

    @Test
    void shouldRejectWhenCanaryRatioZero() {
        RuleDefinition def = RuleDefinition.builder()
                .code("R1").name("R1").canaryRatio(0.0)
                .canaryConditionExpression("amount > 100")
                .build();
        RuleContext ctx = RuleContext.of(new HashMap<>());

        assertFalse(router.shouldRouteToCanary(def, ctx));
    }

    @Test
    void shouldRejectWhenConditionsNotMet() {
        RuleDefinition def = RuleDefinition.builder()
                .code("R2").name("R2").canaryRatio(1.0)
                .canaryConditions(List.of("tenantId == 'T001'"))
                .canaryConditionExpression("amount > 100")
                .build();

        Map<String, Object> facts = new HashMap<>();
        facts.put("tenantId", "T002");  // 不满足条件
        facts.put("amount", 500);
        RuleContext ctx = RuleContext.of(facts);

        assertFalse(router.shouldRouteToCanary(def, ctx));
    }

    @Test
    void shouldRouteWhenRatioIsOneHundredPercent() {
        RuleDefinition def = RuleDefinition.builder()
                .code("R3").name("R3").canaryRatio(1.0)
                .canaryConditionExpression("amount > 100")
                .build();

        Map<String, Object> facts = new HashMap<>();
        facts.put("amount", 500);
        RuleContext ctx = RuleContext.of(facts);

        assertTrue(router.shouldRouteToCanary(def, ctx));
    }

    @Test
    void shouldBuildCanaryDefinitionWithOverride() {
        RuleDefinition original = RuleDefinition.builder()
                .code("R4")
                .name("原规则")
                .category("EVM")
                .conditionExpression("amount >= 1000")
                .severityExpression("'RED'")
                .canaryConditionExpression("amount >= 800")
                .canarySeverityExpression("'YELLOW'")
                .build();

        RuleDefinition canary = router.buildCanaryDefinition(original);

        assertEquals("R4", canary.getCode());
        assertTrue(canary.getName().contains("[CANARY]"));
        assertEquals("amount >= 800", canary.getConditionExpression());
        assertEquals("'YELLOW'", canary.getSeverityExpression());
        assertEquals("PUBLISHED", canary.getStatus());
    }

    @Test
    void shouldEvaluateCanaryAndMarkResult() {
        RuleDefinition def = RuleDefinition.builder()
                .code("R5")
                .name("测试规则")
                .conditionExpression("amount >= 1000")
                .severityExpression("'RED'")
                .defaultSeverity(RuleSeverity.YELLOW)
                .canaryConditionExpression("amount >= 500")
                .canarySeverityExpression("'YELLOW'")
                .canaryRatio(1.0)
                .build();

        Map<String, Object> facts = new HashMap<>();
        facts.put("amount", 800);
        RuleContext ctx = RuleContext.of(facts);

        RuleResult result = router.evaluateCanary(def, ctx);

        assertNotNull(result);
        assertTrue(result.isTriggered());           // 候选版本条件 800 >= 500 满足
        assertTrue(result.isCanary());              // 灰度标记已打上
        assertEquals("CANARY", result.getCanaryBucket());
        assertEquals(RuleSeverity.YELLOW, result.getSeverity());
    }

    @Test
    void shouldNotTriggerCanaryWhenExpressionFalse() {
        RuleDefinition def = RuleDefinition.builder()
                .code("R6")
                .name("测试规则")
                .conditionExpression("amount >= 1000")
                .canaryConditionExpression("amount >= 5000")
                .canaryRatio(1.0)
                .build();

        Map<String, Object> facts = new HashMap<>();
        facts.put("amount", 800);
        RuleContext ctx = RuleContext.of(facts);

        RuleResult result = router.evaluateCanary(def, ctx);

        assertNotNull(result);
        assertFalse(result.isTriggered());
        assertTrue(result.isCanary());
    }

    @Test
    void shouldRecordBucketCountsCorrectly() {
        router.recordBucket("R7", true);
        router.recordBucket("R7", true);
        router.recordBucket("R7", false);
        router.recordBucket("R8", true);

        Map<String, long[]> stats = router.getCanaryBucketStats();
        long[] r7Counts = stats.get("R7");
        assertNotNull(r7Counts);
        assertEquals(1, r7Counts[0]);   // PRIMARY
        assertEquals(2, r7Counts[1]);   // CANARY

        long[] r8Counts = stats.get("R8");
        assertNotNull(r8Counts);
        assertEquals(0, r8Counts[0]);
        assertEquals(1, r8Counts[1]);
    }

    @Test
    void shouldResetStats() {
        router.recordBucket("R9", true);
        router.resetStats();
        assertTrue(router.getCanaryBucketStats().isEmpty());
    }

    @Test
    void shouldFallbackToMainWhenCanaryExpressionNull() {
        // canaryConditionExpression 为 null 时，buildCanaryDefinition 应回退到主表达式
        RuleDefinition def = RuleDefinition.builder()
                .code("R10")
                .name("测试")
                .conditionExpression("amount >= 1000")
                .severityExpression("'RED'")
                .canaryRatio(1.0)
                // 不设 canaryConditionExpression
                .canarySeverityExpression("'YELLOW'")
                .build();

        RuleDefinition canary = router.buildCanaryDefinition(def);
        assertEquals("amount >= 1000", canary.getConditionExpression());
        assertEquals("'YELLOW'", canary.getSeverityExpression());
    }

    @Test
    void shouldRouteStablyForSameTraceId() {
        // 同一 traceId 多次调用 shouldRouteToCanary 结果应稳定
        RuleDefinition def = RuleDefinition.builder()
                .code("R11")
                .name("R11")
                .canaryRatio(0.3)
                .canaryConditionExpression("amount > 100")
                .build();

        Map<String, Object> facts = new HashMap<>();
        facts.put("amount", 500);
        RuleContext ctx = RuleContext.of(facts, "TEST", "JUNIT", "stable-trace-001");

        boolean first = router.shouldRouteToCanary(def, ctx);
        for (int i = 0; i < 10; i++) {
            assertEquals(first, router.shouldRouteToCanary(def, ctx),
                    "同一 traceId 分桶结果应稳定");
        }
    }
}
