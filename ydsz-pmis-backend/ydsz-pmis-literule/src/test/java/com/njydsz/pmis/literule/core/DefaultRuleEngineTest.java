package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleEngineStats;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.expr.AviatorExpressionEvaluator;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.impl.ExpressionRule;
import com.njydsz.pmis.literule.impl.StaticRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultRuleEngine 单元测试
 *
 * @author ydsz-pmis-team
 */
@DisplayName("DefaultRuleEngine 规则引擎测试")
class DefaultRuleEngineTest {

    private DefaultRuleEngine engine;
    private ExpressionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        engine = new DefaultRuleEngine();
        evaluator = new AviatorExpressionEvaluator();
    }

    @Test
    @DisplayName("注册并评估静态规则")
    void testStaticRuleRegisterAndEvaluate() {
        engine.register(new StaticRule("R001", "测试规则1", "TEST", ctx ->
                RuleResult.triggered("R001", "测试规则1", "TEST",
                        RuleSeverity.RED, "触发", "描述")));

        List<RuleResult> results = engine.evaluate(RuleContext.of(Map.of()));
        assertEquals(1, results.size());
        assertTrue(results.get(0).isTriggered());
        assertEquals(RuleSeverity.RED, results.get(0).getSeverity());
    }

    @Test
    @DisplayName("结果按严重度倒序排列（RED → YELLOW → INFO）")
    void testResultSortedBySeverityDesc() {
        engine.register(new StaticRule("R_INFO", "信息", "TEST", 100, ctx ->
                RuleResult.triggered("R_INFO", "信息", "TEST", RuleSeverity.INFO, "info", "")));
        engine.register(new StaticRule("R_RED", "红色", "TEST", 100, ctx ->
                RuleResult.triggered("R_RED", "红色", "TEST", RuleSeverity.RED, "red", "")));
        engine.register(new StaticRule("R_YELLOW", "黄色", "TEST", 100, ctx ->
                RuleResult.triggered("R_YELLOW", "黄色", "TEST", RuleSeverity.YELLOW, "yellow", "")));

        List<RuleResult> results = engine.evaluate(RuleContext.of(Map.of()));
        assertEquals(3, results.size());
        assertEquals(RuleSeverity.RED, results.get(0).getSeverity());
        assertEquals(RuleSeverity.YELLOW, results.get(1).getSeverity());
        assertEquals(RuleSeverity.INFO, results.get(2).getSeverity());
    }

    @Test
    @DisplayName("按优先级编排执行（priority 数值越小越先执行）")
    void testPriorityOrder() {
        var executionOrder = new java.util.concurrent.CopyOnWriteArrayList<String>();
        engine.register(new StaticRule("R_LOW", "低优先级", "TEST", 200, ctx -> {
            executionOrder.add("R_LOW");
            return RuleResult.notTriggered("R_LOW");
        }));
        engine.register(new StaticRule("R_HIGH", "高优先级", "TEST", 50, ctx -> {
            executionOrder.add("R_HIGH");
            return RuleResult.notTriggered("R_HIGH");
        }));
        engine.register(new StaticRule("R_MID", "中优先级", "TEST", 100, ctx -> {
            executionOrder.add("R_MID");
            return RuleResult.notTriggered("R_MID");
        }));

        engine.evaluate(RuleContext.of(Map.of()));
        assertEquals(List.of("R_HIGH", "R_MID", "R_LOW"), executionOrder);
    }

    @Test
    @DisplayName("单规则异常不影响其他规则")
    void testExceptionIsolation() {
        engine.register(new StaticRule("R_NORMAL", "正常规则", "TEST", ctx ->
                RuleResult.triggered("R_NORMAL", "正常规则", "TEST", RuleSeverity.YELLOW, "ok", "")));
        engine.register(new StaticRule("R_BROKEN", "异常规则", "TEST", ctx -> {
            throw new RuntimeException("模拟异常");
        }));

        List<RuleResult> results = engine.evaluate(RuleContext.of(Map.of()));
        assertEquals(1, results.size());
        assertEquals("R_NORMAL", results.get(0).getRuleCode());
    }

    @Test
    @DisplayName("表达式规则评估 - 条件满足触发")
    void testExpressionRuleTriggered() {
        var def = com.njydsz.pmis.literule.api.RuleDefinition.builder()
                .code("EXPR_001")
                .name("表达式规则测试")
                .category("TEST")
                .conditionExpression("value >= 100")
                .defaultSeverity(RuleSeverity.YELLOW)
                .titleTemplate("值 ${value} 超过 100")
                .descriptionTemplate("当前值 ${value} 已超阈值")
                .build();
        engine.register(new ExpressionRule(def, evaluator));

        List<RuleResult> results = engine.evaluate(RuleContext.of(Map.of("value", 150)));
        assertEquals(1, results.size());
        assertTrue(results.get(0).isTriggered());
        assertEquals("值 150 超过 100", results.get(0).getTitle());
    }

    @Test
    @DisplayName("表达式规则评估 - 条件不满足不触发")
    void testExpressionRuleNotTriggered() {
        var def = com.njydsz.pmis.literule.api.RuleDefinition.builder()
                .code("EXPR_002")
                .name("表达式规则测试")
                .category("TEST")
                .conditionExpression("value >= 100")
                .defaultSeverity(RuleSeverity.YELLOW)
                .build();
        engine.register(new ExpressionRule(def, evaluator));

        List<RuleResult> results = engine.evaluate(RuleContext.of(Map.of("value", 50)));
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("动态严重度表达式")
    void testDynamicSeverity() {
        var def = com.njydsz.pmis.literule.api.RuleDefinition.builder()
                .code("EXPR_SEV")
                .name("动态严重度")
                .category("TEST")
                .conditionExpression("cost >= 500000")
                .severityExpression("cost >= 1000000 ? 'RED' : 'YELLOW'")
                .defaultSeverity(RuleSeverity.YELLOW)
                .build();
        engine.register(new ExpressionRule(def, evaluator));

        // 600000 -> YELLOW
        List<RuleResult> results1 = engine.evaluate(RuleContext.of(Map.of("cost", 600000)));
        assertEquals(RuleSeverity.YELLOW, results1.get(0).getSeverity());

        // 1200000 -> RED
        List<RuleResult> results2 = engine.evaluate(RuleContext.of(Map.of("cost", 1200000)));
        assertEquals(RuleSeverity.RED, results2.get(0).getSeverity());
    }

    @Test
    @DisplayName("topResult 返回最高严重度")
    void testTopResult() {
        engine.register(new StaticRule("R_Y", "黄色", "TEST", ctx ->
                RuleResult.triggered("R_Y", "黄色", "TEST", RuleSeverity.YELLOW, "", "")));
        engine.register(new StaticRule("R_R", "红色", "TEST", ctx ->
                RuleResult.triggered("R_R", "红色", "TEST", RuleSeverity.RED, "", "")));

        RuleResult top = engine.topResult(RuleContext.of(Map.of()));
        assertNotNull(top);
        assertEquals(RuleSeverity.RED, top.getSeverity());
    }

    @Test
    @DisplayName("dryRun 返回全部结果（含未触发）")
    void testDryRun() {
        engine.register(new StaticRule("R1", "规则1", "TEST", ctx ->
                RuleResult.triggered("R1", "规则1", "TEST", RuleSeverity.YELLOW, "", "")));
        engine.register(new StaticRule("R2", "规则2", "TEST", ctx ->
                RuleResult.notTriggered("R2")));

        List<RuleResult> all = engine.dryRun(RuleContext.of(Map.of()));
        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("执行统计正确记录")
    void testStats() {
        engine.register(new StaticRule("R1", "规则1", "TEST", ctx ->
                RuleResult.triggered("R1", "规则1", "TEST", RuleSeverity.RED, "", "")));
        engine.register(new StaticRule("R2", "规则2", "TEST", ctx -> {
            throw new RuntimeException("error");
        }));

        engine.evaluate(RuleContext.of(Map.of()));

        RuleEngineStats stats = engine.getStats();
        assertEquals(2, stats.getTotalEvaluations());
        assertEquals(1, stats.getTotalTriggered());
        assertEquals(1, stats.getTotalErrors());

        RuleEngineStats.RuleStat r1Stat = stats.getPerRuleStats().get("R1");
        assertNotNull(r1Stat);
        assertEquals(1, r1Stat.getExecutions());
        assertEquals(1, r1Stat.getTriggered());

        RuleEngineStats.RuleStat r2Stat = stats.getPerRuleStats().get("R2");
        assertNotNull(r2Stat);
        assertEquals(1, r2Stat.getErrors());
    }

    @Test
    @DisplayName("热更新覆盖同编码规则")
    void testHotUpdateOverwrite() {
        // 注册规则 v1
        engine.register(new StaticRule("R_HOT", "版本1", "TEST", ctx ->
                RuleResult.triggered("R_HOT", "版本1", "TEST", RuleSeverity.YELLOW, "v1", "")));

        // 重新注册同编码规则 v2
        engine.register(new StaticRule("R_HOT", "版本2", "TEST", ctx ->
                RuleResult.triggered("R_HOT", "版本2", "TEST", RuleSeverity.RED, "v2", "")));

        assertEquals(1, engine.getRules().size());
        List<RuleResult> results = engine.evaluate(RuleContext.of(Map.of()));
        assertEquals("v2", results.get(0).getTitle());
    }
}
