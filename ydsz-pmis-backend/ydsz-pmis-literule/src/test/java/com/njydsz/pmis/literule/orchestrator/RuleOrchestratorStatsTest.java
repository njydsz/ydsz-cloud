package com.njydsz.pmis.literule.orchestrator;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleEngineStats;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.core.DefaultRuleEngine;
import com.njydsz.pmis.literule.expr.AviatorExpressionEvaluator;
import com.njydsz.pmis.literule.impl.StaticRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RuleOrchestrator 统计统一测试
 *
 * <p>验证 P0-3：编排层执行统计统一记录到引擎统计中，消除编排层与引擎层统计割裂。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@DisplayName("RuleOrchestrator 统计统一测试")
class RuleOrchestratorStatsTest {

    private DefaultRuleEngine engine;
    private AviatorExpressionEvaluator evaluator;
    private RuleOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        engine = new DefaultRuleEngine();
        evaluator = new AviatorExpressionEvaluator();
        orchestrator = new RuleOrchestrator(evaluator);
        // 将引擎的统计记录器注入编排器
        orchestrator.setStatsRecorder(engine.asStatsRecorder());
    }

    @Test
    @DisplayName("THEN 链执行结果应记录到引擎统计")
    void shouldRecordThenChainStats() {
        StaticRule rule1 = new StaticRule("ORCH_R1", "规则1", "EVM", ctx ->
                RuleResult.triggered("ORCH_R1", "规则1", "EVM", RuleSeverity.YELLOW, "", ""));
        StaticRule rule2 = new StaticRule("ORCH_R2", "规则2", "COST", ctx ->
                RuleResult.triggered("ORCH_R2", "规则2", "COST", RuleSeverity.RED, "", ""));

        orchestrator.register(RuleChain.then(rule1, rule2));

        RuleContext context = RuleContext.of(new HashMap<>());
        List<RuleResult> results = orchestrator.evaluate(context);

        assertEquals(2, results.size());

        // 验证统计已记录到引擎
        RuleEngineStats stats = engine.getStats();
        assertEquals(2, stats.getTotalEvaluations());
        assertEquals(2, stats.getTotalTriggered());
        assertNotNull(stats.getPerRuleStats().get("ORCH_R1"));
        assertNotNull(stats.getPerRuleStats().get("ORCH_R2"));
        assertEquals(1, stats.getPerRuleStats().get("ORCH_R1").getExecutions());
        assertEquals(1, stats.getPerRuleStats().get("ORCH_R2").getExecutions());
    }

    @Test
    @DisplayName("WHEN 并行链执行结果应记录到引擎统计")
    void shouldRecordWhenChainStats() {
        StaticRule rule1 = new StaticRule("ORCH_P1", "并行规则1", "EVM", ctx ->
                RuleResult.triggered("ORCH_P1", "并行规则1", "EVM", RuleSeverity.YELLOW, "", ""));
        StaticRule rule2 = new StaticRule("ORCH_P2", "并行规则2", "COST", ctx ->
                RuleResult.triggered("ORCH_P2", "并行规则2", "COST", RuleSeverity.RED, "", ""));

        orchestrator.register(RuleChain.when(rule1, rule2));

        RuleContext context = RuleContext.of(new HashMap<>());
        orchestrator.evaluate(context);

        // 并行执行的统计也应记录
        RuleEngineStats stats = engine.getStats();
        assertEquals(2, stats.getTotalEvaluations());
        assertNotNull(stats.getPerRuleStats().get("ORCH_P1"));
        assertNotNull(stats.getPerRuleStats().get("ORCH_P2"));
    }

    @Test
    @DisplayName("IF 条件链执行结果应记录到引擎统计")
    void shouldRecordIfChainStats() {
        StaticRule actionRule = new StaticRule("ORCH_IF", "条件规则", "EVM", ctx ->
                RuleResult.triggered("ORCH_IF", "条件规则", "EVM", RuleSeverity.YELLOW, "", ""));

        orchestrator.register(RuleChain.ifThen("amount > 100", actionRule));

        Map<String, Object> facts = new HashMap<>();
        facts.put("amount", 200);
        RuleContext context = RuleContext.of(facts);
        orchestrator.evaluate(context);

        RuleEngineStats stats = engine.getStats();
        assertEquals(1, stats.getTotalEvaluations());
        assertEquals(1, stats.getTotalTriggered());
        assertNotNull(stats.getPerRuleStats().get("ORCH_IF"));
    }

    @Test
    @DisplayName("IF 条件不满足时不记录统计")
    void shouldNotRecordWhenIfConditionFalse() {
        StaticRule actionRule = new StaticRule("ORCH_IF", "条件规则", "EVM", ctx ->
                RuleResult.triggered("ORCH_IF", "条件规则", "EVM", RuleSeverity.YELLOW, "", ""));

        orchestrator.register(RuleChain.ifThen("amount > 100", actionRule));

        Map<String, Object> facts = new HashMap<>();
        facts.put("amount", 50);
        RuleContext context = RuleContext.of(facts);
        orchestrator.evaluate(context);

        RuleEngineStats stats = engine.getStats();
        assertEquals(0, stats.getTotalEvaluations());
    }

    @Test
    @DisplayName("未设置 StatsRecorder 时不记录统计（向后兼容）")
    void shouldNotRecordWithoutStatsRecorder() {
        RuleOrchestrator plainOrchestrator = new RuleOrchestrator(evaluator);
        // 不设置 statsRecorder

        StaticRule rule = new StaticRule("ORCH_PLAIN", "规则", "EVM", ctx ->
                RuleResult.triggered("ORCH_PLAIN", "规则", "EVM", RuleSeverity.YELLOW, "", ""));
        plainOrchestrator.register(RuleChain.then(rule));

        plainOrchestrator.evaluate(RuleContext.of(new HashMap<>()));

        // 引擎统计应为空
        assertEquals(0, engine.getStats().getTotalEvaluations());
    }

    @Test
    @DisplayName("多链混合编排统计全部记录")
    void shouldRecordMultiChainStats() {
        StaticRule r1 = new StaticRule("MIX_R1", "规则1", "EVM", ctx ->
                RuleResult.triggered("MIX_R1", "规则1", "EVM", RuleSeverity.YELLOW, "", ""));
        StaticRule r2 = new StaticRule("MIX_R2", "规则2", "COST", ctx ->
                RuleResult.triggered("MIX_R2", "规则2", "COST", RuleSeverity.RED, "", ""));

        // THEN 链 + WHEN 链
        orchestrator.register(RuleChain.then(r1));
        orchestrator.register(RuleChain.when(r2));

        orchestrator.evaluate(RuleContext.of(new HashMap<>()));

        RuleEngineStats stats = engine.getStats();
        assertEquals(2, stats.getTotalEvaluations());
        assertEquals(2, stats.getTotalTriggered());
    }

    @Test
    @DisplayName("编排层异常规则也记录到统计")
    void shouldRecordErrorStats() {
        StaticRule errorRule = new StaticRule("ERR_RULE", "异常规则", "EVM", ctx -> {
            throw new RuntimeException("测试异常");
        });
        StaticRule normalRule = new StaticRule("OK_RULE", "正常规则", "COST", ctx ->
                RuleResult.triggered("OK_RULE", "正常规则", "COST", RuleSeverity.YELLOW, "", ""));

        orchestrator.register(RuleChain.then(errorRule, normalRule));

        List<RuleResult> results = orchestrator.evaluate(RuleContext.of(new HashMap<>()));

        // 异常规则被隔离，正常规则仍执行
        assertEquals(1, results.size());
        assertEquals("OK_RULE", results.get(0).getRuleCode());

        RuleEngineStats stats = engine.getStats();
        assertEquals(2, stats.getTotalEvaluations()); // 两条都评估了
        assertEquals(1, stats.getTotalErrors()); // 异常规则记录了 error
        assertEquals(1, stats.getTotalTriggered()); // 正常规则触发了
    }
}
