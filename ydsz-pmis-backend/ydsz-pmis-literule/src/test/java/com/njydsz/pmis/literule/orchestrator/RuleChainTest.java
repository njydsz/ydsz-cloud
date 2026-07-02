package com.njydsz.pmis.literule.orchestrator;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.expr.AviatorExpressionEvaluator;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.impl.StaticRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RuleChain 规则链编排测试
 *
 * <p>覆盖 THEN 顺序执行、IF 条件执行（满足/不满足）、SWITCH 分支选择、WHEN 并行执行四种语义。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("RuleChain 规则链编排测试")
class RuleChainTest {

    private ExpressionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new AviatorExpressionEvaluator();
    }

    /**
     * 构建一条必定触发、记录执行顺序的静态规则
     */
    private StaticRule triggeredRule(String code, String name, List<String> executionOrder) {
        return new StaticRule(code, name, "TEST", ctx -> {
            executionOrder.add(code);
            return RuleResult.triggered(code, name, "TEST", RuleSeverity.INFO, name, "");
        });
    }

    @Test
    @DisplayName("THEN 顺序执行 - 全部节点依次执行并收集结果")
    void testThenSequential() {
        CopyOnWriteArrayList<String> order = new CopyOnWriteArrayList<>();
        StaticRule r1 = triggeredRule("R1", "规则一", order);
        StaticRule r2 = triggeredRule("R2", "规则二", order);
        StaticRule r3 = triggeredRule("R3", "规则三", order);

        RuleChain chain = RuleChain.then(r1, r2, r3);
        List<RuleResult> results = chain.evaluate(RuleContext.of(Map.of()), evaluator);

        // 全部触发，结果数 = 3
        assertEquals(3, results.size());
        // 执行顺序与注册顺序一致
        assertEquals(List.of("R1", "R2", "R3"), order);
        // 结果编码全部存在
        List<String> codes = results.stream().map(RuleResult::getRuleCode).collect(Collectors.toList());
        assertTrue(codes.contains("R1"));
        assertTrue(codes.contains("R2"));
        assertTrue(codes.contains("R3"));
    }

    @Test
    @DisplayName("THEN 顺序执行 - 未触发结果不被收集")
    void testThenNotTriggeredFiltered() {
        StaticRule r1 = triggeredRule("R1", "规则一", new CopyOnWriteArrayList<>());
        StaticRule r2 = new StaticRule("R2", "未触发规则", "TEST",
                ctx -> RuleResult.notTriggered("R2"));

        RuleChain chain = RuleChain.then(r1, r2);
        List<RuleResult> results = chain.evaluate(RuleContext.of(Map.of()), evaluator);

        assertEquals(1, results.size());
        assertEquals("R1", results.get(0).getRuleCode());
    }

    @Test
    @DisplayName("IF 条件执行 - 条件满足时执行动作规则")
    void testIfConditionMatched() {
        StaticRule action = new StaticRule("R_IF", "条件动作", "TEST",
                ctx -> RuleResult.triggered("R_IF", "条件动作", "TEST",
                        RuleSeverity.YELLOW, "条件满足", "amount 超过阈值"));

        RuleChain chain = RuleChain.ifThen("amount > 1000", action);

        RuleContext ctx = RuleContext.of(Map.of("amount", 1500));
        List<RuleResult> results = chain.evaluate(ctx, evaluator);

        assertEquals(1, results.size());
        assertTrue(results.get(0).isTriggered());
        assertEquals("R_IF", results.get(0).getRuleCode());
    }

    @Test
    @DisplayName("IF 条件执行 - 条件不满足时不执行动作规则")
    void testIfConditionNotMatched() {
        CopyOnWriteArrayList<String> executed = new CopyOnWriteArrayList<>();
        StaticRule action = new StaticRule("R_IF", "条件动作", "TEST", ctx -> {
            executed.add("R_IF");
            return RuleResult.triggered("R_IF", "条件动作", "TEST",
                    RuleSeverity.YELLOW, "条件满足", "");
        });

        RuleChain chain = RuleChain.ifThen("amount > 1000", action);

        RuleContext ctx = RuleContext.of(Map.of("amount", 500));
        List<RuleResult> results = chain.evaluate(ctx, evaluator);

        // 条件不满足，无结果
        assertTrue(results.isEmpty());
        // 动作规则未执行
        assertTrue(executed.isEmpty());
    }

    @Test
    @DisplayName("SWITCH 分支选择 - 命中对应分支")
    void testSwitchBranchMatched() {
        CopyOnWriteArrayList<String> executed = new CopyOnWriteArrayList<>();
        StaticRule branchA = triggeredRule("R_A", "分支A", executed);
        StaticRule branchB = triggeredRule("R_B", "分支B", executed);
        StaticRule branchC = triggeredRule("R_C", "分支C", executed);

        RuleChain chain = RuleChain.switchOn("type",
                Map.of("A", branchA, "B", branchB, "C", branchC));

        RuleContext ctx = RuleContext.of(Map.of("type", "B"));
        List<RuleResult> results = chain.evaluate(ctx, evaluator);

        assertEquals(1, results.size());
        assertEquals("R_B", results.get(0).getRuleCode());
        // 仅 B 分支执行
        assertEquals(List.of("R_B"), executed);
    }

    @Test
    @DisplayName("SWITCH 分支选择 - 未命中分支时无结果")
    void testSwitchBranchNotMatched() {
        CopyOnWriteArrayList<String> executed = new CopyOnWriteArrayList<>();
        StaticRule branchA = triggeredRule("R_A", "分支A", executed);

        RuleChain chain = RuleChain.switchOn("type", Map.of("A", branchA));

        RuleContext ctx = RuleContext.of(Map.of("type", "Z"));
        List<RuleResult> results = chain.evaluate(ctx, evaluator);

        assertTrue(results.isEmpty());
        assertTrue(executed.isEmpty());
    }

    @Test
    @DisplayName("WHEN 并行执行 - 全部节点执行并收集结果")
    void testWhenParallel() {
        CopyOnWriteArrayList<String> executed = new CopyOnWriteArrayList<>();
        StaticRule r1 = triggeredRule("R1", "规则一", executed);
        StaticRule r2 = triggeredRule("R2", "规则二", executed);
        StaticRule r3 = triggeredRule("R3", "规则三", executed);

        RuleChain chain = RuleChain.when(r1, r2, r3);
        List<RuleResult> results = chain.evaluate(RuleContext.of(Map.of()), evaluator);

        // 全部触发，结果数 = 3
        assertEquals(3, results.size());
        // 全部节点均已执行
        assertEquals(3, executed.size());
        // 结果编码全部存在（并行执行顺序不保证，故用包含校验）
        List<String> codes = results.stream().map(RuleResult::getRuleCode).collect(Collectors.toList());
        assertTrue(codes.contains("R1"));
        assertTrue(codes.contains("R2"));
        assertTrue(codes.contains("R3"));
    }

    @Test
    @DisplayName("RuleOrchestrator 编排器 - 按注册顺序执行多条链并合并结果")
    void testOrchestratorMerge() {
        RuleOrchestrator orchestrator = new RuleOrchestrator(evaluator);

        // 链1：THEN 顺序执行
        orchestrator.register(RuleChain.then(
                new StaticRule("R1", "规则一", "TEST", ctx ->
                        RuleResult.triggered("R1", "规则一", "TEST", RuleSeverity.INFO, "r1", "")),
                new StaticRule("R2", "规则二", "TEST", ctx ->
                        RuleResult.triggered("R2", "规则二", "TEST", RuleSeverity.YELLOW, "r2", ""))
        ));

        // 链2：IF 条件执行（满足）
        orchestrator.register(RuleChain.ifThen("amount > 1000",
                new StaticRule("R_IF", "条件动作", "TEST", ctx ->
                        RuleResult.triggered("R_IF", "条件动作", "TEST", RuleSeverity.RED, "if", ""))));

        // 链3：SWITCH 分支选择（命中 C）
        orchestrator.register(RuleChain.switchOn("type",
                Map.of("A", new StaticRule("R_A", "分支A", "TEST", ctx ->
                                RuleResult.triggered("R_A", "分支A", "TEST", RuleSeverity.INFO, "a", "")),
                        "C", new StaticRule("R_C", "分支C", "TEST", ctx ->
                                RuleResult.triggered("R_C", "分支C", "TEST", RuleSeverity.INFO, "c", "")))));

        RuleContext ctx = RuleContext.of(Map.of("amount", 2000, "type", "C"));
        List<RuleResult> results = orchestrator.evaluate(ctx);

        // 三条链各产出 2/1/1 个结果，合计 4
        assertEquals(4, results.size());
        List<String> codes = results.stream().map(RuleResult::getRuleCode).collect(Collectors.toList());
        assertTrue(codes.contains("R1"));
        assertTrue(codes.contains("R2"));
        assertTrue(codes.contains("R_IF"));
        assertTrue(codes.contains("R_C"));
    }
}
