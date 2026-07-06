package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.expr.AviatorExpressionEvaluator;
import com.njydsz.pmis.literule.impl.ExpressionRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 多租户运行时隔离测试（P2-14）
 *
 * <p>验证 1.5.0 起启用租户过滤后的核心行为：
 * <ul>
 *   <li>默认租户 1L 向后兼容：规则与上下文均为 1L 时正常评估</li>
 *   <li>跨租户规则不评估：rule.tenantId != context.tenantId 时跳过</li>
 *   <li>同租户规则正常评估：rule.tenantId == context.tenantId 时放行</li>
 *   <li>dryRun 同样遵循租户隔离</li>
 *   <li>ExpressionRule 从 RuleDefinition.tenantId 取值</li>
 *   <li>RuleContext.of 默认租户 1L（向后兼容）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
class RuleTenantIsolationTest {

    private DefaultRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DefaultRuleEngine();
        engine.resetStats();
    }

    // ============ 默认租户 1L 向后兼容 ============

    @Test
    @DisplayName("默认租户 - 规则与上下文均为默认 1L 时正常评估")
    void defaultTenantShouldEvaluateNormally() {
        Rule rule = createTriggeredRule("R001", "默认租户规则", 1L);
        engine.register(rule);

        // RuleContext.of 默认 tenantId=1L
        RuleContext ctx = RuleContext.of(Map.of("amount", 1000));
        assertThat(ctx.getTenantId()).isEqualTo(1L);

        List<RuleResult> results = engine.evaluate(ctx);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRuleCode()).isEqualTo("R001");
        assertThat(results.get(0).isTriggered()).isTrue();
    }

    @Test
    @DisplayName("默认租户 - 未覆写 getTenantId 的编码规则使用默认 1L")
    void ruleWithoutTenantOverrideShouldUseDefault() {
        // 不覆写 getTenantId，使用接口默认 1L
        Rule rule = createTriggeredRuleNoTenantOverride("R001", "编码规则");
        engine.register(rule);

        RuleContext ctx = RuleContext.of(Map.of("amount", 1000));
        List<RuleResult> results = engine.evaluate(ctx);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).isTriggered()).isTrue();
    }

    // ============ 跨租户隔离 ============

    @Test
    @DisplayName("跨租户 - rule.tenantId=2 与 context.tenantId=1 不匹配时跳过评估")
    void crossTenantShouldSkipEvaluation() {
        Rule tenant2Rule = createTriggeredRule("R_T2", "租户2规则", 2L);
        engine.register(tenant2Rule);

        // 上下文为默认租户 1L
        RuleContext ctx = RuleContext.of(Map.of("amount", 1000));
        List<RuleResult> results = engine.evaluate(ctx);

        // 租户 2 的规则不应被评估
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("跨租户 - rule.tenantId=1 与 context.tenantId=2 不匹配时跳过评估")
    void crossTenantReverseShouldSkipEvaluation() {
        Rule tenant1Rule = createTriggeredRule("R_T1", "租户1规则", 1L);
        engine.register(tenant1Rule);

        // 上下文为租户 2
        RuleContext ctx = RuleContext.of(Map.of("amount", 1000), "DEFAULT", "TEST",
                "trace-1", 2L);
        List<RuleResult> results = engine.evaluate(ctx);

        // 租户 1 的规则不应被评估
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("混合租户 - 仅评估与上下文租户匹配的规则")
    void mixedTenantsShouldOnlyEvaluateMatching() {
        Rule t1RuleA = createTriggeredRule("R_T1_A", "租户1规则A", 1L);
        Rule t1RuleB = createTriggeredRule("R_T1_B", "租户1规则B", 1L);
        Rule t2Rule = createTriggeredRule("R_T2", "租户2规则", 2L);
        Rule t3Rule = createTriggeredRule("R_T3", "租户3规则", 3L);
        engine.register(t1RuleA);
        engine.register(t1RuleB);
        engine.register(t2Rule);
        engine.register(t3Rule);

        // 上下文为租户 1
        RuleContext ctx = RuleContext.of(Map.of("amount", 1000));
        List<RuleResult> results = engine.evaluate(ctx);

        // 仅租户 1 的两条规则被评估
        assertThat(results).hasSize(2);
        assertThat(results).extracting(RuleResult::getRuleCode)
                .containsExactlyInAnyOrder("R_T1_A", "R_T1_B");
    }

    @Test
    @DisplayName("混合租户 - 切换上下文租户后评估对应规则")
    void switchTenantContextShouldEvaluateCorrespondingRules() {
        Rule t1Rule = createTriggeredRule("R_T1", "租户1规则", 1L);
        Rule t2Rule = createTriggeredRule("R_T2", "租户2规则", 2L);
        engine.register(t1Rule);
        engine.register(t2Rule);

        // 上下文为租户 1
        RuleContext ctx1 = RuleContext.of(Map.of("amount", 1000));
        List<RuleResult> results1 = engine.evaluate(ctx1);
        assertThat(results1).hasSize(1);
        assertThat(results1.get(0).getRuleCode()).isEqualTo("R_T1");

        // 上下文切换为租户 2
        RuleContext ctx2 = RuleContext.of(Map.of("amount", 1000), "DEFAULT", "TEST",
                "trace-2", 2L);
        List<RuleResult> results2 = engine.evaluate(ctx2);
        assertThat(results2).hasSize(1);
        assertThat(results2.get(0).getRuleCode()).isEqualTo("R_T2");
    }

    // ============ dryRun 租户隔离 ============

    @Test
    @DisplayName("dryRun - 同样遵循租户隔离")
    void dryRunShouldRespectTenantIsolation() {
        Rule t1Rule = createTriggeredRule("R_T1", "租户1规则", 1L);
        Rule t2Rule = createTriggeredRule("R_T2", "租户2规则", 2L);
        engine.register(t1Rule);
        engine.register(t2Rule);

        // 上下文为默认租户 1L
        List<RuleResult> results = engine.dryRun(RuleContext.of(Map.of("amount", 1000)));

        // dryRun 仅返回租户 1 的规则结果
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRuleCode()).isEqualTo("R_T1");
    }

    @Test
    @DisplayName("dryRun - 跨租户规则不出现")
    void dryRunShouldExcludeCrossTenantRules() {
        Rule t2Rule = createTriggeredRule("R_T2", "租户2规则", 2L);
        engine.register(t2Rule);

        // 上下文为默认租户 1L
        List<RuleResult> results = engine.dryRun(RuleContext.of(Map.of("amount", 1000)));

        assertThat(results).isEmpty();
    }

    // ============ ExpressionRule 租户隔离 ============

    @Test
    @DisplayName("ExpressionRule - 从 RuleDefinition.tenantId 取值")
    void expressionRuleShouldReadTenantFromDefinition() {
        AviatorExpressionEvaluator evaluator = new AviatorExpressionEvaluator(false);

        // 构造 tenantId=2 的规则定义
        RuleDefinition def = RuleDefinition.builder()
                .code("R_EXPR_T2")
                .name("租户2表达式规则")
                .conditionExpression("amount > 100")
                .severityExpression("RED")
                .tenantId(2L)
                .build();

        ExpressionRule rule = new ExpressionRule(def, evaluator);
        assertThat(rule.getTenantId()).isEqualTo(2L);

        engine.register(rule);

        // 上下文为租户 1：不应评估
        RuleContext ctx1 = RuleContext.of(Map.of("amount", 1000));
        assertThat(engine.evaluate(ctx1)).isEmpty();

        // 上下文为租户 2：应评估
        RuleContext ctx2 = RuleContext.of(Map.of("amount", 1000), "DEFAULT", "TEST",
                "trace-2", 2L);
        List<RuleResult> results = engine.evaluate(ctx2);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRuleCode()).isEqualTo("R_EXPR_T2");
        assertThat(results.get(0).isTriggered()).isTrue();
    }

    @Test
    @DisplayName("ExpressionRule - 默认 tenantId=1L 向后兼容")
    void expressionRuleDefaultTenantShouldBeOne() {
        AviatorExpressionEvaluator evaluator = new AviatorExpressionEvaluator(false);

        // 不指定 tenantId，使用默认值 1L
        RuleDefinition def = RuleDefinition.builder()
                .code("R_EXPR_DEFAULT")
                .name("默认租户表达式规则")
                .conditionExpression("amount > 100")
                .severityExpression("INFO")
                .build();

        ExpressionRule rule = new ExpressionRule(def, evaluator);
        assertThat(rule.getTenantId()).isEqualTo(1L);

        engine.register(rule);

        // 上下文为默认租户 1L：应评估
        RuleContext ctx = RuleContext.of(Map.of("amount", 1000));
        List<RuleResult> results = engine.evaluate(ctx);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).isTriggered()).isTrue();
    }

    // ============ RuleContext 默认值 ============

    @Test
    @DisplayName("RuleContext - of(facts) 默认 tenantId=1L")
    void ruleContextOfFactsShouldUseDefaultTenant() {
        RuleContext ctx = RuleContext.of(Map.of("k", "v"));
        assertThat(ctx.getTenantId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("RuleContext - of(facts, scenario, source) 默认 tenantId=1L")
    void ruleContextOfThreeArgsShouldUseDefaultTenant() {
        RuleContext ctx = RuleContext.of(Map.of("k", "v"), "COCKPIT", "TEST");
        assertThat(ctx.getTenantId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("RuleContext - of(facts, scenario, source, traceId) 默认 tenantId=1L")
    void ruleContextOfFourArgsShouldUseDefaultTenant() {
        RuleContext ctx = RuleContext.of(Map.of("k", "v"), "COCKPIT", "TEST", "trace-1");
        assertThat(ctx.getTenantId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("RuleContext - of(facts, scenario, source, traceId, tenantId) 指定租户")
    void ruleContextOfFiveArgsShouldUseSpecifiedTenant() {
        RuleContext ctx = RuleContext.of(Map.of("k", "v"), "COCKPIT", "TEST", "trace-1", 99L);
        assertThat(ctx.getTenantId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("RuleContext - toString 包含 tenantId")
    void ruleContextToStringShouldContainTenantId() {
        RuleContext ctx = RuleContext.of(Map.of("k", "v"), "DEFAULT", "TEST", "trace-1", 5L);
        String str = ctx.toString();
        assertThat(str).contains("tenantId=5");
    }

    // ============ 租户隔离 + 场景过滤组合 ============

    @Test
    @DisplayName("组合 - 租户过滤 + 场景过滤共同生效")
    void tenantAndScenarioFilterShouldBothApply() {
        // 租户 1 + COCKPIT 场景
        Rule t1Cockpit = createRuleWithScopeAndTenant("R_T1_CK", "租户1 Cockpit", "COCKPIT", 1L, true);
        // 租户 1 + BUDGET 场景（不匹配 COCKPIT 上下文）
        Rule t1Budget = createRuleWithScopeAndTenant("R_T1_BG", "租户1 Budget", "BUDGET", 1L, true);
        // 租户 2 + COCKPIT 场景（不匹配租户 1 上下文）
        Rule t2Cockpit = createRuleWithScopeAndTenant("R_T2_CK", "租户2 Cockpit", "COCKPIT", 2L, true);
        engine.register(t1Cockpit);
        engine.register(t1Budget);
        engine.register(t2Cockpit);

        // 上下文：租户 1 + COCKPIT 场景
        RuleContext ctx = RuleContext.of(Map.of("amount", 1000), "COCKPIT", "TEST");
        List<RuleResult> results = engine.evaluate(ctx);

        // 仅 R_T1_CK 同时满足租户与场景
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRuleCode()).isEqualTo("R_T1_CK");
    }

    // ============ 统计不受租户过滤影响 ============

    @Test
    @DisplayName("统计 - 跨租户跳过的规则不计入评估统计")
    void statsShouldNotCountSkippedTenantRules() {
        Rule t1Rule = createTriggeredRule("R_T1", "租户1规则", 1L);
        Rule t2Rule = createTriggeredRule("R_T2", "租户2规则", 2L);
        engine.register(t1Rule);
        engine.register(t2Rule);

        // 上下文为租户 1
        engine.evaluate(RuleContext.of(Map.of("amount", 1000)));

        // 仅评估了 1 条规则（租户 1 的）
        assertThat(engine.getStats().getTotalEvaluations()).isEqualTo(1);
        assertThat(engine.getStats().getTotalTriggered()).isEqualTo(1);
    }

    // ============ 辅助方法 ============

    /**
     * 创建触发型规则，可指定 tenantId
     */
    private Rule createTriggeredRule(String code, String name, long tenantId) {
        return new Rule() {
            @Override
            public String getCode() { return code; }
            @Override
            public String getName() { return name; }
            @Override
            public String getCategory() { return "TEST"; }
            @Override
            public int getPriority() { return 100; }
            @Override
            public long getTenantId() { return tenantId; }
            @Override
            public RuleResult evaluate(RuleContext context) {
                return RuleResult.triggered(code, name, "TEST", RuleSeverity.INFO, name, "触发");
            }
        };
    }

    /**
     * 创建触发型规则，不覆写 getTenantId（使用接口默认 1L）
     */
    private Rule createTriggeredRuleNoTenantOverride(String code, String name) {
        return new Rule() {
            @Override
            public String getCode() { return code; }
            @Override
            public String getName() { return name; }
            @Override
            public String getCategory() { return "TEST"; }
            @Override
            public int getPriority() { return 100; }
            @Override
            public RuleResult evaluate(RuleContext context) {
                return RuleResult.triggered(code, name, "TEST", RuleSeverity.INFO, name, "触发");
            }
        };
    }

    /**
     * 创建带 scope 和 tenantId 的触发型规则
     */
    private Rule createRuleWithScopeAndTenant(String code, String name, String scope,
                                               long tenantId, boolean triggered) {
        return new Rule() {
            @Override
            public String getCode() { return code; }
            @Override
            public String getName() { return name; }
            @Override
            public String getCategory() { return "TEST"; }
            @Override
            public int getPriority() { return 100; }
            @Override
            public String getScope() { return scope; }
            @Override
            public long getTenantId() { return tenantId; }
            @Override
            public RuleResult evaluate(RuleContext context) {
                if (!triggered) {
                    return RuleResult.notTriggered(code);
                }
                return RuleResult.triggered(code, name, "TEST", RuleSeverity.INFO, name, "触发");
            }
        };
    }
}
