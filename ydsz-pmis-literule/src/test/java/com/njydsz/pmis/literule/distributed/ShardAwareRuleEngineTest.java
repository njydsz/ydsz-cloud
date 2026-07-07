package com.njydsz.pmis.literule.distributed;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.api.RuleEngineStats;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ShardAwareRuleEngine 单元测试
 *
 * <p>测试分片感知规则引擎装饰器的本地分片命中、一致性 hash 路由、
 * 节点注册/注销、节点变更重新分片、边界条件（单节点/空集群/null key）、
 * 以及与 DefaultRuleEngine 的委托关系，目标覆盖率 100%。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("ShardAwareRuleEngine 单元测试")
class ShardAwareRuleEngineTest {

    private RuleEngine delegate;
    private NodeRegistry nodeRegistry;
    private ConsistentHashSharder sharder;

    @BeforeEach
    void setUp() {
        delegate = Mockito.mock(RuleEngine.class);
        nodeRegistry = Mockito.mock(NodeRegistry.class);
        sharder = Mockito.mock(ConsistentHashSharder.class);
    }

    // ==================== 辅助方法 ====================

    /** 构造 Rule mock（默认租户 1、scope=null、无互斥组） */
    private Rule mockRule(String code) {
        Rule rule = Mockito.mock(Rule.class);
        when(rule.getCode()).thenReturn(code);
        when(rule.getName()).thenReturn("规则-" + code);
        when(rule.getCategory()).thenReturn("TEST");
        when(rule.getPriority()).thenReturn(100);
        when(rule.getTenantId()).thenReturn("1");
        return rule;
    }

    /** 构造触发型 Rule mock */
    private Rule mockTriggeredRule(String code, RuleSeverity severity) {
        Rule rule = mockRule(code);
        RuleResult result = RuleResult.builder()
                .ruleCode(code)
                .ruleName("规则-" + code)
                .category("TEST")
                .triggered(true)
                .severity(severity)
                .title("标题-" + code)
                .description("描述-" + code)
                .build();
        when(rule.evaluate(any())).thenReturn(result);
        return rule;
    }

    /** 构造未触发的 Rule mock */
    private Rule mockNotTriggeredRule(String code) {
        Rule rule = mockRule(code);
        when(rule.evaluate(any())).thenReturn(RuleResult.notTriggered(code));
        return rule;
    }

    /** 构造默认上下文 */
    private RuleContext defaultContext() {
        Map<String, Object> facts = new HashMap<>();
        facts.put("amount", 1000);
        return RuleContext.of(facts, "DEFAULT", "TEST", "trace-1", "1");
    }

    /** 启用分片：模拟多节点集群（2 节点），selfId=node-1 */
    private void enableSharding() {
        ClusterNode node1 = new ClusterNode("node-1", "localhost:8081");
        ClusterNode node2 = new ClusterNode("node-2", "localhost:8082");
        when(nodeRegistry.getAliveNodes()).thenReturn(List.of(node1, node2));
        when(nodeRegistry.getSelfNodeId()).thenReturn("node-1");
        when(sharder.getNodeCount()).thenReturn(2);
    }

    // ==================== 构造器 ====================

    @Nested
    @DisplayName("构造器")
    class ConstructorTest {

        @Test
        @DisplayName("双参构造器 - 创建默认 ConsistentHashSharder")
        void shouldCreateDefaultSharderWithTwoArgConstructor() {
            when(nodeRegistry.getAliveNodes()).thenReturn(List.of());
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry);

            // 默认 sharder 的环为空，集群规模=0
            engine.refreshNodes();
            assertThat(engine.getClusterSize()).isZero();
            assertThat(engine.isShardingEnabled()).isFalse();
        }

        @Test
        @DisplayName("三参构造器 - 使用传入的 sharder")
        void shouldUseProvidedSharder() {
            when(nodeRegistry.getAliveNodes()).thenReturn(List.of());
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            when(sharder.getNodeCount()).thenReturn(0);

            engine.refreshNodes();

            verify(sharder).updateNodes(any());
        }
    }

    // ==================== 节点刷新 ====================

    @Nested
    @DisplayName("节点刷新")
    class RefreshNodesTest {

        @Test
        @DisplayName("空集群 - 分片关闭，全部本地执行")
        void shouldDisableShardingForEmptyCluster() {
            when(nodeRegistry.getAliveNodes()).thenReturn(List.of());
            when(sharder.getNodeCount()).thenReturn(0);
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);

            engine.refreshNodes();

            assertThat(engine.isShardingEnabled()).isFalse();
            assertThat(engine.getClusterSize()).isZero();
        }

        @Test
        @DisplayName("null 节点列表 - 分片关闭")
        void shouldDisableShardingForNullNodes() {
            when(nodeRegistry.getAliveNodes()).thenReturn(null);
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);

            engine.refreshNodes();

            assertThat(engine.isShardingEnabled()).isFalse();
        }

        @Test
        @DisplayName("单节点集群 - 分片关闭，全部本地执行")
        void shouldDisableShardingForSingle node() {
            ClusterNode node1 = new ClusterNode("node-1", "localhost:8081");
            when(nodeRegistry.getAliveNodes()).thenReturn(List.of(node1));
            when(sharder.getNodeCount()).thenReturn(1);
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);

            engine.refreshNodes();

            assertThat(engine.isShardingEnabled()).isFalse();
            assertThat(engine.getClusterSize()).isEqualTo(1);
        }

        @Test
        @DisplayName("多节点集群 - 分片启用")
        void shouldEnableShardingForMultiNode() {
            enableSharding();
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);

            engine.refreshNodes();

            assertThat(engine.isShardingEnabled()).isTrue();
            assertThat(engine.getClusterSize()).isEqualTo(2);
        }

        @Test
        @DisplayName("相同节点签名 - 不重复重建 hash 环")
        void shouldNotRebuildRingWhenSignatureUnchanged() {
            enableSharding();
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);

            engine.refreshNodes();
            engine.refreshNodes(); // 第二次签名相同

            // sharder.updateNodes 只被调用一次
            verify(sharder, times(1)).updateNodes(any());
        }

        @Test
        @DisplayName("节点变更 - 重新分片")
        void shouldRebuildRingWhenNodesChanged() {
            ClusterNode node1 = new ClusterNode("node-1", "localhost:8081");
            ClusterNode node2 = new ClusterNode("node-2", "localhost:8082");
            when(nodeRegistry.getAliveNodes()).thenReturn(List.of(node1, node2));
            when(nodeRegistry.getSelfNodeId()).thenReturn("node-1");
            when(sharder.getNodeCount()).thenReturn(2);
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);

            engine.refreshNodes();

            // 节点变更：新增 node-3
            ClusterNode node3 = new ClusterNode("node-3", "localhost:8083");
            when(nodeRegistry.getAliveNodes()).thenReturn(List.of(node1, node2, node3));
            when(sharder.getNodeCount()).thenReturn(3);

            engine.refreshNodes();

            // sharder.updateNodes 被调用两次
            verify(sharder, times(2)).updateNodes(any());
        }

        @Test
        @DisplayName("从多节点缩减为单节点 - 分片关闭")
        void shouldDisableShardingWhenScaledDownToSingle() {
            enableSharding();
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);

            engine.refreshNodes();
            assertThat(engine.isShardingEnabled()).isTrue();

            // 缩减为单节点
            ClusterNode node1 = new ClusterNode("node-1", "localhost:8081");
            when(nodeRegistry.getAliveNodes()).thenReturn(List.of(node1));
            when(sharder.getNodeCount()).thenReturn(1);

            engine.refreshNodes();

            assertThat(engine.isShardingEnabled()).isFalse();
        }
    }

    // ==================== 委托方法 ====================

    @Nested
    @DisplayName("委托方法")
    class DelegateTest {

        @Test
        @DisplayName("register - 委托给 delegate")
        void shouldDelegateRegister() {
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            Rule rule = mockRule("R1");

            engine.register(rule);

            verify(delegate).register(rule);
        }

        @Test
        @DisplayName("unregister - 委托给 delegate")
        void shouldDelegateUnregister() {
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);

            engine.unregister("R1");

            verify(delegate).unregister("R1");
        }

        @Test
        @DisplayName("getRules - 委托给 delegate")
        void shouldDelegateGetRules() {
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            List<Rule> rules = List.of(mockRule("R1"));
            when(delegate.getRules()).thenReturn(rules);

            List<Rule> result = engine.getRules();

            assertThat(result).isSameAs(rules);
            verify(delegate).getRules();
        }

        @Test
        @DisplayName("getStats - 委托给 delegate")
        void shouldDelegateGetStats() {
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            RuleEngineStats stats = RuleEngineStats.empty();
            when(delegate.getStats()).thenReturn(stats);

            RuleEngineStats result = engine.getStats();

            assertThat(result).isSameAs(stats);
            verify(delegate).getStats();
        }
    }

    // ==================== evaluate 评估 ====================

    @Nested
    @DisplayName("evaluate 评估")
    class EvaluateTest {

        @Test
        @DisplayName("分片关闭 - 完全委托给 delegate")
        void shouldDelegateEvaluateWhenShardingDisabled() {
            when(nodeRegistry.getAliveNodes()).thenReturn(List.of());
            when(sharder.getNodeCount()).thenReturn(0);
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            engine.refreshNodes();

            List<RuleResult> expected = List.of(RuleResult.builder().ruleCode("R1").triggered(true).build());
            when(delegate.evaluate(any())).thenReturn(expected);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).isSameAs(expected);
            verify(delegate).evaluate(any());
        }

        @Test
        @DisplayName("分片启用 + 规则属于当前节点 - 执行评估")
        void shouldEvaluateMineRules() {
            enableSharding();
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            engine.refreshNodes();

            Rule rule = mockTriggeredRule("R1", RuleSeverity.RED);
            when(delegate.getRules()).thenReturn(List.of(rule));
            when(sharder.isMine(eq("R1"), eq("node-1"))).thenReturn(true);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R1");
            verify(rule).evaluate(any());
            verify(delegate, never()).evaluate(any());
        }

        @Test
        @DisplayName("分片启用 + 规则不属于当前节点 - 跳过")
        void shouldSkipNotMineRules() {
            enableSharding();
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            engine.refreshNodes();

            Rule rule = mockTriggeredRule("R1", RuleSeverity.RED);
            when(delegate.getRules()).thenReturn(List.of(rule));
            when(sharder.isMine(eq("R1"), eq("node-1"))).thenReturn(false);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).isEmpty();
            verify(rule, never()).evaluate(any());
        }

        @Test
        @DisplayName("分片启用 + 混合规则 - 仅执行属于当前节点的")
        void shouldEvaluateOnlyMineRules() {
            enableSharding();
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            engine.refreshNodes();

            Rule mine = mockTriggeredRule("MINE", RuleSeverity.RED);
            Rule notMine = mockTriggeredRule("NOT_MINE", RuleSeverity.YELLOW);
            when(delegate.getRules()).thenReturn(List.of(mine, notMine));
            when(sharder.isMine(eq("MINE"), eq("node-1"))).thenReturn(true);
            when(sharder.isMine(eq("NOT_MINE"), eq("node-1"))).thenReturn(false);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("MINE");
            verify(mine).evaluate(any());
            verify(notMine, never()).evaluate(any());
        }

        @Test
        @DisplayName("分片启用 + 空规则集 - 返回空列表")
        void shouldReturnEmptyWhenNoRules() {
            enableSharding();
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            engine.refreshNodes();

            when(delegate.getRules()).thenReturn(List.of());

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("规则评估抛异常 - 跳过不中断")
        void shouldSkipRuleWhenEvaluateThrows() {
            enableSharding();
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            engine.refreshNodes();

            Rule badRule = mockRule("BAD");
            when(badRule.evaluate(any())).thenThrow(new RuntimeException("评估异常"));
            Rule goodRule = mockTriggeredRule("GOOD", RuleSeverity.RED);
            when(delegate.getRules()).thenReturn(List.of(badRule, goodRule));
            when(sharder.isMine(eq("BAD"), eq("node-1"))).thenReturn(true);
            when(sharder.isMine(eq("GOOD"), eq("node-1"))).thenReturn(true);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("GOOD");
        }

        @Test
        @DisplayName("规则返回 null 结果 - 跳过")
        void shouldSkipNullResult() {
            enableSharding();
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            engine.refreshNodes();

            Rule nullRule = mockRule("NULL");
            when(nullRule.evaluate(any())).thenReturn(null);
            Rule goodRule = mockTriggeredRule("GOOD", RuleSeverity.RED);
            when(delegate.getRules()).thenReturn(List.of(nullRule, goodRule));
            when(sharder.isMine(eq("NULL"), eq("node-1"))).thenReturn(true);
            when(sharder.isMine(eq("GOOD"), eq("node-1"))).thenReturn(true);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("GOOD");
        }

        @Test
        @DisplayName("规则未触发 - evaluate 模式下不包含在结果中")
        void shouldExcludeNotTriggeredInEvaluate() {
            enableSharding();
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            engine.refreshNodes();

            Rule notTriggered = mockNotTriggeredRule("NT");
            when(delegate.getRules()).thenReturn(List.of(notTriggered));
            when(sharder.isMine(eq("NT"), eq("node-1"))).thenReturn(true);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("结果按严重度倒序排列 - RED > YELLOW > INFO")
        void shouldSortResultsBySeverityDescending() {
            enableSharding();
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            engine.refreshNodes();

            Rule info = mockTriggeredRule("INFO", RuleSeverity.INFO);
            Rule red = mockTriggeredRule("RED", RuleSeverity.RED);
            Rule yellow = mockTriggeredRule("YELLOW", RuleSeverity.YELLOW);
            when(delegate.getRules()).thenReturn(List.of(info, red, yellow));
            when(sharder.isMine(eq("INFO"), eq("node-1"))).thenReturn(true);
            when(sharder.isMine(eq("RED"), eq("node-1"))).thenReturn(true);
            when(sharder.isMine(eq("YELLOW"), eq("node-1"))).thenReturn(true);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).extracting(RuleResult::getRuleCode)
                    .containsExactly("RED", "YELLOW", "INFO");
        }

        @Test
        @DisplayName("severity 为 null 的结果排在最后")
        void shouldPutNullSeverityLast() {
            enableSharding();
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            engine.refreshNodes();

            Rule nullSeverity = mockRule("NULL_SEV");
            when(nullSeverity.evaluate(any())).thenReturn(
                    RuleResult.builder().ruleCode("NULL_SEV").triggered(true).build());
            Rule red = mockTriggeredRule("RED", RuleSeverity.RED);
            when(delegate.getRules()).thenReturn(List.of(nullSeverity, red));
            when(sharder.isMine(eq("NULL_SEV"), eq("node-1"))).thenReturn(true);
            when(sharder.isMine(eq("RED"), eq("node-1"))).thenReturn(true);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).extracting(RuleResult::getRuleCode)
                    .containsExactly("RED", "NULL_SEV");
        }
    }

    // ==================== dryRun 仿真 ====================

    @Nested
    @DisplayName("dryRun 仿真")
    class DryRunTest {

        @Test
        @DisplayName("分片关闭 - 完全委托给 delegate")
        void shouldDelegateDryRunWhenShardingDisabled() {
            when(nodeRegistry.getAliveNodes()).thenReturn(List.of());
            when(sharder.getNodeCount()).thenReturn(0);
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            engine.refreshNodes();

            List<RuleResult> expected = List.of(RuleResult.notTriggered("R1"));
            when(delegate.dryRun(any())).thenReturn(expected);

            List<RuleResult> results = engine.dryRun(defaultContext());

            assertThat(results).isSameAs(expected);
            verify(delegate).dryRun(any());
        }

        @Test
        @DisplayName("分片启用 + dryRun 包含未触发结果")
        void shouldIncludeNotTriggeredInDryRun() {
            enableSharding();
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            engine.refreshNodes();

            Rule triggered = mockTriggeredRule("TRIG", RuleSeverity.RED);
            Rule notTriggered = mockNotTriggeredRule("NT");
            when(delegate.getRules()).thenReturn(List.of(triggered, notTriggered));
            when(sharder.isMine(eq("TRIG"), eq("node-1"))).thenReturn(true);
            when(sharder.isMine(eq("NT"), eq("node-1"))).thenReturn(true);

            List<RuleResult> results = engine.dryRun(defaultContext());

            // dryRun 模式下，未触发结果也被包含
            assertThat(results).hasSize(2);
            assertThat(results).extracting(RuleResult::getRuleCode)
                    .containsExactlyInAnyOrder("TRIG", "NT");
        }

        @Test
        @DisplayName("分片启用 + dryRun 中规则抛异常 - 跳过不中断")
        void shouldSkipRuleWhenDryRunThrows() {
            enableSharding();
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            engine.refreshNodes();

            Rule badRule = mockRule("BAD");
            when(badRule.evaluate(any())).thenThrow(new RuntimeException("dryRun 异常"));
            Rule goodRule = mockTriggeredRule("GOOD", RuleSeverity.RED);
            when(delegate.getRules()).thenReturn(List.of(badRule, goodRule));
            when(sharder.isMine(eq("BAD"), eq("node-1"))).thenReturn(true);
            when(sharder.isMine(eq("GOOD"), eq("node-1"))).thenReturn(true);

            List<RuleResult> results = engine.dryRun(defaultContext());

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("GOOD");
        }

        @Test
        @DisplayName("分片启用 + dryRun 空规则集 - 返回空列表")
        void shouldReturnEmptyWhenNoRulesInDryRun() {
            enableSharding();
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            engine.refreshNodes();

            when(delegate.getRules()).thenReturn(List.of());

            List<RuleResult> results = engine.dryRun(defaultContext());

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("dryRun 结果按严重度倒序排列")
        void shouldSortDryRunResultsBySeverity() {
            enableSharding();
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            engine.refreshNodes();

            Rule red = mockTriggeredRule("RED", RuleSeverity.RED);
            Rule info = mockTriggeredRule("INFO", RuleSeverity.INFO);
            when(delegate.getRules()).thenReturn(List.of(info, red));
            when(sharder.isMine(eq("RED"), eq("node-1"))).thenReturn(true);
            when(sharder.isMine(eq("INFO"), eq("node-1"))).thenReturn(true);

            List<RuleResult> results = engine.dryRun(defaultContext());

            assertThat(results).extracting(RuleResult::getRuleCode)
                    .containsExactly("RED", "INFO");
        }
    }

    // ==================== topResult ====================

    @Nested
    @DisplayName("topResult")
    class TopResultTest {

        @Test
        @DisplayName("分片关闭 + 无触发 - 返回 null")
        void shouldReturnNullWhenNoTriggerAndShardingDisabled() {
            when(nodeRegistry.getAliveNodes()).thenReturn(List.of());
            when(sharder.getNodeCount()).thenReturn(0);
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            engine.refreshNodes();

            when(delegate.evaluate(any())).thenReturn(List.of());

            RuleResult result = engine.topResult(defaultContext());

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("分片关闭 + 有触发 - 返回 delegate 的首个结果")
        void shouldReturnDelegateFirstResultWhenShardingDisabled() {
            when(nodeRegistry.getAliveNodes()).thenReturn(List.of());
            when(sharder.getNodeCount()).thenReturn(0);
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            engine.refreshNodes();

            RuleResult expected = RuleResult.builder().ruleCode("R1").triggered(true)
                    .severity(RuleSeverity.RED).build();
            when(delegate.evaluate(any())).thenReturn(List.of(expected));

            RuleResult result = engine.topResult(defaultContext());

            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("分片启用 + 无触发 - 返回 null")
        void shouldReturnNullWhenNoTriggerAndShardingEnabled() {
            enableSharding();
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            engine.refreshNodes();

            when(delegate.getRules()).thenReturn(List.of());

            RuleResult result = engine.topResult(defaultContext());

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("分片启用 + 有触发 - 返回最高严重度结果")
        void shouldReturnHighestSeverityResultWhenShardingEnabled() {
            enableSharding();
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            engine.refreshNodes();

            Rule yellow = mockTriggeredRule("Y", RuleSeverity.YELLOW);
            Rule red = mockTriggeredRule("R", RuleSeverity.RED);
            when(delegate.getRules()).thenReturn(List.of(yellow, red));
            when(sharder.isMine(eq("Y"), eq("node-1"))).thenReturn(true);
            when(sharder.isMine(eq("R"), eq("node-1"))).thenReturn(true);

            RuleResult result = engine.topResult(defaultContext());

            assertThat(result).isNotNull();
            assertThat(result.getRuleCode()).isEqualTo("R");
            assertThat(result.getSeverity()).isEqualTo(RuleSeverity.RED);
        }
    }

    // ==================== 分片判断 ====================

    @Nested
    @DisplayName("分片判断")
    class ShardingTest {

        @Test
        @DisplayName("分片关闭 - isMine 总是返回 true")
        void shouldReturnTrueForAllWhenShardingDisabled() {
            when(nodeRegistry.getAliveNodes()).thenReturn(List.of());
            when(sharder.getNodeCount()).thenReturn(0);
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            engine.refreshNodes();

            assertThat(engine.isMine("ANY_CODE")).isTrue();
            verify(sharder, never()).isMine(anyString(), anyString());
        }

        @Test
        @DisplayName("分片启用 - isMine 委托给 sharder")
        void shouldDelegateIsMineToSharderWhenEnabled() {
            enableSharding();
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            engine.refreshNodes();

            when(sharder.isMine(eq("R1"), eq("node-1"))).thenReturn(true);
            when(sharder.isMine(eq("R2"), eq("node-1"))).thenReturn(false);

            assertThat(engine.isMine("R1")).isTrue();
            assertThat(engine.isMine("R2")).isFalse();
        }

        @Test
        @DisplayName("getClusterSize - 返回 sharder 节点数")
        void shouldReturnClusterSizeFromSharder() {
            when(sharder.getNodeCount()).thenReturn(5);
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);

            assertThat(engine.getClusterSize()).isEqualTo(5);
        }

        @Test
        @DisplayName("isShardingEnabled - 反映当前分片状态")
        void shouldReflectShardingEnabledStatus() {
            when(nodeRegistry.getAliveNodes()).thenReturn(List.of());
            when(sharder.getNodeCount()).thenReturn(0);
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);

            engine.refreshNodes();
            assertThat(engine.isShardingEnabled()).isFalse();
        }
    }

    // ==================== filterMineRules 边界 ====================

    @Nested
    @DisplayName("filterMineRules 边界")
    class FilterMineRulesTest {

        @Test
        @DisplayName("null 规则 - 视为属于当前节点")
        void shouldIncludeNullRule() {
            enableSharding();
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            engine.refreshNodes();

            Rule goodRule = mockTriggeredRule("GOOD", RuleSeverity.RED);
            when(delegate.getRules()).thenReturn(new ArrayList<>(List.of(null, goodRule)));
            when(sharder.isMine(eq("GOOD"), eq("node-1"))).thenReturn(true);

            List<RuleResult> results = engine.evaluate(defaultContext());

            // null 规则被包含但 evaluate 不会抛异常（null.evaluate 由 mock 处理）
            // 实际上 null 规则在 evaluateSubset 中会抛 NPE 被捕获
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("GOOD");
        }

        @Test
        @DisplayName("code 为 null 的规则 - 视为属于当前节点")
        void shouldIncludeRuleWithNullCode() {
            enableSharding();
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, nodeRegistry, sharder);
            engine.refreshNodes();

            Rule nullCodeRule = mockRule("has-code");
            // 覆写 getCode 返回 null
            when(nullCodeRule.getCode()).thenReturn(null);
            when(nullCodeRule.evaluate(any())).thenReturn(
                    RuleResult.builder().ruleCode(null).triggered(true).severity(RuleSeverity.RED).build());

            when(delegate.getRules()).thenReturn(List.of(nullCodeRule));

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).hasSize(1);
            verify(sharder, never()).isMine(anyString(), anyString());
        }
    }
}
