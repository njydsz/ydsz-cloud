package com.njydsz.pmis.literule.server.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.njydsz.pmis.common.exception.observability.TraceContext;
import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleEngineStats;
import com.njydsz.pmis.literule.api.RuleEnvironment;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link DefaultRuleEngine} 单元测试：覆盖规则注册/注销、按优先级执行、互斥组短路、
 * 租户/环境隔离、异常隔离、严重度排序、dry-run、topResult、统计计数与 MDC traceId 传播。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("默认规则引擎 DefaultRuleEngine 测试")
class DefaultRuleEngineTest {

    private DefaultRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DefaultRuleEngine();
        // 清理 MDC，避免上一用例残留
        MDC.clear();
    }

    /**
     * 构造一个 Mockito mock 的 Rule，按入参预设基本元数据
     * （code/name/category/priority/tenant/environment），evaluate 行为默认返回未触发。
     */
    private Rule mockRule(String code, String name, int priority) {
        Rule rule = mock(Rule.class);
        when(rule.getCode()).thenReturn(code);
        when(rule.getName()).thenReturn(name);
        when(rule.getCategory()).thenReturn("TEST");
        when(rule.getPriority()).thenReturn(priority);
        when(rule.getTenantId()).thenReturn("1");
        when(rule.getEnvironment()).thenReturn(RuleEnvironment.DEFAULT);
        // 默认返回未触发，测试用例可再次 stub evaluate
        when(rule.evaluate(org.mockito.ArgumentMatchers.any(RuleContext.class)))
                .thenReturn(RuleResult.notTriggered(code));
        return rule;
    }

    private RuleContext contextWithFacts(Map<String, Object> facts) {
        return RuleContext.of(facts);
    }

    @Nested
    @DisplayName("register / unregister 注册与注销")
    class RegisterUnregisterCases {

        @Test
        @DisplayName("注册 null 规则时静默跳过，不影响已注册规则")
        void shouldSkipNullRule() {
            Rule rule = mockRule("R001", "规则1", 100);
            engine.register(rule);
            engine.register(null);

            assertThat(engine.getRules()).hasSize(1);
            assertThat(engine.getRules().get(0).getCode()).isEqualTo("R001");
        }

        @Test
        @DisplayName("注册 code 为 null 的规则时静默跳过")
        void shouldSkipRuleWithNullCode() {
            Rule rule = mock(Rule.class);
            when(rule.getCode()).thenReturn(null);

            engine.register(rule);

            assertThat(engine.getRules()).isEmpty();
        }

        @Test
        @DisplayName("注册同编码规则时覆盖旧规则")
        void shouldReplaceRuleWithSameCode() {
            Rule oldRule = mockRule("R001", "旧规则", 100);
            Rule newRule = mockRule("R001", "新规则", 50);

            engine.register(oldRule);
            engine.register(newRule);

            assertThat(engine.getRules()).hasSize(1);
            assertThat(engine.getRules().get(0).getName()).isEqualTo("新规则");
            assertThat(engine.getRules().get(0).getPriority()).isEqualTo(50);
        }

        @Test
        @DisplayName("注销指定编码的规则后，规则列表不再包含该规则")
        void shouldRemoveRuleByCode() {
            Rule rule1 = mockRule("R001", "规则1", 100);
            Rule rule2 = mockRule("R002", "规则2", 200);

            engine.register(rule1);
            engine.register(rule2);
            engine.unregister("R001");

            assertThat(engine.getRules()).hasSize(1);
            assertThat(engine.getRules().get(0).getCode()).isEqualTo("R002");
        }

        @Test
        @DisplayName("注销不存在的规则编码不会抛异常")
        void shouldNotThrowWhenUnregisterUnknownCode() {
            Rule rule = mockRule("R001", "规则1", 100);
            engine.register(rule);

            engine.unregister("NOT_EXIST");

            assertThat(engine.getRules()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("evaluate 规则评估")
    class EvaluateCases {

        @Test
        @DisplayName("规则触发：仅返回 triggered=true 的结果")
        void shouldReturnOnlyTriggeredResults() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 1000);

            Rule triggeredRule = mockRule("R001", "触发规则", 100);
            when(triggeredRule.evaluate(org.mockito.ArgumentMatchers.any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R001", "触发规则", "TEST",
                            RuleSeverity.RED, "金额超限", "金额超过阈值"));

            Rule notTriggeredRule = mockRule("R002", "未触发规则", 200);

            engine.register(triggeredRule);
            engine.register(notTriggeredRule);

            List<RuleResult> results = engine.evaluate(contextWithFacts(facts));

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R001");
            assertThat(results.get(0).isTriggered()).isTrue();
        }

        @Test
        @DisplayName("按优先级排序执行：priority 小的先执行")
        void shouldEvaluateByPriorityOrder() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            // 执行顺序记录器
            java.util.List<String> executionOrder = new java.util.concurrent.CopyOnWriteArrayList<>();

            Rule lowPriorityRule = mockRule("R_LOW", "低优先级（先执行）", 10);
            when(lowPriorityRule.evaluate(org.mockito.ArgumentMatchers.any(RuleContext.class)))
                    .thenAnswer(inv -> {
                        executionOrder.add("R_LOW");
                        return RuleResult.triggered("R_LOW", "低优先级", "TEST",
                                RuleSeverity.INFO, "低", "低");
                    });

            Rule highPriorityRule = mockRule("R_HIGH", "高优先级（后执行）", 200);
            when(highPriorityRule.evaluate(org.mockito.ArgumentMatchers.any(RuleContext.class)))
                    .thenAnswer(inv -> {
                        executionOrder.add("R_HIGH");
                        return RuleResult.triggered("R_HIGH", "高优先级", "TEST",
                                RuleSeverity.INFO, "高", "高");
                    });

            // 故意先注册高优先级规则，验证引擎仍按 priority 升序执行
            engine.register(highPriorityRule);
            engine.register(lowPriorityRule);

            engine.evaluate(contextWithFacts(facts));

            assertThat(executionOrder).containsExactly("R_LOW", "R_HIGH");
        }

        @Test
        @DisplayName("互斥组短路：同组首个命中后跳过后续规则")
        void shouldShortCircuitMutexGroup() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            Rule firstInGroup = mockRule("R_FIRST", "组内首条", 10);
            when(firstInGroup.getMutexGroup()).thenReturn("MUTEX_A");
            when(firstInGroup.evaluate(org.mockito.ArgumentMatchers.any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R_FIRST", "组内首条", "TEST",
                            RuleSeverity.YELLOW, "首条命中", "首条命中"));

            Rule secondInGroup = mockRule("R_SECOND", "组内第二条", 20);
            when(secondInGroup.getMutexGroup()).thenReturn("MUTEX_A");
            when(secondInGroup.evaluate(org.mockito.ArgumentMatchers.any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R_SECOND", "组内第二条", "TEST",
                            RuleSeverity.RED, "第二条命中", "第二条命中"));

            engine.register(firstInGroup);
            engine.register(secondInGroup);

            List<RuleResult> results = engine.evaluate(contextWithFacts(facts));

            // 仅首条命中，第二条被短路
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R_FIRST");
        }

        @Test
        @DisplayName("租户隔离：不同租户的规则不评估")
        void shouldIsolateByTenant() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            Rule sameTenantRule = mockRule("R_TENANT1", "租户1规则", 100);
            when(sameTenantRule.evaluate(org.mockito.ArgumentMatchers.any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R_TENANT1", "租户1", "TEST",
                            RuleSeverity.INFO, "命中", "命中"));

            Rule otherTenantRule = mock(Rule.class);
            when(otherTenantRule.getCode()).thenReturn("R_TENANT2");
            when(otherTenantRule.getName()).thenReturn("租户2规则");
            when(otherTenantRule.getCategory()).thenReturn("TEST");
            when(otherTenantRule.getPriority()).thenReturn(100);
            when(otherTenantRule.getTenantId()).thenReturn("2");
            when(otherTenantRule.getEnvironment()).thenReturn(RuleEnvironment.DEFAULT);
            when(otherTenantRule.evaluate(org.mockito.ArgumentMatchers.any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R_TENANT2", "租户2", "TEST",
                            RuleSeverity.INFO, "不应被评估", "不应被评估"));

            engine.register(sameTenantRule);
            engine.register(otherTenantRule);

            // 上下文租户为 "1"
            RuleContext ctx = RuleContext.of(facts, "DEFAULT", "TEST", "trace-1", "1");
            List<RuleResult> results = engine.evaluate(ctx);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R_TENANT1");
        }

        @Test
        @DisplayName("环境隔离：非 default 环境必须完全匹配")
        void shouldIsolateByEnvironmentWhenNotDefault() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            // environment=default 匹配任何上下文环境
            Rule defaultEnvRule = mockRule("R_DEFAULT", "默认环境规则", 100);
            when(defaultEnvRule.evaluate(org.mockito.ArgumentMatchers.any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R_DEFAULT", "默认", "TEST",
                            RuleSeverity.INFO, "默认命中", "默认命中"));

            // environment=prod 仅匹配上下文 prod 环境
            Rule prodEnvRule = mock(Rule.class);
            when(prodEnvRule.getCode()).thenReturn("R_PROD");
            when(prodEnvRule.getName()).thenReturn("生产环境规则");
            when(prodEnvRule.getCategory()).thenReturn("TEST");
            when(prodEnvRule.getPriority()).thenReturn(100);
            when(prodEnvRule.getTenantId()).thenReturn("1");
            when(prodEnvRule.getEnvironment()).thenReturn("prod");
            when(prodEnvRule.evaluate(org.mockito.ArgumentMatchers.any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R_PROD", "生产", "TEST",
                            RuleSeverity.INFO, "生产命中", "生产命中"));

            engine.register(defaultEnvRule);
            engine.register(prodEnvRule);

            // 上下文环境为 dev，prod 规则不应被评估
            RuleContext ctx = RuleContext.of(facts, "DEFAULT", "TEST", "trace-1", "1", "dev");
            List<RuleResult> results = engine.evaluate(ctx);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R_DEFAULT");
        }

        @Test
        @DisplayName("环境隔离：上下文环境与规则环境完全匹配时被评估")
        void shouldEvaluateWhenEnvironmentMatches() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            Rule prodRule = mock(Rule.class);
            when(prodRule.getCode()).thenReturn("R_PROD");
            when(prodRule.getName()).thenReturn("生产环境规则");
            when(prodRule.getCategory()).thenReturn("TEST");
            when(prodRule.getPriority()).thenReturn(100);
            when(prodRule.getTenantId()).thenReturn("1");
            when(prodRule.getEnvironment()).thenReturn("prod");
            when(prodRule.evaluate(org.mockito.ArgumentMatchers.any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R_PROD", "生产", "TEST",
                            RuleSeverity.RED, "生产命中", "生产命中"));

            engine.register(prodRule);

            RuleContext ctx = RuleContext.of(facts, "DEFAULT", "TEST", "trace-1", "1", "prod");
            List<RuleResult> results = engine.evaluate(ctx);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R_PROD");
        }

        @Test
        @DisplayName("异常隔离：单规则异常不影响其他规则评估")
        void shouldIsolateExceptionPerRule() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            Rule throwingRule = mockRule("R_THROW", "异常规则", 100);
            when(throwingRule.evaluate(org.mockito.ArgumentMatchers.any(RuleContext.class)))
                    .thenThrow(new RuntimeException("规则内部异常"));

            Rule normalRule = mockRule("R_NORMAL", "正常规则", 200);
            when(normalRule.evaluate(org.mockito.ArgumentMatchers.any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R_NORMAL", "正常", "TEST",
                            RuleSeverity.YELLOW, "正常命中", "正常命中"));

            engine.register(throwingRule);
            engine.register(normalRule);

            List<RuleResult> results = engine.evaluate(contextWithFacts(facts));

            // 异常规则不返回结果，正常规则仍被评估
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R_NORMAL");
        }

        @Test
        @DisplayName("结果按严重度倒序排列：RED → YELLOW → INFO")
        void shouldSortResultsBySeverityDesc() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            Rule infoRule = mockRule("R_INFO", "INFO规则", 100);
            when(infoRule.evaluate(org.mockito.ArgumentMatchers.any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R_INFO", "INFO", "TEST",
                            RuleSeverity.INFO, "信息", "信息"));

            Rule redRule = mockRule("R_RED", "RED规则", 200);
            when(redRule.evaluate(org.mockito.ArgumentMatchers.any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R_RED", "RED", "TEST",
                            RuleSeverity.RED, "严重", "严重"));

            Rule yellowRule = mockRule("R_YELLOW", "YELLOW规则", 300);
            when(yellowRule.evaluate(org.mockito.ArgumentMatchers.any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R_YELLOW", "YELLOW", "TEST",
                            RuleSeverity.YELLOW, "预警