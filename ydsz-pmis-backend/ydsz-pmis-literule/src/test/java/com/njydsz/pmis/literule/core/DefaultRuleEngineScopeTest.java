package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleEngineStats;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.impl.StaticRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultRuleEngine 场景过滤与统计测试
 *
 * <p>覆盖 P0-2（scenario/scope 过滤）和 P0-3（统计开关消费）。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@DisplayName("DefaultRuleEngine 场景过滤与统计测试")
class DefaultRuleEngineScopeTest {

    private DefaultRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DefaultRuleEngine();
    }

    // ==================== 场景过滤 ====================

    @Nested
    @DisplayName("场景/作用域过滤")
    class ScopeFilteringTest {

        @Test
        @DisplayName("DEFAULT 场景下评估全部规则（含无 scope 和有 scope 的）")
        void shouldEvaluateAllRulesInDefaultScenario() {
            StaticRule rule1 = new StaticRule("R1", "规则1", "EVM", ctx ->
                    RuleResult.triggered("R1", "规则1", "EVM", RuleSeverity.YELLOW, "", ""));
            StaticRule rule2 = new StaticRule("R2", "规则2", "COST", 100, "RESOURCE_POOL", ctx ->
                    RuleResult.triggered("R2", "规则2", "COST", RuleSeverity.RED, "", ""));

            engine.register(rule1);
            engine.register(rule2);

            // DEFAULT 场景（scenario=null 默认为 DEFAULT）
            RuleContext context = RuleContext.of(new HashMap<>());
            List<RuleResult> results = engine.evaluate(context);

            // DEFAULT 场景下评估全部规则
            assertEquals(2, results.size());
        }

        @Test
        @DisplayName("非 DEFAULT 场景下仅评估 scope 匹配的规则")
        void shouldFilterByScenario() {
            AtomicInteger r1Count = new AtomicInteger(0);
            AtomicInteger r2Count = new AtomicInteger(0);
            AtomicInteger r3Count = new AtomicInteger(0);

            StaticRule rule1 = new StaticRule("R1", "通用规则", "EVM", 100, null, ctx -> {
                r1Count.incrementAndGet();
                return RuleResult.triggered("R1", "通用规则", "EVM", RuleSeverity.YELLOW, "", "");
            });
            StaticRule rule2 = new StaticRule("R2", "资源池规则", "COST", 100, "RESOURCE_POOL", ctx -> {
                r2Count.incrementAndGet();
                return RuleResult.triggered("R2", "资源池规则", "COST", RuleSeverity.RED, "", "");
            });
            StaticRule rule3 = new StaticRule("R3", "预算规则", "BUDGET", 100, "BUDGET_CHECK", ctx -> {
                r3Count.incrementAndGet();
                return RuleResult.triggered("R3", "预算规则", "BUDGET", RuleSeverity.INFO, "", "");
            });

            engine.register(rule1);
            engine.register(rule2);
            engine.register(rule3);

            // RESOURCE_POOL 场景：评估 R1（scope=null 适用全部）和 R2（scope 匹配），跳过 R3
            Map<String, Object> facts = new HashMap<>();
            RuleContext context = RuleContext.of(facts, "RESOURCE_POOL", "TEST");
            List<RuleResult> results = engine.evaluate(context);

            assertEquals(2, results.size());
            assertEquals(1, r1Count.get());
            assertEquals(1, r2Count.get());
            assertEquals(0, r3Count.get());
        }

        @Test
        @DisplayName("scope 为 ALL 的规则在所有场景下都评估")
        void shouldEvaluateAllScopedRules() {
            AtomicInteger count = new AtomicInteger(0);
            StaticRule rule = new StaticRule("R1", "通用规则", "EVM", 100, "ALL", ctx -> {
                count.incrementAndGet();
                return RuleResult.triggered("R1", "通用规则", "EVM", RuleSeverity.YELLOW, "", "");
            });

            engine.register(rule);

            // 任意场景都应评估
            RuleContext context1 = RuleContext.of(new HashMap<>(), "RESOURCE_POOL", "TEST");
            engine.evaluate(context1);
            RuleContext context2 = RuleContext.of(new HashMap<>(), "BUDGET_CHECK", "TEST");
            engine.evaluate(context2);

            assertEquals(2, count.get());
        }

        @Test
        @DisplayName("scope 大小写不敏感匹配")
        void shouldMatchCaseInsensitive() {
            AtomicInteger count = new AtomicInteger(0);
            StaticRule rule = new StaticRule("R1", "规则", "EVM", 100, "resource_pool", ctx -> {
                count.incrementAndGet();
                return RuleResult.triggered("R1", "规则", "EVM", RuleSeverity.YELLOW, "", "");
            });

            engine.register(rule);

            // scenario 使用大写
            RuleContext context = RuleContext.of(new HashMap<>(), "RESOURCE_POOL", "TEST");
            engine.evaluate(context);

            assertEquals(1, count.get());
        }
    }

    // ==================== 统计开关 ====================

    @Nested
    @DisplayName("统计开关")
    class StatsEnabledTest {

        @Test
        @DisplayName("statsEnabled=false 时不记录统计")
        void shouldNotRecordStatsWhenDisabled() {
            engine.setStatsEnabled(false);

            StaticRule rule = new StaticRule("R1", "规则", "EVM", ctx ->
                    RuleResult.triggered("R1", "规则", "EVM", RuleSeverity.YELLOW, "", ""));
            engine.register(rule);

            engine.evaluate(RuleContext.of(new HashMap<>()));

            RuleEngineStats stats = engine.getStats();
            assertEquals(0, stats.getTotalEvaluations());
            assertEquals(0, stats.getTotalTriggered());
        }

        @Test
        @DisplayName("statsEnabled=true 时正常记录统计")
        void shouldRecordStatsWhenEnabled() {
            engine.setStatsEnabled(true);

            StaticRule rule = new StaticRule("R1", "规则", "EVM", ctx ->
                    RuleResult.triggered("R1", "规则", "EVM", RuleSeverity.YELLOW, "", ""));
            engine.register(rule);

            engine.evaluate(RuleContext.of(new HashMap<>()));

            RuleEngineStats stats = engine.getStats();
            assertEquals(1, stats.getTotalEvaluations());
            assertEquals(1, stats.getTotalTriggered());
        }

        @Test
        @DisplayName("asStatsRecorder 返回的记录器可被编排层使用")
        void shouldExposeStatsRecorder() {
            assertNotNull(engine.asStatsRecorder());

            engine.asStatsRecorder().record("TEST_RULE", true, false, 5L);

            RuleEngineStats stats = engine.getStats();
            assertEquals(1, stats.getTotalEvaluations());
            assertEquals(1, stats.getTotalTriggered());
            assertNotNull(stats.getPerRuleStats().get("TEST_RULE"));
            assertEquals(1, stats.getPerRuleStats().get("TEST_RULE").getExecutions());
        }
    }
}
