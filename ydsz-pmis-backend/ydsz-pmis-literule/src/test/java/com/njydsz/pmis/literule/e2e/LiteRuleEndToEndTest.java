package com.njydsz.pmis.literule.e2e;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.api.ScriptDefinition;
import com.njydsz.pmis.literule.config.RuleConflict;
import com.njydsz.pmis.literule.config.RuleConflictDetector;
import com.njydsz.pmis.literule.core.AsyncTraceRecorder;
import com.njydsz.pmis.literule.core.DefaultRuleEngine;
import com.njydsz.pmis.literule.dsl.RuleDsl;
import com.njydsz.pmis.literule.dsl.RuleDslConverter;
import com.njydsz.pmis.literule.dsl.RuleDslParser;
import com.njydsz.pmis.literule.expr.AviatorExpressionEvaluator;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.impl.ExpressionRule;
import com.njydsz.pmis.literule.impl.ScriptRule;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LiteRule 端到端集成测试
 *
 * <p>覆盖规则引擎完整链路：规则定义 → RuleConfigProvider 存储 → 冲突检测 →
 * DefaultRuleEngine 注册 → 评估 → 结果验证 → Trace 记录。
 *
 * <p>使用内存版 RuleConfigProvider，不依赖 Spring 容器和外部存储。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@DisplayName("LiteRule 端到端集成测试")
class LiteRuleEndToEndTest {

    private DefaultRuleEngine engine;
    private ExpressionEvaluator evaluator;
    private InMemoryRuleConfigProvider configProvider;
    private RuleConflictDetector conflictDetector;
    private AsyncTraceRecorder traceRecorder;
    private InMemoryTraceRecorder traceDelegate;

    @BeforeEach
    void setUp() {
        engine = new DefaultRuleEngine();
        engine.setStatsEnabled(true);
        evaluator = new AviatorExpressionEvaluator(false);
        configProvider = new InMemoryRuleConfigProvider();
        conflictDetector = new RuleConflictDetector(configProvider);
        traceDelegate = new InMemoryTraceRecorder();
        traceRecorder = new AsyncTraceRecorder(1000, 10, 100);
        traceRecorder.setDelegate(traceDelegate);
        engine.setTraceRecorder(traceRecorder);
    }

    @AfterEach
    void tearDown() {
        if (traceRecorder != null) {
            traceRecorder.shutdown(3);
        }
    }

    // ---------- 场景 1：完整流程 - 规则定义→存储→注册→评估→结果验证 ----------

    @Test
    @DisplayName("端到端 - 表达式规则完整流程")
    void expressionRuleEndToEndShouldWork() {
        // 1. 定义规则
        RuleDefinition def = RuleDefinition.builder()
                .code("R_E2E_001")
                .name("预算超支预警")
                .category("BUDGET")
                .conditionExpression("budgetUsedRatio > 0.9")
                .defaultSeverity(RuleSeverity.RED)
                .priority(50)
                .tenantId(1L)
                .build();

        // 2. 存储到 ConfigProvider
        configProvider.save(def, "test");

        // 3. 冲突检测（无冲突）
        List<RuleConflict> conflicts = conflictDetector.detect(def);
        assertTrue(conflicts.isEmpty(), "无冲突");

        // 4. 转换并注册到引擎
        Rule rule = new ExpressionRule(def, evaluator);
        engine.register(rule);

        // 5. 评估 - 条件满足
        Map<String, Object> facts = new HashMap<>();
        facts.put("budgetUsedRatio", 0.95);
        List<RuleResult> results = engine.evaluate(RuleContext.of(facts, "BUDGET", "E2E_TEST"));

        // 6. 验证结果
        assertEquals(1, results.size());
        RuleResult result = results.get(0);
        assertTrue(result.isTriggered());
        assertEquals("R_E2E_001", result.getRuleCode());
        assertEquals(RuleSeverity.RED, result.getSeverity());
    }

    // ---------- 场景 2：多规则类型联合评估 ----------

    @Test
    @DisplayName("端到端 - 表达式 + 脚本规则联合评估")
    void multipleRuleTypesEndToEndShouldWork() {
        // 表达式规则
        RuleDefinition exprDef = RuleDefinition.builder()
                .code("R_E2E_EXPR")
                .name("EVM红色预警")
                .category("RISK")
                .conditionExpression("evmRedCount >= 3")
                .defaultSeverity(RuleSeverity.RED)
                .priority(10)
                .tenantId(1L)
                .build();
        engine.register(new ExpressionRule(exprDef, evaluator));

        // 脚本规则（Groovy）
        ScriptDefinition scriptDef = ScriptDefinition.builder()
                .ruleCode("R_E2E_SCRIPT")
                .ruleName("复杂条件判断")
                .category("RISK")
                .language("groovy")
                .script("def cpi = facts.cpi ?: 1.0\n" +
                        "def spi = facts.spi ?: 1.0\n" +
                        "if (cpi < 0.9 && spi < 0.85) {\n" +
                        "    severity = 'YELLOW'\n" +
                        "    return true\n" +
                        "}\n" +
                        "return false")
                .defaultSeverity("INFO")
                .sandboxEnabled(true)
                .priority(20)
                .build();
        engine.register(ScriptRule.from(scriptDef));

        // 评估 - 两个条件都满足
        Map<String, Object> facts = new HashMap<>();
        facts.put("evmRedCount", 5);
        facts.put("cpi", 0.8);
        facts.put("spi", 0.8);
        List<RuleResult> results = engine.evaluate(RuleContext.of(facts, "RISK", "E2E_TEST"));

        assertEquals(2, results.size());
        // 按严重度排序，RED 在前
        assertEquals(RuleSeverity.RED, results.get(0).getSeverity());
        assertEquals(RuleSeverity.YELLOW, results.get(1).getSeverity());
    }

    // ---------- 场景 3：互斥组端到端 ----------

    @Test
    @DisplayName("端到端 - 互斥组高优先级命中后低优先级短路")
    void mutexGroupEndToEndShouldShortCircuit() {
        // 高优先级规则（互斥组 RISK_GROUP）
        RuleDefinition highDef = RuleDefinition.builder()
                .code("R_MUTEX_HIGH")
                .name("高风险规则")
                .category("RISK")
                .conditionExpression("amount > 10000")
                .defaultSeverity(RuleSeverity.RED)
                .priority(10)
                .mutexGroup("RISK_GROUP")
                .tenantId(1L)
                .build();
        engine.register(new ExpressionRule(highDef, evaluator));

        // 低优先级规则（同互斥组）
        RuleDefinition lowDef = RuleDefinition.builder()
                .code("R_MUTEX_LOW")
                .name("低风险规则")
                .category("RISK")
                .conditionExpression("amount > 5000")
                .defaultSeverity(RuleSeverity.YELLOW)
                .priority(50)
                .mutexGroup("RISK_GROUP")
                .tenantId(1L)
                .build();
        engine.register(new ExpressionRule(lowDef, evaluator));

        // 评估 - 两个条件都满足，但同互斥组高优先级短路
        Map<String, Object> facts = new HashMap<>();
        facts.put("amount", 15000);
        List<RuleResult> results = engine.evaluate(RuleContext.of(facts));

        // 只有高优先级规则触发
        assertEquals(1, results.size());
        assertEquals("R_MUTEX_HIGH", results.get(0).getRuleCode());
        assertEquals(RuleSeverity.RED, results.get(0).getSeverity());
    }

    // ---------- 场景 4：DSL 端到端 - YAML解析→转换→引擎执行 ----------

    @Test
    @DisplayName("端到端 - DSL YAML 解析→转换→引擎执行")
    void dslEndToEndShouldExecuteViaEngine() {
        String yaml = """
                rules:
                  - code: R_DSL_EXPR
                    name: DSL表达式规则
                    type: expression
                    category: RISK
                    priority: 10
                    condition: amount > 1000
                    severity: RED
                  - code: R_DSL_SCRIPT
                    name: DSL脚本规则
                    type: script
                    category: RISK
                    priority: 20
                    script_language: groovy
                    script_body: |
                      def ratio = facts.ratio ?: 0
                      if (ratio > 0.8) {
                        severity = 'YELLOW'
                        return true
                      }
                      return false
                """;

        // 1. 解析 YAML
        RuleDsl dsl = RuleDslParser.parse(yaml);

        // 2. 校验
        RuleDslParser.validate(dsl);

        // 3. 转换为规则
        List<Rule> rules = RuleDslConverter.toRules(dsl, evaluator);

        // 4. 注册到引擎
        for (Rule rule : rules) {
            engine.register(rule);
        }

        // 5. 评估
        Map<String, Object> facts = new HashMap<>();
        facts.put("amount", 2000);
        facts.put("ratio", 0.9);
        List<RuleResult> results = engine.evaluate(RuleContext.of(facts));

        // 6. 验证 - 两个规则都触发
        assertEquals(2, results.size());
        // 按严重度排序，RED 在前
        assertEquals(RuleSeverity.RED, results.get(0).getSeverity());
        assertEquals("R_DSL_EXPR", results.get(0).getRuleCode());
        assertEquals(RuleSeverity.YELLOW, results.get(1).getSeverity());
        assertEquals("R_DSL_SCRIPT", results.get(1).getRuleCode());
    }

    // ---------- 场景 5：冲突检测端到端 ----------

    @Test
    @DisplayName("端到端 - 规则保存前冲突检测")
    void conflictDetectionEndToEndShouldDetectOverlap() {
        // 已有规则
        RuleDefinition existing = RuleDefinition.builder()
                .code("R_EXIST_E2E")
                .name("已有金额规则")
                .category("RISK")
                .conditionExpression("amount > 1000")
                .defaultSeverity(RuleSeverity.RED)
                .tenantId(1L)
                .build();
        configProvider.save(existing, "admin");

        // 新规则 - 范围重叠
        RuleDefinition newDef = RuleDefinition.builder()
                .code("R_NEW_E2E")
                .name("新金额规则")
                .category("RISK")
                .conditionExpression("amount > 2000")
                .defaultSeverity(RuleSeverity.YELLOW)
                .tenantId(1L)
                .build();

        // 冲突检测
        List<RuleConflict> conflicts = conflictDetector.detect(newDef);

        // 应检测到 CONDITION_OVERLAP
        assertTrue(conflicts.stream().anyMatch(c -> c.getType() == RuleConflict.Type.CONDITION_OVERLAP),
                "应检测到条件范围重叠");
    }

    // ---------- 场景 6：Trace 记录端到端 ----------

    @Test
    @DisplayName("端到端 - 评估后 Trace 被异步记录")
    void traceRecordingEndToEndShouldCaptureTraces() throws InterruptedException {
        // 注册规则
        RuleDefinition def = RuleDefinition.builder()
                .code("R_TRACE_E2E")
                .name("Trace测试规则")
                .category("RISK")
                .conditionExpression("amount > 100")
                .defaultSeverity(RuleSeverity.YELLOW)
                .priority(10)
                .tenantId(1L)
                .build();
        engine.register(new ExpressionRule(def, evaluator));

        // 评估
        Map<String, Object> facts = new HashMap<>();
        facts.put("amount", 200);
        engine.evaluate(RuleContext.of(facts));

        // 等待异步 Trace 写入
        Thread.sleep(500);

        // 验证 Trace 被记录
        List<com.njydsz.pmis.literule.api.RuleExecutionTrace> traces = traceDelegate.getByRuleCode("R_TRACE_E2E", 10);
        assertFalse(traces.isEmpty(), "应至少记录一条 Trace");
        assertEquals("R_TRACE_E2E", traces.get(0).getRuleCode());
        assertTrue(traces.get(0).isTriggered());
    }

    // ---------- 场景 7：热加载端到端 ----------

    @Test
    @DisplayName("端到端 - 规则注销后不再触发")
    void hotReloadEndToEndShouldUnregisterRule() {
        // 注册规则
        RuleDefinition def = RuleDefinition.builder()
                .code("R_HOT_E2E")
                .name("热加载测试")
                .category("RISK")
                .conditionExpression("amount > 100")
                .defaultSeverity(RuleSeverity.RED)
                .priority(10)
                .tenantId(1L)
                .build();
        engine.register(new ExpressionRule(def, evaluator));

        // 第一次评估 - 触发
        Map<String, Object> facts = new HashMap<>();
        facts.put("amount", 200);
        List<RuleResult> results1 = engine.evaluate(RuleContext.of(facts));
        assertEquals(1, results1.size());

        // 注销规则
        engine.unregister("R_HOT_E2E");

        // 第二次评估 - 不触发
        List<RuleResult> results2 = engine.evaluate(RuleContext.of(facts));
        assertTrue(results2.isEmpty(), "注销后不应再触发");
    }

    // ---------- 场景 8：dryRun 端到端 ----------

    @Test
    @DisplayName("端到端 - dryRun 返回所有结果含未触发")
    void dryRunEndToEndShouldReturnAllResults() {
        // 注册两条规则
        RuleDefinition triggered = RuleDefinition.builder()
                .code("R_DRY_01")
                .name("触发规则")
                .category("RISK")
                .conditionExpression("amount > 100")
                .defaultSeverity(RuleSeverity.RED)
                .priority(10)
                .tenantId(1L)
                .build();
        engine.register(new ExpressionRule(triggered, evaluator));

        RuleDefinition notTriggered = RuleDefinition.builder()
                .code("R_DRY_02")
                .name("未触发规则")
                .category("RISK")
                .conditionExpression("amount > 1000")
                .defaultSeverity(RuleSeverity.YELLOW)
                .priority(20)
                .tenantId(1L)
                .build();
        engine.register(new ExpressionRule(notTriggered, evaluator));

        // dryRun
        Map<String, Object> facts = new HashMap<>();
        facts.put("amount", 200);
        List<RuleResult> results = engine.dryRun(RuleContext.of(facts));

        // 应返回所有结果（含未触发）
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(r -> r.getRuleCode().equals("R_DRY_01") && r.isTriggered()));
        assertTrue(results.stream().anyMatch(r -> r.getRuleCode().equals("R_DRY_02") && !r.isTriggered()));
    }

    // ---------- 场景 9：topResult 端到端 ----------

    @Test
    @DisplayName("端到端 - topResult 返回最严重结果")
    void topResultEndToEndShouldReturnMostSevere() {
        RuleDefinition red = RuleDefinition.builder()
                .code("R_TOP_RED")
                .name("红色规则")
                .category("RISK")
                .conditionExpression("amount > 100")
                .defaultSeverity(RuleSeverity.RED)
                .priority(10)
                .tenantId(1L)
                .build();
        engine.register(new ExpressionRule(red, evaluator));

        RuleDefinition yellow = RuleDefinition.builder()
                .code("R_TOP_YELLOW")
                .name("黄色规则")
                .category("RISK")
                .conditionExpression("amount > 50")
                .defaultSeverity(RuleSeverity.YELLOW)
                .priority(20)
                .tenantId(1L)
                .build();
        engine.register(new ExpressionRule(yellow, evaluator));

        Map<String, Object> facts = new HashMap<>();
        facts.put("amount", 200);
        RuleResult top = engine.topResult(RuleContext.of(facts));

        assertNotNull(top);
        assertEquals(RuleSeverity.RED, top.getSeverity());
    }

    // ---------- 内存版测试基础设施 ----------

    /**
     * 内存版 RuleConfigProvider（用于端到端测试）
     */
    static class InMemoryRuleConfigProvider implements RuleConfigProvider {
        final Map<String, RuleDefinition> store = new ConcurrentHashMap<>();

        @Override
        public List<RuleDefinition> loadEnabledRules() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<RuleDefinition> loadAllRules() {
            return new ArrayList<>(store.values());
        }

        @Override
        public RuleDefinition save(RuleDefinition definition, String operator) {
            store.put(definition.getCode(), definition);
            return definition;
        }

        @Override
        public void toggleEnabled(String ruleCode, boolean enabled, String operator) {
            // no-op
        }

        @Override
        public RuleDefinition findByCode(String ruleCode) {
            return store.get(ruleCode);
        }
    }

    /**
     * 内存版 TraceRecorder（用于端到端测试，作为 AsyncTraceRecorder 的 delegate）
     */
    static class InMemoryTraceRecorder implements com.njydsz.pmis.literule.spi.TraceRecorder {
        final List<com.njydsz.pmis.literule.api.RuleExecutionTrace> traces =
                java.util.Collections.synchronizedList(new ArrayList<>());

        @Override
        public void record(com.njydsz.pmis.literule.api.RuleExecutionTrace trace) {
            traces.add(trace);
        }

        @Override
        public List<com.njydsz.pmis.literule.api.RuleExecutionTrace> getByTraceId(String traceId) {
            return traces.stream().filter(t -> traceId.equals(t.getTraceId())).toList();
        }

        @Override
        public List<com.njydsz.pmis.literule.api.RuleExecutionTrace> getByRuleCode(String ruleCode, int limit) {
            return traces.stream()
                    .filter(t -> ruleCode.equals(t.getRuleCode()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<com.njydsz.pmis.literule.api.RuleExecutionTrace> getRecentTraces(int limit) {
            return traces.stream().limit(limit).toList();
        }
    }
}
