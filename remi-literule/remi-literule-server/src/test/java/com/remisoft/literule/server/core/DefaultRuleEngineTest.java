package com.remisoft.literule.server.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.remisoft.common.core.constant.HeaderConstants;
import com.remisoft.literule.api.Rule;
import com.remisoft.literule.api.RuleContext;
import com.remisoft.literule.api.RuleEngineStats;
import com.remisoft.literule.api.RuleEnvironment;
import com.remisoft.literule.api.RuleResult;
import com.remisoft.literule.api.RuleSeverity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link DefaultRuleEngine} 单元测试：覆盖规则注册/注销、按优先级执行、互斥组短路、
 * 租户/环境隔离、异常隔离、严重度排序、dry-run、topResult、统计计数与 MDC traceId 传播。
 *
 * @since 1.0.0
 * @author remi-team
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
        when(rule.evaluate(any(RuleContext.class)))
                .thenReturn(RuleResult.notTriggered(code));
        return rule;
    }

    private RuleContext contextWithFacts(Map<String, Object> facts) {
        return RuleContext.of(facts);
    }

    @Nested
    @DisplayName("register / unregister 注册与注销")
    /**
     * 测试分组：register / unregister 注册与注销
     */
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
    /**
     * 测试分组：evaluate 规则评估
     */
    class EvaluateCases {

        @Test
        @DisplayName("规则触发：仅返回 triggered=true 的结果")
        void shouldReturnOnlyTriggeredResults() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 1000);

            Rule triggeredRule = mockRule("R001", "触发规则", 100);
            when(triggeredRule.evaluate(any(RuleContext.class)))
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
            List<String> executionOrder = new CopyOnWriteArrayList<>();

            Rule lowPriorityRule = mockRule("R_LOW", "低优先级（先执行）", 10);
            when(lowPriorityRule.evaluate(any(RuleContext.class)))
                    .thenAnswer(inv -> {
                        executionOrder.add("R_LOW");
                        return RuleResult.triggered("R_LOW", "低优先级", "TEST",
                                RuleSeverity.INFO, "低", "低");
                    });

            Rule highPriorityRule = mockRule("R_HIGH", "高优先级（后执行）", 200);
            when(highPriorityRule.evaluate(any(RuleContext.class)))
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
            when(firstInGroup.evaluate(any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R_FIRST", "组内首条", "TEST",
                            RuleSeverity.YELLOW, "首条命中", "首条命中"));

            Rule secondInGroup = mockRule("R_SECOND", "组内第二条", 20);
            when(secondInGroup.getMutexGroup()).thenReturn("MUTEX_A");
            when(secondInGroup.evaluate(any(RuleContext.class)))
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
            when(sameTenantRule.evaluate(any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R_TENANT1", "租户1", "TEST",
                            RuleSeverity.INFO, "命中", "命中"));

            Rule otherTenantRule = mock(Rule.class);
            when(otherTenantRule.getCode()).thenReturn("R_TENANT2");
            when(otherTenantRule.getName()).thenReturn("租户2规则");
            when(otherTenantRule.getCategory()).thenReturn("TEST");
            when(otherTenantRule.getPriority()).thenReturn(100);
            when(otherTenantRule.getTenantId()).thenReturn("2");
            when(otherTenantRule.getEnvironment()).thenReturn(RuleEnvironment.DEFAULT);
            when(otherTenantRule.evaluate(any(RuleContext.class)))
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
            when(defaultEnvRule.evaluate(any(RuleContext.class)))
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
            when(prodEnvRule.evaluate(any(RuleContext.class)))
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
            when(prodRule.evaluate(any(RuleContext.class)))
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
            when(throwingRule.evaluate(any(RuleContext.class)))
                    .thenThrow(new RuntimeException("规则内部异常"));

            Rule normalRule = mockRule("R_NORMAL", "正常规则", 200);
            when(normalRule.evaluate(any(RuleContext.class)))
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
            when(infoRule.evaluate(any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R_INFO", "INFO", "TEST",
                            RuleSeverity.INFO, "信息", "信息"));

            Rule redRule = mockRule("R_RED", "RED规则", 200);
            when(redRule.evaluate(any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R_RED", "RED", "TEST",
                            RuleSeverity.RED, "严重", "严重"));

            Rule yellowRule = mockRule("R_YELLOW", "YELLOW规则", 300);
            when(yellowRule.evaluate(any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R_YELLOW", "YELLOW", "TEST",
                            RuleSeverity.YELLOW, "预警", "预警"));

            engine.register(infoRule);
            engine.register(redRule);
            engine.register(yellowRule);

            List<RuleResult> results = engine.evaluate(contextWithFacts(facts));

            assertThat(results).hasSize(3);
            assertThat(results.get(0).getSeverity()).isEqualTo(RuleSeverity.RED);
            assertThat(results.get(1).getSeverity()).isEqualTo(RuleSeverity.YELLOW);
            assertThat(results.get(2).getSeverity()).isEqualTo(RuleSeverity.INFO);
        }
    }

    @Nested
    @DisplayName("dryRun 仿真评估")
    /**
     * 测试分组：dryRun 仿真评估
     */
    class DryRunCases {

        @Test
        @DisplayName("dryRun 返回全部匹配规则的结果（含未触发）")
        void shouldReturnAllResultsIncludingNotTriggered() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            Rule triggeredRule = mockRule("R001", "触发规则", 100);
            when(triggeredRule.evaluate(any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R001", "触发", "TEST",
                            RuleSeverity.INFO, "命中", "命中"));

            Rule notTriggeredRule = mockRule("R002", "未触发规则", 200);

            engine.register(triggeredRule);
            engine.register(notTriggeredRule);

            List<RuleResult> results = engine.dryRun(contextWithFacts(facts));

            // dryRun 返回全部结果，含未触发
            assertThat(results).hasSize(2);
            assertThat(results).extracting(RuleResult::getRuleCode)
                    .containsExactlyInAnyOrder("R001", "R002");
        }

        @Test
        @DisplayName("dryRun 不记录统计：getStats 计数应保持为 0")
        void shouldNotRecordStatsOnDryRun() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            Rule rule = mockRule("R001", "规则", 100);
            when(rule.evaluate(any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R001", "规则", "TEST",
                            RuleSeverity.INFO, "命中", "命中"));

            engine.register(rule);
            engine.dryRun(contextWithFacts(facts));

            RuleEngineStats stats = engine.getStats();
            assertThat(stats.getTotalEvaluations()).isZero();
            assertThat(stats.getTotalTriggered()).isZero();
        }
    }

    @Nested
    @DisplayName("topResult 取最高严重度结果")
    /**
     * 测试分组：topResult 取最高严重度结果
     */
    class TopResultCases {

        @Test
        @DisplayName("topResult 返回最高严重度的结果")
        void shouldReturnHighestSeverityResult() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            Rule infoRule = mockRule("R_INFO", "INFO", 100);
            when(infoRule.evaluate(any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R_INFO", "INFO", "TEST",
                            RuleSeverity.INFO, "信息", "信息"));

            Rule redRule = mockRule("R_RED", "RED", 200);
            when(redRule.evaluate(any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R_RED", "RED", "TEST",
                            RuleSeverity.RED, "严重", "严重"));

            engine.register(infoRule);
            engine.register(redRule);

            RuleResult top = engine.topResult(contextWithFacts(facts));

            assertThat(top).isNotNull();
            assertThat(top.getSeverity()).isEqualTo(RuleSeverity.RED);
        }

        @Test
        @DisplayName("无规则触发时 topResult 返回 null")
        void shouldReturnNullWhenNoTriggered() {
            Rule rule = mockRule("R001", "未触发规则", 100);
            engine.register(rule);

            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            assertThat(engine.topResult(contextWithFacts(facts))).isNull();
        }
    }

    @Nested
    @DisplayName("getStats 统计计数")
    /**
     * 测试分组：getStats 统计计数
     */
    class StatsCases {

        @Test
        @DisplayName("evaluate 后统计计数正确：评估次数、触发次数")
        void shouldRecordCorrectCountsAfterEvaluate() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            Rule triggeredRule = mockRule("R001", "触发", 100);
            when(triggeredRule.evaluate(any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R001", "触发", "TEST",
                            RuleSeverity.INFO, "命中", "命中"));

            Rule notTriggeredRule = mockRule("R002", "未触发", 200);

            engine.register(triggeredRule);
            engine.register(notTriggeredRule);

            engine.evaluate(contextWithFacts(facts));

            RuleEngineStats stats = engine.getStats();
            // 两条规则被评估
            assertThat(stats.getTotalEvaluations()).isEqualTo(2);
            // 一条规则触发
            assertThat(stats.getTotalTriggered()).isEqualTo(1);
            // 无异常
            assertThat(stats.getTotalErrors()).isZero();
            // 注册规则数
            assertThat(stats.getRegisteredRules()).isEqualTo(2);
            // 按规则明细：R001 触发 1 次
            RuleEngineStats.RuleStat r001Stat = stats.getPerRuleStats().get("R001");
            assertThat(r001Stat).isNotNull();
            assertThat(r001Stat.getExecutions()).isEqualTo(1);
            assertThat(r001Stat.getTriggered()).isEqualTo(1);
        }

        @Test
        @DisplayName("规则抛异常时 totalErrors 递增，但其他规则仍记录执行次数")
        void shouldRecordErrorsWhenRuleThrows() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            Rule throwingRule = mockRule("R_THROW", "异常规则", 100);
            doThrow(new RuntimeException("boom"))
                    .when(throwingRule)
                    .evaluate(any(RuleContext.class));

            Rule normalRule = mockRule("R_NORMAL", "正常规则", 200);
            when(normalRule.evaluate(any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R_NORMAL", "正常", "TEST",
                            RuleSeverity.INFO, "命中", "命中"));

            engine.register(throwingRule);
            engine.register(normalRule);

            engine.evaluate(contextWithFacts(facts));

            RuleEngineStats stats = engine.getStats();
            assertThat(stats.getTotalEvaluations()).isEqualTo(2);
            assertThat(stats.getTotalErrors()).isEqualTo(1);
            assertThat(stats.getTotalTriggered()).isEqualTo(1);
        }

        @Test
        @DisplayName("resetStats 清空所有统计计数")
        void shouldResetAllStats() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            Rule rule = mockRule("R001", "规则", 100);
            when(rule.evaluate(any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R001", "规则", "TEST",
                            RuleSeverity.INFO, "命中", "命中"));

            engine.register(rule);
            engine.evaluate(contextWithFacts(facts));

            engine.resetStats();

            RuleEngineStats stats = engine.getStats();
            assertThat(stats.getTotalEvaluations()).isZero();
            assertThat(stats.getTotalTriggered()).isZero();
            assertThat(stats.getTotalErrors()).isZero();
            assertThat(stats.getPerRuleStats()).isEmpty();
        }

        @Test
        @DisplayName("setStatsEnabled(false) 关闭统计后不再记录计数")
        void shouldNotRecordWhenStatsDisabled() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            Rule rule = mockRule("R001", "规则", 100);
            when(rule.evaluate(any(RuleContext.class)))
                    .thenReturn(RuleResult.triggered("R001", "规则", "TEST",
                            RuleSeverity.INFO, "命中", "命中"));

            engine.register(rule);
            engine.setStatsEnabled(false);

            engine.evaluate(contextWithFacts(facts));

            RuleEngineStats stats = engine.getStats();
            assertThat(stats.getTotalEvaluations()).isZero();
            assertThat(stats.getTotalTriggered()).isZero();
        }
    }

    @Nested
    @DisplayName("MDC traceId 传播")
    /**
     * 测试分组：MDC traceId 传播
     */
    class MdcTraceIdCases {

        @Test
        @DisplayName("evaluate 后 MDC 恢复为原状（原有 traceId 被还原）")
        void shouldRestoreMdcAfterEvaluate() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            // 预设 MDC 中已有 traceId
            MDC.put(HeaderConstants.MDC_TRACE_ID_KEY, "previous-trace");

            Rule rule = mockRule("R001", "规则", 100);
            engine.register(rule);

            // 上下文显式指定 traceId
            RuleContext ctx = RuleContext.of(facts, "DEFAULT", "TEST", "evaluate-trace", "1");
            engine.evaluate(ctx);

            // evaluate 后 MDC 应恢复为原值
            assertThat(MDC.get(HeaderConstants.MDC_TRACE_ID_KEY)).isEqualTo("previous-trace");
        }

        @Test
        @DisplayName("evaluate 期间 MDC traceId 为上下文 traceId")
        void shouldSetMdcTraceIdDuringEvaluate() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            // 捕获 evaluate 期间的 MDC traceId
            AtomicReference<String> capturedTraceId = new AtomicReference<>();

            Rule rule = mockRule("R001", "规则", 100);
            when(rule.evaluate(any(RuleContext.class)))
                    .thenAnswer(inv -> {
                        capturedTraceId.set(MDC.get(HeaderConstants.MDC_TRACE_ID_KEY));
                        return RuleResult.notTriggered("R001");
                    });

            engine.register(rule);

            RuleContext ctx = RuleContext.of(facts, "DEFAULT", "TEST", "in-eval-trace", "1");
            engine.evaluate(ctx);

            // evaluate 期间 MDC 应被设置为上下文 traceId
            assertThat(capturedTraceId.get()).isEqualTo("in-eval-trace");
        }

        @Test
        @DisplayName("evaluate 前未设置 MDC traceId，evaluate 后 MDC traceId 被清理")
        void shouldClearMdcWhenNoPreviousTraceId() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("v", 1);

            // 确保初始 MDC 无 traceId
            MDC.remove(HeaderConstants.MDC_TRACE_ID_KEY);

            Rule rule = mockRule("R001", "规则", 100);
            engine.register(rule);

            RuleContext ctx = RuleContext.of(facts, "DEFAULT", "TEST", "trace-to-clear", "1");
            engine.evaluate(ctx);

            // evaluate 后 MDC traceId 应被清理（恢复为原状即 null）
            assertThat(MDC.get(HeaderConstants.MDC_TRACE_ID_KEY)).isNull();
        }
    }
}
