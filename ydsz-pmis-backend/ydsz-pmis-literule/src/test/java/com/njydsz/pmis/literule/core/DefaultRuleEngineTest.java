package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleEngineStats;
import com.njydsz.pmis.literule.api.RuleExecutionTrace;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.api.StatsRecorder;
import com.njydsz.pmis.literule.spi.TraceRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DefaultRuleEngine 单元测试
 *
 * <p>测试规则引擎的注册/注销、优先级编排、互斥组短路、租户隔离、场景过滤、
 * 灰度路由、索引优化、统计记录、轨迹记录、断点调试、异常隔离、资源释放等核心能力，
 * 目标覆盖率 100%。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("DefaultRuleEngine 单元测试")
class DefaultRuleEngineTest {

    private DefaultRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DefaultRuleEngine();
    }

    @AfterEach
    void tearDown() {
        // 释放引擎资源（避免 AsyncTraceRecorder 线程泄漏）
        engine.destroy();
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造 Rule mock 测试桩（默认租户 1、scope=null、无互斥组）
     *
     * @param code     规则编码
     * @param priority 优先级
     * @return Rule mock
     */
    private Rule mockRule(String code, int priority) {
        Rule rule = Mockito.mock(Rule.class);
        when(rule.getCode()).thenReturn(code);
        when(rule.getName()).thenReturn("规则-" + code);
        when(rule.getCategory()).thenReturn("TEST");
        when(rule.getPriority()).thenReturn(priority);
        when(rule.getTenantId()).thenReturn("1");
        return rule;
    }

    /**
     * 构造 Rule mock 测试桩（带触发结果）
     */
    private Rule mockTriggeredRule(String code, int priority, RuleSeverity severity) {
        Rule rule = mockRule(code, priority);
        RuleResult result = RuleResult.builder()
                .ruleCode(code)
                .ruleName("规则-" + code)
                .category("TEST")
                .triggered(true)
                .severity(severity)
                .title("标题-" + code)
                .description("描述-" + code)
                .threshold("amount > 100")
                .build();
        when(rule.evaluate(any())).thenReturn(result);
        return rule;
    }

    /**
     * 构造未触发的 Rule mock
     */
    private Rule mockNotTriggeredRule(String code, int priority) {
        Rule rule = mockRule(code, priority);
        when(rule.evaluate(any())).thenReturn(RuleResult.notTriggered(code));
        return rule;
    }

    /**
     * 构造默认上下文（租户 1、场景 DEFAULT）
     */
    private RuleContext defaultContext() {
        Map<String, Object> facts = new HashMap<>();
        facts.put("amount", 1000);
        return RuleContext.of(facts, "DEFAULT", "TEST", "trace-1", "1");
    }

    /**
     * 构造指定租户的上下文
     */
    private RuleContext contextWithTenant(String tenantId) {
        return RuleContext.of(new HashMap<>(), "DEFAULT", "TEST", "trace-1", tenantId);
    }

    /**
     * 构造指定场景的上下文
     */
    private RuleContext contextWithScenario(String scenario) {
        return RuleContext.of(new HashMap<>(), scenario, "TEST", "trace-1", "1");
    }

    // ==================== 规则注册与注销 ====================

    @Nested
    @DisplayName("规则注册与注销")
    class RegisterTest {

        @Test
        @DisplayName("register null 规则 - 空操作")
        void shouldNotRegisterNullRule() {
            engine.register(null);
            assertThat(engine.getRules()).isEmpty();
        }

        @Test
        @DisplayName("register code 为 null - 空操作")
        void shouldNotRegisterRuleWithNullCode() {
            Rule rule = Mockito.mock(Rule.class);
            when(rule.getCode()).thenReturn(null);

            engine.register(rule);
            assertThat(engine.getRules()).isEmpty();
        }

        @Test
        @DisplayName("注册有效规则 - 出现在 getRules 中")
        void shouldRegisterValidRule() {
            Rule rule = mockRule("R1", 100);

            engine.register(rule);

            assertThat(engine.getRules()).hasSize(1);
            assertThat(engine.getRules().get(0).getCode()).isEqualTo("R1");
        }

        @Test
        @DisplayName("注册同编码规则 - 热更新覆盖")
        void shouldReplaceRuleWithSameCode() {
            Rule rule1 = mockRule("R1", 100);
            Rule rule2 = mockRule("R1", 200);

            engine.register(rule1);
            engine.register(rule2);

            assertThat(engine.getRules()).hasSize(1);
            assertThat(engine.getRules().get(0).getPriority()).isEqualTo(200);
        }

        @Test
        @DisplayName("unregister null 编码 - 空操作")
        void shouldNotUnregisterNullCode() {
            engine.unregister(null);
            // 无异常即通过
        }

        @Test
        @DisplayName("注销已存在规则 - 从列表中移除")
        void shouldUnregisterExistingRule() {
            Rule rule = mockRule("R1", 100);
            engine.register(rule);

            engine.unregister("R1");

            assertThat(engine.getRules()).isEmpty();
        }

        @Test
        @DisplayName("注销不存在的规则 - 无副作用")
        void shouldNotFailWhenUnregisteringNonExistentRule() {
            engine.register(mockRule("R1", 100));

            engine.unregister("NON_EXISTENT");

            assertThat(engine.getRules()).hasSize(1);
        }

        @Test
        @DisplayName("getRules 返回不可修改的副本")
        void shouldReturnImmutableCopy() {
            engine.register(mockRule("R1", 100));

            List<Rule> rules = engine.getRules();
            assertThat(rules).hasSize(1);
            // 尝试修改应抛出异常
            assertThat(rules).isUnmodifiable();
        }
    }

    // ==================== 优先级排序 ====================

    @Nested
    @DisplayName("优先级排序")
    class PriorityTest {

        @Test
        @DisplayName("priority 越小越先执行 - 升序排列")
        void shouldSortByPriorityAscending() {
            engine.register(mockNotTriggeredRule("R100", 100));
            engine.register(mockNotTriggeredRule("R50", 50));
            engine.register(mockNotTriggeredRule("R200", 200));
            engine.register(mockNotTriggeredRule("R10", 10));

            List<Rule> rules = engine.getRules();
            assertThat(rules).extracting(Rule::getCode)
                    .containsExactly("R10", "R50", "R100", "R200");
        }

        @Test
        @DisplayName("相同优先级 - 后注册者排前（二分查找左侧插入）")
        void shouldKeepRegistrationOrderForSamePriority() {
            engine.register(mockNotTriggeredRule("R_A", 100));
            engine.register(mockNotTriggeredRule("R_B", 100));
            engine.register(mockNotTriggeredRule("R_C", 100));

            List<Rule> rules = engine.getRules();
            // 二分查找在相同 priority 时取左侧插入位置，因此后注册的规则排在同优先级规则前面
            assertThat(rules).extracting(Rule::getCode)
                    .containsExactly("R_C", "R_B", "R_A");
        }

        @Test
        @DisplayName("二分查找 - 头部插入")
        void shouldBinarySearchInsertAtHead() {
            engine.register(mockNotTriggeredRule("R100", 100));
            engine.register(mockNotTriggeredRule("R50", 50)); // 插入头部

            List<Rule> rules = engine.getRules();
            assertThat(rules.get(0).getCode()).isEqualTo("R50");
        }

        @Test
        @DisplayName("二分查找 - 尾部插入")
        void shouldBinarySearchInsertAtTail() {
            engine.register(mockNotTriggeredRule("R100", 100));
            engine.register(mockNotTriggeredRule("R200", 200)); // 插入尾部

            List<Rule> rules = engine.getRules();
            assertThat(rules.get(1).getCode()).isEqualTo("R200");
        }

        @Test
        @DisplayName("二分查找 - 中间插入")
        void shouldBinarySearchInsertInMiddle() {
            engine.register(mockNotTriggeredRule("R10", 10));
            engine.register(mockNotTriggeredRule("R100", 100));
            engine.register(mockNotTriggeredRule("R200", 200));
            engine.register(mockNotTriggeredRule("R50", 50)); // 中间插入

            List<Rule> rules = engine.getRules();
            assertThat(rules).extracting(Rule::getCode)
                    .containsExactly("R10", "R50", "R100", "R200");
        }
    }

    // ==================== evaluate 评估流程 ====================

    @Nested
    @DisplayName("evaluate 评估流程")
    class EvaluateTest {

        @Test
        @DisplayName("空规则集 - 返回空列表")
        void shouldReturnEmptyForEmptyRules() {
            List<RuleResult> results = engine.evaluate(defaultContext());
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("规则触发 - 结果包含在返回列表中")
        void shouldReturnTriggeredResult() {
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R1");
            assertThat(results.get(0).isTriggered()).isTrue();
        }

        @Test
        @DisplayName("规则未触发 - 结果列表为空")
        void shouldNotReturnNotTriggeredResult() {
            Rule rule = mockNotTriggeredRule("R1", 100);
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("结果按严重度倒序排列 - RED > YELLOW > INFO")
        void shouldSortResultsBySeverityDescending() {
            // 注册顺序：INFO, RED, YELLOW
            engine.register(mockTriggeredRule("INFO_RULE", 10, RuleSeverity.INFO));
            engine.register(mockTriggeredRule("RED_RULE", 20, RuleSeverity.RED));
            engine.register(mockTriggeredRule("YELLOW_RULE", 30, RuleSeverity.YELLOW));

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).extracting(RuleResult::getRuleCode)
                    .containsExactly("RED_RULE", "YELLOW_RULE", "INFO_RULE");
        }

        @Test
        @DisplayName("结果中 severity 为 null 的排在最后")
        void shouldPutNullSeverityLast() {
            // 触发但无 severity
            Rule nullSeverityRule = mockRule("NULL_SEV", 10);
            when(nullSeverityRule.evaluate(any())).thenReturn(
                    RuleResult.builder().ruleCode("NULL_SEV").triggered(true).build());
            engine.register(nullSeverityRule);
            engine.register(mockTriggeredRule("RED_RULE", 20, RuleSeverity.RED));

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).extracting(RuleResult::getRuleCode)
                    .containsExactly("RED_RULE", "NULL_SEV");
        }
    }

    // ==================== 互斥组短路 ====================

    @Nested
    @DisplayName("互斥组短路")
    class MutexGroupTest {

        @Test
        @DisplayName("同组首条命中 - 后续规则跳过评估")
        void shouldSkipRemainingRulesInGroupWhenFirstTriggers() {
            Rule rule1 = mockTriggeredRule("R1", 10, RuleSeverity.RED);
            when(rule1.getMutexGroup()).thenReturn("GROUP_A");
            Rule rule2 = mockTriggeredRule("R2", 20, RuleSeverity.YELLOW);
            when(rule2.getMutexGroup()).thenReturn("GROUP_A");

            engine.register(rule1);
            engine.register(rule2);

            List<RuleResult> results = engine.evaluate(defaultContext());

            // 仅 R1 触发，R2 被跳过
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R1");
            verify(rule2, never()).evaluate(any());
        }

        @Test
        @DisplayName("同组首条未命中 - 后续规则继续评估")
        void shouldEvaluateNextRuleWhenFirstDoesNotTrigger() {
            Rule rule1 = mockNotTriggeredRule("R1", 10);
            when(rule1.getMutexGroup()).thenReturn("GROUP_A");
            Rule rule2 = mockTriggeredRule("R2", 20, RuleSeverity.YELLOW);
            when(rule2.getMutexGroup()).thenReturn("GROUP_A");

            engine.register(rule1);
            engine.register(rule2);

            List<RuleResult> results = engine.evaluate(defaultContext());

            // R2 被评估并触发
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R2");
            verify(rule1).evaluate(any());
            verify(rule2).evaluate(any());
        }

        @Test
        @DisplayName("不同互斥组 - 互不影响")
        void shouldEvaluateRulesInDifferentGroupsIndependently() {
            Rule rule1 = mockTriggeredRule("R1", 10, RuleSeverity.RED);
            when(rule1.getMutexGroup()).thenReturn("GROUP_A");
            Rule rule2 = mockTriggeredRule("R2", 20, RuleSeverity.YELLOW);
            when(rule2.getMutexGroup()).thenReturn("GROUP_B");

            engine.register(rule1);
            engine.register(rule2);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).hasSize(2);
            verify(rule1).evaluate(any());
            verify(rule2).evaluate(any());
        }

        @Test
        @DisplayName("空白互斥组名称 - 不作为互斥组处理")
        void shouldTreatBlankMutexGroupAsNoGroup() {
            Rule rule1 = mockTriggeredRule("R1", 10, RuleSeverity.RED);
            when(rule1.getMutexGroup()).thenReturn("  ");
            Rule rule2 = mockTriggeredRule("R2", 20, RuleSeverity.YELLOW);
            when(rule2.getMutexGroup()).thenReturn("  ");

            engine.register(rule1);
            engine.register(rule2);

            List<RuleResult> results = engine.evaluate(defaultContext());

            // 空白互斥组不生效，两条规则都被评估
            assertThat(results).hasSize(2);
            verify(rule1).evaluate(any());
            verify(rule2).evaluate(any());
        }
    }

    // ==================== 租户隔离 ====================

    @Nested
    @DisplayName("租户隔离")
    class TenantIsolationTest {

        @Test
        @DisplayName("租户匹配 - 规则被评估")
        void shouldEvaluateRuleWhenTenantMatches() {
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            when(rule.getTenantId()).thenReturn("1");
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(contextWithTenant("1"));

            assertThat(results).hasSize(1);
            verify(rule).evaluate(any());
        }

        @Test
        @DisplayName("租户不匹配 - 规则不被评估")
        void shouldNotEvaluateRuleWhenTenantMismatch() {
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            when(rule.getTenantId()).thenReturn("TENANT_A");
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(contextWithTenant("TENANT_B"));

            assertThat(results).isEmpty();
            verify(rule, never()).evaluate(any());
        }

        @Test
        @DisplayName("规则租户为 null - 不匹配任何上下文租户")
        void shouldNotMatchWhenRuleTenantIsNull() {
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            when(rule.getTenantId()).thenReturn(null);
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(contextWithTenant("1"));

            // rule.getTenantId()=null 不等于 context.getTenantId()="1"
            assertThat(results).isEmpty();
            verify(rule, never()).evaluate(any());
        }

        @Test
        @DisplayName("上下文租户为 null - 不匹配规则租户 1")
        void shouldNotMatchWhenContextTenantIsNull() {
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            when(rule.getTenantId()).thenReturn("1");
            engine.register(rule);

            RuleContext ctx = RuleContext.of(new HashMap<>(), "DEFAULT", "TEST", "trace-1", null);
            List<RuleResult> results = engine.evaluate(ctx);

            // context.tenantId=null != rule.tenantId="1"
            assertThat(results).isEmpty();
            verify(rule, never()).evaluate(any());
        }

        @Test
        @DisplayName("dryRun 同样遵循租户隔离")
        void shouldEnforceTenantIsolationInDryRun() {
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            when(rule.getTenantId()).thenReturn("TENANT_A");
            engine.register(rule);

            List<RuleResult> results = engine.dryRun(contextWithTenant("TENANT_B"));

            assertThat(results).isEmpty();
            verify(rule, never()).evaluate(any());
        }
    }

    // ==================== 场景过滤 ====================

    @Nested
    @DisplayName("场景过滤")
    class ScopeFilterTest {

        @Test
        @DisplayName("scenario=null - 评估全部规则")
        void shouldEvaluateAllRulesWhenScenarioIsNull() {
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            when(rule.getScope()).thenReturn("COCKPIT");
            engine.register(rule);

            RuleContext ctx = RuleContext.of(new HashMap<>(), null, "TEST", "trace-1", "1");
            List<RuleResult> results = engine.evaluate(ctx);

            assertThat(results).hasSize(1);
            verify(rule).evaluate(any());
        }

        @Test
        @DisplayName("scenario=DEFAULT - 评估全部规则")
        void shouldEvaluateAllRulesWhenScenarioIsDefault() {
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            when(rule.getScope()).thenReturn("COCKPIT");
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(contextWithScenario("DEFAULT"));

            assertThat(results).hasSize(1);
            verify(rule).evaluate(any());
        }

        @Test
        @DisplayName("场景精确匹配 - 评估")
        void shouldEvaluateWhenScopeMatchesScenario() {
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            when(rule.getScope()).thenReturn("COCKPIT");
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(contextWithScenario("COCKPIT"));

            assertThat(results).hasSize(1);
            verify(rule).evaluate(any());
        }

        @Test
        @DisplayName("场景不匹配 - 跳过评估")
        void shouldSkipWhenScopeDoesNotMatchScenario() {
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            when(rule.getScope()).thenReturn("COCKPIT");
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(contextWithScenario("BUDGET_CHECK"));

            assertThat(results).isEmpty();
            verify(rule, never()).evaluate(any());
        }

        @Test
        @DisplayName("scope=null - 适用于全部场景")
        void shouldEvaluateWhenScopeIsNull() {
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            when(rule.getScope()).thenReturn(null);
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(contextWithScenario("COCKPIT"));

            assertThat(results).hasSize(1);
            verify(rule).evaluate(any());
        }

        @Test
        @DisplayName("scope=ALL - 适用于全部场景")
        void shouldEvaluateWhenScopeIsAll() {
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            when(rule.getScope()).thenReturn("ALL");
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(contextWithScenario("COCKPIT"));

            assertThat(results).hasSize(1);
            verify(rule).evaluate(any());
        }

        @Test
        @DisplayName("scope=ALL 大小写不敏感")
        void shouldEvaluateWhenScopeIsAllCaseInsensitive() {
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            when(rule.getScope()).thenReturn("all");
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(contextWithScenario("COCKPIT"));

            assertThat(results).hasSize(1);
            verify(rule).evaluate(any());
        }

        @Test
        @DisplayName("场景匹配大小写不敏感")
        void shouldMatchScopeCaseInsensitive() {
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            when(rule.getScope()).thenReturn("cockpit");
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(contextWithScenario("COCKPIT"));

            assertThat(results).hasSize(1);
            verify(rule).evaluate(any());
        }
    }

    // ==================== 灰度路由 ====================

    @Nested
    @DisplayName("灰度路由")
    class CanaryTest {

        @Test
        @DisplayName("canaryEnabled=false - 不走灰度")
        void shouldNotRouteWhenCanaryDisabled() {
            RuleCanaryRouter canaryRouter = Mockito.mock(RuleCanaryRouter.class);
            engine.setCanaryRouter(canaryRouter);
            engine.setCanaryEnabled(false);

            RuleDefinition def = RuleDefinition.builder()
                    .code("R1")
                    .canaryRatio(0.5)
                    .canaryConditionExpression("amount > 100")
                    .build();
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            when(rule.getRuleDefinition()).thenReturn(def);
            engine.register(rule);

            engine.evaluate(defaultContext());

            // canaryEnabled=false，不应调用 canaryRouter
            verify(canaryRouter, never()).shouldRouteToCanary(any(), any());
        }

        @Test
        @DisplayName("canaryRouter=null - 不走灰度")
        void shouldNotRouteWhenCanaryRouterNull() {
            // canaryRouter 默认为 null
            RuleDefinition def = RuleDefinition.builder()
                    .code("R1")
                    .canaryRatio(0.5)
                    .canaryConditionExpression("amount > 100")
                    .build();
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            when(rule.getRuleDefinition()).thenReturn(def);
            engine.register(rule);

            // 不应抛异常
            List<RuleResult> results = engine.evaluate(defaultContext());
            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("canaryRatio <= 0 - 不走灰度")
        void shouldNotRouteWhenCanaryRatioZero() {
            RuleCanaryRouter canaryRouter = Mockito.mock(RuleCanaryRouter.class);
            engine.setCanaryRouter(canaryRouter);

            RuleDefinition def = RuleDefinition.builder()
                    .code("R1")
                    .canaryRatio(0.0)
                    .canaryConditionExpression("amount > 100")
                    .build();
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            when(rule.getRuleDefinition()).thenReturn(def);
            engine.register(rule);

            engine.evaluate(defaultContext());

            verify(canaryRouter, never()).shouldRouteToCanary(any(), any());
        }

        @Test
        @DisplayName("canaryConditionExpression 和 canarySeverityExpression 均为 null - 不走灰度")
        void shouldNotRouteWhenNoCanaryExpressions() {
            RuleCanaryRouter canaryRouter = Mockito.mock(RuleCanaryRouter.class);
            engine.setCanaryRouter(canaryRouter);

            RuleDefinition def = RuleDefinition.builder()
                    .code("R1")
                    .canaryRatio(0.5)
                    .canaryConditionExpression(null)
                    .canarySeverityExpression(null)
                    .build();
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            when(rule.getRuleDefinition()).thenReturn(def);
            engine.register(rule);

            engine.evaluate(defaultContext());

            verify(canaryRouter, never()).shouldRouteToCanary(any(), any());
        }

        @Test
        @DisplayName("命中灰度桶 - 评估候选版本，跳过主版本")
        void shouldEvaluateCanaryWhenRoutedToCanary() {
            RuleCanaryRouter canaryRouter = Mockito.mock(RuleCanaryRouter.class);
            engine.setCanaryRouter(canaryRouter);

            RuleDefinition def = RuleDefinition.builder()
                    .code("R1")
                    .canaryRatio(0.5)
                    .canaryConditionExpression("amount > 100")
                    .build();
            Rule rule = mockRule("R1", 100);
            when(rule.getRuleDefinition()).thenReturn(def);

            // 灰度候选规则
            Rule canaryRule = Mockito.mock(Rule.class);
            when(canaryRule.getCode()).thenReturn("R1_CANARY");
            when(canaryRule.getName()).thenReturn("R1 Canary");
            when(canaryRule.evaluate(any())).thenReturn(
                    RuleResult.builder().ruleCode("R1").triggered(true).severity(RuleSeverity.YELLOW).build());

            when(canaryRouter.shouldRouteToCanary(any(), any())).thenReturn(true);
            when(canaryRouter.buildCanaryRule(any())).thenReturn(canaryRule);

            engine.register(rule);
            List<RuleResult> results = engine.evaluate(defaultContext());

            // 主版本不评估，候选版本评估
            verify(rule, never()).evaluate(any());
            verify(canaryRule).evaluate(any());
            verify(canaryRouter).markCanary(any());
            verify(canaryRouter).recordBucket(eq("R1"), eq(true));
            assertThat(results).hasSize(1);
            assertThat(results.get(0).isTriggered()).isTrue();
        }

        @Test
        @DisplayName("命中灰度桶 + 超时执行器 - 使用 timeoutExecutor 评估候选版本")
        void shouldUseTimeoutExecutorForCanary() {
            RuleCanaryRouter canaryRouter = Mockito.mock(RuleCanaryRouter.class);
            engine.setCanaryRouter(canaryRouter);
            RuleTimeoutExecutor timeoutExecutor = Mockito.mock(RuleTimeoutExecutor.class);
            engine.setTimeoutExecutor(timeoutExecutor);

            RuleDefinition def = RuleDefinition.builder()
                    .code("R1")
                    .canaryRatio(0.5)
                    .canaryConditionExpression("amount > 100")
                    .build();
            Rule rule = mockRule("R1", 100);
            when(rule.getRuleDefinition()).thenReturn(def);

            Rule canaryRule = Mockito.mock(Rule.class);
            when(canaryRule.getCode()).thenReturn("R1_CANARY");
            when(timeoutExecutor.evaluateWithTimeout(any(), any(), anyLong()))
                    .thenReturn(RuleResult.builder().ruleCode("R1").triggered(true).build());

            when(canaryRouter.shouldRouteToCanary(any(), any())).thenReturn(true);
            when(canaryRouter.buildCanaryRule(any())).thenReturn(canaryRule);

            engine.register(rule);
            engine.evaluate(defaultContext());

            verify(timeoutExecutor).evaluateWithTimeout(eq(canaryRule), any(), eq(0L));
            verify(canaryRouter).markCanary(any());
        }

        @Test
        @DisplayName("命中灰度桶 + 候选版本评估异常 - 记录但不中断")
        void shouldHandleCanaryEvaluationException() {
            RuleCanaryRouter canaryRouter = Mockito.mock(RuleCanaryRouter.class);
            engine.setCanaryRouter(canaryRouter);

            RuleDefinition def = RuleDefinition.builder()
                    .code("R1")
                    .canaryRatio(0.5)
                    .canaryConditionExpression("amount > 100")
                    .build();
            Rule rule = mockRule("R1", 100);
            when(rule.getRuleDefinition()).thenReturn(def);

            Rule canaryRule = Mockito.mock(Rule.class);
            when(canaryRule.getCode()).thenReturn("R1_CANARY");
            when(canaryRule.evaluate(any())).thenThrow(new RuntimeException("候选版本评估失败"));

            when(canaryRouter.shouldRouteToCanary(any(), any())).thenReturn(true);
            when(canaryRouter.buildCanaryRule(any())).thenReturn(canaryRule);

            engine.register(rule);
            List<RuleResult> results = engine.evaluate(defaultContext());

            // 异常被捕获，不中断
            assertThat(results).isEmpty();
            // markCanary 不应被调用（result 为 null）
            verify(canaryRouter, never()).markCanary(any());
            // 统计应记录异常
            RuleEngineStats stats = engine.getStats();
            assertThat(stats.getTotalErrors()).isEqualTo(1);
        }

        @Test
        @DisplayName("命中灰度桶 + 候选版本返回 null - markCanary 不调用")
        void shouldNotCallMarkCanaryWhenResultIsNull() {
            RuleCanaryRouter canaryRouter = Mockito.mock(RuleCanaryRouter.class);
            engine.setCanaryRouter(canaryRouter);

            RuleDefinition def = RuleDefinition.builder()
                    .code("R1")
                    .canaryRatio(0.5)
                    .canaryConditionExpression("amount > 100")
                    .build();
            Rule rule = mockRule("R1", 100);
            when(rule.getRuleDefinition()).thenReturn(def);

            Rule canaryRule = Mockito.mock(Rule.class);
            when(canaryRule.getCode()).thenReturn("R1_CANARY");
            when(canaryRule.evaluate(any())).thenReturn(null);

            when(canaryRouter.shouldRouteToCanary(any(), any())).thenReturn(true);
            when(canaryRouter.buildCanaryRule(any())).thenReturn(canaryRule);

            engine.register(rule);
            engine.evaluate(defaultContext());

            // result 为 null，markCanary 不调用
            verify(canaryRouter, never()).markCanary(any());
        }

        @Test
        @DisplayName("未命中灰度桶 - 评估主版本")
        void shouldEvaluateMainVersionWhenNotRoutedToCanary() {
            RuleCanaryRouter canaryRouter = Mockito.mock(RuleCanaryRouter.class);
            engine.setCanaryRouter(canaryRouter);

            RuleDefinition def = RuleDefinition.builder()
                    .code("R1")
                    .canaryRatio(0.5)
                    .canaryConditionExpression("amount > 100")
                    .build();
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            when(rule.getRuleDefinition()).thenReturn(def);

            when(canaryRouter.shouldRouteToCanary(any(), any())).thenReturn(false);

            engine.register(rule);
            List<RuleResult> results = engine.evaluate(defaultContext());

            // 主版本被评估
            verify(rule).evaluate(any());
            verify(canaryRouter).recordBucket(eq("R1"), eq(false));
            verify(canaryRouter, never()).buildCanaryRule(any());
            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("仅 canarySeverityExpression 非空 - 也走灰度")
        void shouldRouteWhenOnlyCanarySeverityExpressionPresent() {
            RuleCanaryRouter canaryRouter = Mockito.mock(RuleCanaryRouter.class);
            engine.setCanaryRouter(canaryRouter);

            RuleDefinition def = RuleDefinition.builder()
                    .code("R1")
                    .canaryRatio(0.5)
                    .canarySeverityExpression("amount > 100 ? 'RED' : 'YELLOW'")
                    .build();
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            when(rule.getRuleDefinition()).thenReturn(def);

            when(canaryRouter.shouldRouteToCanary(any(), any())).thenReturn(false);

            engine.register(rule);
            engine.evaluate(defaultContext());

            verify(canaryRouter).shouldRouteToCanary(any(), any());
        }
    }

    // ==================== 索引优化 ====================

    @Nested
    @DisplayName("索引优化")
    class IndexTest {

        @Test
        @DisplayName("规则数 < 200 - 索引不启用，线性扫描")
        void shouldNotEnableIndexBelowThreshold() {
            engine.register(mockNotTriggeredRule("R1", 100));
            engine.register(mockNotTriggeredRule("R2", 200));

            // 索引未启用，evaluate 走线性扫描路径
            List<RuleResult> results = engine.evaluate(defaultContext());
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("规则数 >= 200 - 索引启用，走索引查找")
        void shouldEnableIndexAtOrAboveThreshold() {
            // 注册 200 条规则
            for (int i = 0; i < 200; i++) {
                engine.register(mockNotTriggeredRule("R" + i, i));
            }

            // 索引应已启用
            List<RuleResult> results = engine.evaluate(defaultContext());
            assertThat(results).isEmpty(); // 全部未触发

            // 验证统计：评估了 200 条规则
            RuleEngineStats stats = engine.getStats();
            assertThat(stats.getTotalEvaluations()).isEqualTo(200);
        }

        @Test
        @DisplayName("索引启用后 - 租户过滤由索引完成")
        void shouldFilterByTenantViaIndex() {
            // 注册 100 条 tenant_1 规则 + 100 条 tenant_2 规则
            for (int i = 0; i < 100; i++) {
                Rule rule = mockNotTriggeredRule("T1_R" + i, i);
                when(rule.getTenantId()).thenReturn("1");
                engine.register(rule);
            }
            for (int i = 0; i < 100; i++) {
                Rule rule = mockNotTriggeredRule("T2_R" + i, 200 + i);
                when(rule.getTenantId()).thenReturn("2");
                engine.register(rule);
            }

            // 用 tenant_1 上下文评估
            List<RuleResult> results = engine.evaluate(contextWithTenant("1"));
            assertThat(results).isEmpty();

            // 仅评估 tenant_1 的 100 条规则
            RuleEngineStats stats = engine.getStats();
            assertThat(stats.getTotalEvaluations()).isEqualTo(100);
        }
    }

    // ==================== 熔断器 ====================

    @Nested
    @DisplayName("熔断器")
    class CircuitBreakerTest {

        @Test
        @DisplayName("熔断器阻止评估 - 规则被跳过")
        void shouldSkipRuleWhenCircuitBreakerBlocks() {
            RuleCircuitBreaker breaker = Mockito.mock(RuleCircuitBreaker.class);
            when(breaker.allowEvaluate("R1")).thenReturn(false);
            engine.setCircuitBreaker(breaker);

            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(defaultContext());

            // 规则被熔断，不评估
            assertThat(results).isEmpty();
            verify(rule, never()).evaluate(any());
            // 熔断器阻止评估后不会调用 recordResult（recordResult 仅在实际评估后调用）
            verify(breaker, never()).recordResult(anyString(), anyBoolean());
        }

        @Test
        @DisplayName("熔断器允许评估 - 规则被评估")
        void shouldEvaluateWhenCircuitBreakerAllows() {
            RuleCircuitBreaker breaker = Mockito.mock(RuleCircuitBreaker.class);
            when(breaker.allowEvaluate("R1")).thenReturn(true);
            engine.setCircuitBreaker(breaker);

            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).hasSize(1);
            verify(rule).evaluate(any());
            verify(breaker).recordResult(eq("R1"), eq(true));
        }

        @Test
        @DisplayName("无熔断器 - 正常评估")
        void shouldEvaluateWithoutCircuitBreaker() {
            // circuitBreaker 默认为 null
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).hasSize(1);
        }
    }

    // ==================== 超时执行器 ====================

    @Nested
    @DisplayName("超时执行器")
    class TimeoutExecutorTest {

        @Test
        @DisplayName("设置超时执行器 - 使用它评估规则")
        void shouldUseTimeoutExecutorWhenSet() {
            RuleTimeoutExecutor executor = Mockito.mock(RuleTimeoutExecutor.class);
            when(executor.evaluateWithTimeout(any(), any(), anyLong()))
                    .thenReturn(RuleResult.builder().ruleCode("R1").triggered(true).severity(RuleSeverity.RED).build());
            engine.setTimeoutExecutor(executor);

            Rule rule = mockRule("R1", 100);
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).hasSize(1);
            verify(executor).evaluateWithTimeout(eq(rule), any(), eq(0L));
            // 规则自身的 evaluate 不应被调用
            verify(rule, never()).evaluate(any());
        }

        @Test
        @DisplayName("无超时执行器 - 直接调用 rule.evaluate")
        void shouldCallRuleEvaluateDirectlyWithoutTimeoutExecutor() {
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).hasSize(1);
            verify(rule).evaluate(any());
        }

        @Test
        @DisplayName("超时执行器抛异常 - 记录但不中断")
        void shouldHandleTimeoutExecutorException() {
            RuleTimeoutExecutor executor = Mockito.mock(RuleTimeoutExecutor.class);
            when(executor.evaluateWithTimeout(any(), any(), anyLong()))
                    .thenThrow(new RuntimeException("执行器异常"));
            engine.setTimeoutExecutor(executor);

            Rule rule = mockRule("R1", 100);
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).isEmpty();
            RuleEngineStats stats = engine.getStats();
            assertThat(stats.getTotalErrors()).isEqualTo(1);
        }

        @Test
        @DisplayName("结果描述以\"评估超时\"开头 - 视为异常")
        void shouldTreatTimeoutResultAsError() {
            Rule rule = mockRule("R1", 100);
            when(rule.evaluate(any())).thenReturn(
                    RuleResult.builder().ruleCode("R1").triggered(false).description("评估超时（100ms）").build());
            engine.register(rule);

            engine.evaluate(defaultContext());

            RuleEngineStats stats = engine.getStats();
            assertThat(stats.getTotalErrors()).isEqualTo(1);
        }
    }

    // ==================== 轨迹记录 ====================

    @Nested
    @DisplayName("轨迹记录")
    class TraceTest {

        @Test
        @DisplayName("无 TraceRecorder - 不记录轨迹")
        void shouldNotRecordTraceWhenRecorderNull() {
            // traceRecorder 默认为 null
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            engine.register(rule);

            engine.evaluate(defaultContext());
            // 无异常即通过
        }

        @Test
        @DisplayName("TraceRecorder 已禁用 - 不记录轨迹")
        void shouldNotRecordTraceWhenRecorderDisabled() {
            TraceRecorder recorder = Mockito.mock(TraceRecorder.class);
            when(recorder.isEnabled()).thenReturn(false);
            engine.setTraceRecorder(recorder);

            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            engine.register(rule);

            engine.evaluate(defaultContext());

            verify(recorder, never()).record(any());
        }

        @Test
        @DisplayName("TraceRecorder 已启用 - 记录轨迹")
        void shouldRecordTraceWhenEnabled() {
            TraceRecorder recorder = Mockito.mock(TraceRecorder.class);
            when(recorder.isEnabled()).thenReturn(true);
            engine.setTraceRecorder(recorder);

            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            engine.register(rule);

            engine.evaluate(defaultContext());

            verify(recorder).record(any(RuleExecutionTrace.class));
        }

        @Test
        @DisplayName("TraceRecorder 记录异常 - 不中断评估")
        void shouldNotBreakWhenTraceRecorderThrows() {
            TraceRecorder recorder = Mockito.mock(TraceRecorder.class);
            when(recorder.isEnabled()).thenReturn(true);
            Mockito.doThrow(new RuntimeException("记录失败")).when(recorder).record(any());
            engine.setTraceRecorder(recorder);

            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(defaultContext());

            // 轨迹记录异常不影响评估结果
            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("轨迹包含完整信息 - severity 和 threshold")
        void shouldBuildTraceWithFullInfo() {
            TraceRecorder recorder = Mockito.mock(TraceRecorder.class);
            when(recorder.isEnabled()).thenReturn(true);
            engine.setTraceRecorder(recorder);

            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            engine.register(rule);

            engine.evaluate(defaultContext());

            ArgumentCaptor<RuleExecutionTrace> captor = ArgumentCaptor.forClass(RuleExecutionTrace.class);
            verify(recorder).record(captor.capture());
            RuleExecutionTrace trace = captor.getValue();
            assertThat(trace.getRuleCode()).isEqualTo("R1");
            assertThat(trace.isTriggered()).isTrue();
            assertThat(trace.getSeverity()).isEqualTo("RED");
            assertThat(trace.getConditionResult()).isEqualTo("amount > 100");
            assertThat(trace.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("规则评估异常时也记录轨迹")
        void shouldRecordTraceWhenRuleThrows() {
            TraceRecorder recorder = Mockito.mock(TraceRecorder.class);
            when(recorder.isEnabled()).thenReturn(true);
            engine.setTraceRecorder(recorder);

            Rule rule = mockRule("R1", 100);
            when(rule.evaluate(any())).thenThrow(new RuntimeException("评估失败"));
            engine.register(rule);

            engine.evaluate(defaultContext());

            ArgumentCaptor<RuleExecutionTrace> captor = ArgumentCaptor.forClass(RuleExecutionTrace.class);
            verify(recorder).record(captor.capture());
            RuleExecutionTrace trace = captor.getValue();
            assertThat(trace.isTriggered()).isFalse();
            assertThat(trace.getErrorMessage()).isEqualTo("评估失败");
        }

        @Test
        @DisplayName("结果为 null 时构建轨迹 - severity 和 conditionResult 为 null")
        void shouldBuildTraceWithNullResult() {
            TraceRecorder recorder = Mockito.mock(TraceRecorder.class);
            when(recorder.isEnabled()).thenReturn(true);
            engine.setTraceRecorder(recorder);

            Rule rule = mockRule("R1", 100);
            when(rule.evaluate(any())).thenReturn(null);
            engine.register(rule);

            engine.evaluate(defaultContext());

            ArgumentCaptor<RuleExecutionTrace> captor = ArgumentCaptor.forClass(RuleExecutionTrace.class);
            verify(recorder).record(captor.capture());
            RuleExecutionTrace trace = captor.getValue();
            assertThat(trace.getSeverity()).isNull();
            assertThat(trace.getConditionResult()).isNull();
        }
    }

    // ==================== 断点调试 ====================

    @Nested
    @DisplayName("断点调试")
    class BreakpointTest {

        @Test
        @DisplayName("无断点 Hook - 不触发断点逻辑")
        void shouldNotTriggerBreakpointWhenHookNull() {
            // breakpointHook 默认为 null
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("Hook 存在但规则无断点 - 不触发回调")
        void shouldNotCallHookWhenNoBreakpoint() {
            BreakpointHook hook = Mockito.mock(BreakpointHook.class);
            when(hook.hasBreakpoint("R1")).thenReturn(false);
            engine.setBreakpointHook(hook);

            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            engine.register(rule);

            engine.evaluate(defaultContext());

            verify(hook, never()).onBeforeEvaluate(any());
            verify(hook, never()).onAfterEvaluate(any());
        }

        @Test
        @DisplayName("Hook 返回 CONTINUE - 正常评估 + 调用 onAfterEvaluate")
        void shouldEvaluateAndCallAfterHookWhenContinue() {
            BreakpointHook hook = Mockito.mock(BreakpointHook.class);
            when(hook.hasBreakpoint("R1")).thenReturn(true);
            when(hook.onBeforeEvaluate(any())).thenReturn(BreakpointHook.BreakpointAction.CONTINUE);
            engine.setBreakpointHook(hook);

            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).hasSize(1);
            verify(hook).onBeforeEvaluate(any());
            verify(hook).onAfterEvaluate(any());
        }

        @Test
        @DisplayName("Hook 返回 STEP_OVER - 跳过当前规则评估")
        void shouldSkipRuleWhenStepOver() {
            BreakpointHook hook = Mockito.mock(BreakpointHook.class);
            when(hook.hasBreakpoint("R1")).thenReturn(true);
            when(hook.onBeforeEvaluate(any())).thenReturn(BreakpointHook.BreakpointAction.STEP_OVER);
            engine.setBreakpointHook(hook);

            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(defaultContext());

            // 规则被跳过
            assertThat(results).isEmpty();
            verify(rule, never()).evaluate(any());
            // STEP_OVER 不调用 onAfterEvaluate
            verify(hook, never()).onAfterEvaluate(any());
        }

        @Test
        @DisplayName("Hook 返回 SUSPEND - 仍评估规则（阻塞由 Hook 内部实现）")
        void shouldEvaluateWhenSuspend() {
            BreakpointHook hook = Mockito.mock(BreakpointHook.class);
            when(hook.hasBreakpoint("R1")).thenReturn(true);
            when(hook.onBeforeEvaluate(any())).thenReturn(BreakpointHook.BreakpointAction.SUSPEND);
            engine.setBreakpointHook(hook);

            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).hasSize(1);
            verify(hook).onAfterEvaluate(any());
        }

        @Test
        @DisplayName("onBeforeEvaluate 抛异常 - 不中断评估")
        void shouldNotBreakWhenBeforeHookThrows() {
            BreakpointHook hook = Mockito.mock(BreakpointHook.class);
            when(hook.hasBreakpoint("R1")).thenReturn(true);
            Mockito.doThrow(new RuntimeException("Hook 异常")).when(hook).onBeforeEvaluate(any());
            engine.setBreakpointHook(hook);

            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(defaultContext());

            // 异常被吞掉，评估继续
            assertThat(results).hasSize(1);
            // onAfterEvaluate 仍被调用（hasBreakpoint=true）
            verify(hook).onAfterEvaluate(any());
        }

        @Test
        @DisplayName("onAfterEvaluate 抛异常 - 不中断评估")
        void shouldNotBreakWhenAfterHookThrows() {
            BreakpointHook hook = Mockito.mock(BreakpointHook.class);
            when(hook.hasBreakpoint("R1")).thenReturn(true);
            when(hook.onBeforeEvaluate(any())).thenReturn(BreakpointHook.BreakpointAction.CONTINUE);
            Mockito.doThrow(new RuntimeException("After Hook 异常")).when(hook).onAfterEvaluate(any());
            engine.setBreakpointHook(hook);

            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(defaultContext());

            // 异常被吞掉，评估结果仍返回
            assertThat(results).hasSize(1);
        }
    }

    // ==================== 监控指标 ====================

    @Nested
    @DisplayName("监控指标")
    class MetricsTest {

        @Test
        @DisplayName("无监控指标 - 不调用记录方法")
        void shouldNotRecordMetricsWhenNull() {
            // metrics 默认为 null
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            engine.register(rule);

            engine.evaluate(defaultContext());
            // 无异常即通过
        }

        @Test
        @DisplayName("设置监控指标 - recordEvaluation 被调用")
        void shouldCallRecordEvaluationWhenMetricsSet() {
            RuleMetrics metrics = Mockito.mock(RuleMetrics.class);
            engine.setMetrics(metrics);

            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            engine.register(rule);

            engine.evaluate(defaultContext());

            verify(metrics).recordEvaluation(eq("R1"), eq("DEFAULT"), eq(true),
                    eq(RuleSeverity.RED), eq(false), anyLong());
        }

        @Test
        @DisplayName("设置监控指标 - recordEvaluatedRules 被调用")
        void shouldCallRecordEvaluatedRulesWhenMetricsSet() {
            RuleMetrics metrics = Mockito.mock(RuleMetrics.class);
            engine.setMetrics(metrics);

            engine.register(mockTriggeredRule("R1", 100, RuleSeverity.RED));
            engine.register(mockNotTriggeredRule("R2", 200));

            engine.evaluate(defaultContext());

            // 评估了 2 条规则
            verify(metrics).recordEvaluatedRules(eq(2));
        }

        @Test
        @DisplayName("设置监控指标 - register 时调用 recordRegisteredRules")
        void shouldCallRecordRegisteredRulesOnRegister() {
            RuleMetrics metrics = Mockito.mock(RuleMetrics.class);
            engine.setMetrics(metrics);

            engine.register(mockNotTriggeredRule("R1", 100));

            verify(metrics).recordRegisteredRules(eq(1));
        }

        @Test
        @DisplayName("设置监控指标 - unregister 时调用 recordRegisteredRules")
        void shouldCallRecordRegisteredRulesOnUnregister() {
            RuleMetrics metrics = Mockito.mock(RuleMetrics.class);
            engine.setMetrics(metrics);

            engine.register(mockNotTriggeredRule("R1", 100));
            engine.unregister("R1");

            // register 内部先调 unregister（热更新覆盖）→ recordRegisteredRules(0)，
            // 然后添加规则 → recordRegisteredRules(1)，
            // 最后手动 unregister → recordRegisteredRules(0)，共 3 次
            verify(metrics, times(3)).recordRegisteredRules(anyInt());
            verify(metrics, times(2)).recordRegisteredRules(eq(0));
            verify(metrics, times(1)).recordRegisteredRules(eq(1));
        }

        @Test
        @DisplayName("recordEvaluation 抛异常 - 不中断评估")
        void shouldNotBreakWhenMetricsThrows() {
            RuleMetrics metrics = Mockito.mock(RuleMetrics.class);
            Mockito.doThrow(new RuntimeException("指标记录失败"))
                    .when(metrics).recordEvaluation(anyString(), anyString(), anyBoolean(),
                            any(), anyBoolean(), anyLong());
            engine.setMetrics(metrics);

            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            engine.register(rule);

            List<RuleResult> results = engine.evaluate(defaultContext());

            // 异常被吞掉，评估结果仍返回
            assertThat(results).hasSize(1);
        }

        @Test
        @DisplayName("getStats - metrics 为 null 时 lastEvaluatedRules=0")
        void shouldReturnZeroLastEvaluatedRulesWhenMetricsNull() {
            engine.register(mockTriggeredRule("R1", 100, RuleSeverity.RED));
            engine.evaluate(defaultContext());

            RuleEngineStats stats = engine.getStats();
            assertThat(stats.getLastEvaluatedRules()).isEqualTo(0);
        }

        @Test
        @DisplayName("getStats - metrics 存在时返回 metrics.getLastEvaluatedRules")
        void shouldReturnMetricsLastEvaluatedRules() {
            RuleMetrics metrics = Mockito.mock(RuleMetrics.class);
            when(metrics.getLastEvaluatedRules()).thenReturn(42);
            engine.setMetrics(metrics);

            engine.register(mockTriggeredRule("R1", 100, RuleSeverity.RED));
            engine.evaluate(defaultContext());

            RuleEngineStats stats = engine.getStats();
            assertThat(stats.getLastEvaluatedRules()).isEqualTo(42);
        }
    }

    // ==================== 异常处理 ====================

    @Nested
    @DisplayName("异常处理")
    class ExceptionHandlingTest {

        @Test
        @DisplayName("规则评估抛异常 - 记录但不中断")
        void shouldRecordButNotBreakWhenRuleThrows() {
            Rule rule1 = mockRule("R1", 100);
            when(rule1.evaluate(any())).thenThrow(new RuntimeException("R1 异常"));
            Rule rule2 = mockTriggeredRule("R2", 200, RuleSeverity.RED);
            engine.register(rule1);
            engine.register(rule2);

            List<RuleResult> results = engine.evaluate(defaultContext());

            // R1 异常不中断，R2 正常评估
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R2");
            RuleEngineStats stats = engine.getStats();
            assertThat(stats.getTotalErrors()).isEqualTo(1);
            assertThat(stats.getTotalEvaluations()).isEqualTo(2);
        }

        @Test
        @DisplayName("空上下文事实 - 正常评估")
        void shouldEvaluateWithEmptyFacts() {
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            engine.register(rule);

            RuleContext ctx = RuleContext.of(new HashMap<>());
            List<RuleResult> results = engine.evaluate(ctx);

            assertThat(results).hasSize(1);
        }
    }

    // ==================== topResult / dryRun ====================

    @Nested
    @DisplayName("topResult / dryRun")
    class TopResultAndDryRunTest {

        @Test
        @DisplayName("topResult - 无触发返回 null")
        void shouldReturnNullWhenNoTrigger() {
            engine.register(mockNotTriggeredRule("R1", 100));

            RuleResult result = engine.topResult(defaultContext());

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("topResult - 返回最高严重度结果")
        void shouldReturnHighestSeverityResult() {
            engine.register(mockTriggeredRule("R1", 10, RuleSeverity.INFO));
            engine.register(mockTriggeredRule("R2", 20, RuleSeverity.RED));

            RuleResult result = engine.topResult(defaultContext());

            assertThat(result).isNotNull();
            assertThat(result.getRuleCode()).isEqualTo("R2");
            assertThat(result.getSeverity()).isEqualTo(RuleSeverity.RED);
        }

        @Test
        @DisplayName("dryRun - 返回全部结果含未触发")
        void shouldReturnAllResultsInDryRun() {
            Rule triggered = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            Rule notTriggered = mockNotTriggeredRule("R2", 200);
            engine.register(triggered);
            engine.register(notTriggered);

            List<RuleResult> results = engine.dryRun(defaultContext());

            assertThat(results).hasSize(2);
            assertThat(results).extracting(RuleResult::getRuleCode)
                    .containsExactlyInAnyOrder("R1", "R2");
        }

        @Test
        @DisplayName("dryRun - 规则返回 null 时转为 notTriggered")
        void shouldConvertNullResultToNotTriggeredInDryRun() {
            Rule rule = mockRule("R1", 100);
            when(rule.evaluate(any())).thenReturn(null);
            engine.register(rule);

            List<RuleResult> results = engine.dryRun(defaultContext());

            assertThat(results).hasSize(1);
            assertThat(results.get(0).isTriggered()).isFalse();
            assertThat(results.get(0).getRuleCode()).isEqualTo("R1");
        }

        @Test
        @DisplayName("dryRun - 规则抛异常时转为异常结果")
        void shouldConvertExceptionToErrorResultInDryRun() {
            Rule rule = mockRule("R1", 100);
            when(rule.evaluate(any())).thenThrow(new RuntimeException("评估异常"));
            engine.register(rule);

            List<RuleResult> results = engine.dryRun(defaultContext());

            assertThat(results).hasSize(1);
            assertThat(results.get(0).isTriggered()).isFalse();
            assertThat(results.get(0).getDescription()).contains("评估异常");
        }

        @Test
        @DisplayName("dryRun - 租户不匹配的规则被跳过")
        void shouldSkipMismatchedTenantInDryRun() {
            Rule rule = mockTriggeredRule("R1", 100, RuleSeverity.RED);
            when(rule.getTenantId()).thenReturn("TENANT_A");
            engine.register(rule);

            List<RuleResult> results = engine.dryRun(contextWithTenant("TENANT_B"));

            assertThat(results).isEmpty();
            verify(rule, never()).evaluate(any());
        }
    }

    // ==================== 统计与配置 ====================

    @Nested
    @DisplayName("统计与配置")
    class StatsAndConfigTest {

        @Test
        @DisplayName("record - 统计启用时记录")
        void shouldRecordWhenStatsEnabled() {
            engine.setStatsEnabled(true);
            engine.record("R1", true, false, 10);
            engine.record("R1", false, true, 20); // 已有条目
            engine.record("R2", false, false, 30);

            RuleEngineStats stats = engine.getStats();
            assertThat(stats.getTotalEvaluations()).isEqualTo(3);
            assertThat(stats.getTotalTriggered()).isEqualTo(1);
            assertThat(stats.getTotalErrors()).isEqualTo(1);
            assertThat(stats.getTotalElapsedMs()).isEqualTo(60);
            assertThat(stats.getPerRuleStats()).containsKeys("R1", "R2");
            assertThat(stats.getPerRuleStats().get("R1").getExecutions()).isEqualTo(2);
            assertThat(stats.getPerRuleStats().get("R1").getTriggered()).isEqualTo(1);
            assertThat(stats.getPerRuleStats().get("R1").getErrors()).isEqualTo(1);
            assertThat(stats.getPerRuleStats().get("R1").getTotalElapsedMs()).isEqualTo(30);
        }

        @Test
        @DisplayName("record - 统计禁用时不记录")
        void shouldNotRecordWhenStatsDisabled() {
            engine.setStatsEnabled(false);
            engine.record("R1", true, false, 10);

            RuleEngineStats stats = engine.getStats();
            assertThat(stats.getTotalEvaluations()).isEqualTo(0);
        }

        @Test
        @DisplayName("isStatsEnabled - 默认启用")
        void shouldDefaultStatsEnabled() {
            assertThat(engine.isStatsEnabled()).isTrue();
        }

        @Test
        @DisplayName("setStatsEnabled - 切换状态")
        void shouldToggleStatsEnabled() {
            engine.setStatsEnabled(false);
            assertThat(engine.isStatsEnabled()).isFalse();
            engine.setStatsEnabled(true);
            assertThat(engine.isStatsEnabled()).isTrue();
        }

        @Test
        @DisplayName("resetStats - 重置所有计数器")
        void shouldResetAllCounters() {
            engine.record("R1", true, false, 10);
            engine.record("R2", false, true, 20);

            engine.resetStats();

            RuleEngineStats stats = engine.getStats();
            assertThat(stats.getTotalEvaluations()).isEqualTo(0);
            assertThat(stats.getTotalTriggered()).isEqualTo(0);
            assertThat(stats.getTotalErrors()).isEqualTo(0);
            assertThat(stats.getTotalElapsedMs()).isEqualTo(0);
            assertThat(stats.getPerRuleStats()).isEmpty();
        }

        @Test
        @DisplayName("asStatsRecorder - 返回自身")
        void shouldReturnSelfAsStatsRecorder() {
            StatsRecorder recorder = engine.asStatsRecorder();
            assertThat(recorder).isSameAs(engine);
        }

        @Test
        @DisplayName("getStats - 包含注册规则数")
        void shouldIncludeRegisteredRulesInStats() {
            engine.register(mockNotTriggeredRule("R1", 100));
            engine.register(mockNotTriggeredRule("R2", 200));

            RuleEngineStats stats = engine.getStats();
            assertThat(stats.getRegisteredRules()).isEqualTo(2);
        }

        @Test
        @DisplayName("evaluate 后 perRuleStats 包含规则明细")
        void shouldPopulatePerRuleStatsAfterEvaluate() {
            engine.register(mockTriggeredRule("R1", 100, RuleSeverity.RED));
            engine.evaluate(defaultContext());

            RuleEngineStats stats = engine.getStats();
            assertThat(stats.getPerRuleStats()).containsKey("R1");
            assertThat(stats.getPerRuleStats().get("R1").getExecutions()).isEqualTo(1);
            assertThat(stats.getPerRuleStats().get("R1").getTriggered()).isEqualTo(1);
        }
    }

    // ==================== Setter/Getter 测试 ====================

    @Nested
    @DisplayName("Setter/Getter 配置")
    class SetterGetterTest {

        @Test
        @DisplayName("TraceRecorder 设置与获取")
        void shouldSetAndGetTraceRecorder() {
            TraceRecorder recorder = Mockito.mock(TraceRecorder.class);
            engine.setTraceRecorder(recorder);
            assertThat(engine.getTraceRecorder()).isSameAs(recorder);

            engine.setTraceRecorder(null);
            assertThat(engine.getTraceRecorder()).isNull();
        }

        @Test
        @DisplayName("TimeoutExecutor 设置与获取")
        void shouldSetAndGetTimeoutExecutor() {
            RuleTimeoutExecutor executor = Mockito.mock(RuleTimeoutExecutor.class);
            engine.setTimeoutExecutor(executor);
            assertThat(engine.getTimeoutExecutor()).isSameAs(executor);

            engine.setTimeoutExecutor(null);
            assertThat(engine.getTimeoutExecutor()).isNull();
        }

        @Test
        @DisplayName("CircuitBreaker 设置与获取")
        void shouldSetAndGetCircuitBreaker() {
            RuleCircuitBreaker breaker = Mockito.mock(RuleCircuitBreaker.class);
            engine.setCircuitBreaker(breaker);
            assertThat(engine.getCircuitBreaker()).isSameAs(breaker);

            engine.setCircuitBreaker(null);
            assertThat(engine.getCircuitBreaker()).isNull();
        }

        @Test
        @DisplayName("Metrics 设置与获取")
        void shouldSetAndGetMetrics() {
            RuleMetrics metrics = Mockito.mock(RuleMetrics.class);
            engine.setMetrics(metrics);
            assertThat(engine.getMetrics()).isSameAs(metrics);

            engine.setMetrics(null);
            assertThat(engine.getMetrics()).isNull();
        }

        @Test
        @DisplayName("CanaryRouter 设置与获取")
        void shouldSetAndGetCanaryRouter() {
            RuleCanaryRouter router = Mockito.mock(RuleCanaryRouter.class);
            engine.setCanaryRouter(router);
            assertThat(engine.getCanaryRouter()).isSameAs(router);

            engine.setCanaryRouter(null);
            assertThat(engine.getCanaryRouter()).isNull();
        }

        @Test
        @DisplayName("CanaryEnabled 设置与获取")
        void shouldSetAndGetCanaryEnabled() {
            assertThat(engine.isCanaryEnabled()).isTrue(); // 默认启用

            engine.setCanaryEnabled(false);
            assertThat(engine.isCanaryEnabled()).isFalse();

            engine.setCanaryEnabled(true);
            assertThat(engine.isCanaryEnabled()).isTrue();
        }

        @Test
        @DisplayName("BreakpointHook 设置与获取")
        void shouldSetAndGetBreakpointHook() {
            BreakpointHook hook = Mockito.mock(BreakpointHook.class);
            engine.setBreakpointHook(hook);
            assertThat(engine.getBreakpointHook()).isSameAs(hook);

            engine.setBreakpointHook(null);
            assertThat(engine.getBreakpointHook()).isNull();
        }
    }

    // ==================== 资源释放 ====================

    @Nested
    @DisplayName("资源释放 destroy")
    class DestroyTest {

        @Test
        @DisplayName("destroy - 无 TraceRecorder 和 TimeoutExecutor 时空操作")
        void shouldNoOpWhenNoResourcesToRelease() {
            // 不设置任何资源
            engine.destroy();
            // 无异常即通过
        }

        @Test
        @DisplayName("destroy - 普通 TraceRecorder（非 Async）不调用 shutdown")
        void shouldNotShutdownNonAsyncTraceRecorder() {
            TraceRecorder recorder = Mockito.mock(TraceRecorder.class);
            engine.setTraceRecorder(recorder);

            // destroy 不应调用 mock 的任何方法（TraceRecorder 接口无 shutdown）
            engine.destroy();
            // 无异常即通过
        }

        @Test
        @DisplayName("destroy - AsyncTraceRecorder 调用 shutdown")
        void shouldShutdownAsyncTraceRecorder() {
            AsyncTraceRecorder asyncRecorder = new AsyncTraceRecorder(100, 10, 1000);
            engine.setTraceRecorder(asyncRecorder);

            // destroy 应调用 asyncRecorder.shutdown(5)
            engine.destroy();

            // 再次调用 destroy 不应抛异常（幂等）
            engine.destroy();
        }

        @Test
        @DisplayName("destroy - TimeoutExecutor 调用 shutdown")
        void shouldShutdownTimeoutExecutor() {
            RuleTimeoutExecutor executor = Mockito.mock(RuleTimeoutExecutor.class);
            engine.setTimeoutExecutor(executor);

            engine.destroy();

            verify(executor).shutdown();
        }

        @Test
        @DisplayName("destroy - 同时释放 AsyncTraceRecorder 和 TimeoutExecutor")
        void shouldReleaseBothResources() {
            AsyncTraceRecorder asyncRecorder = new AsyncTraceRecorder(100, 10, 1000);
            engine.setTraceRecorder(asyncRecorder);
            RuleTimeoutExecutor executor = Mockito.mock(RuleTimeoutExecutor.class);
            engine.setTimeoutExecutor(executor);

            engine.destroy();

            verify(executor).shutdown();
        }
    }
}
