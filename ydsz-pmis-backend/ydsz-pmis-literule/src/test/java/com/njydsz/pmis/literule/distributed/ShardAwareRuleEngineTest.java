package com.njydsz.pmis.literule.distributed;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.api.RuleEngineStats;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ShardAwareRuleEngine 单元测试
 *
 * <p>测试分片感知规则引擎装饰器的构造器、节点刷新、委托方法、
 * evaluate/dryRun 分片过滤与本地执行、topResult、isMine、
 * 一致性 hash 路由、节点变更重新分片、filterMineRules 边界等核心能力，
 * 目标覆盖率 100%。
 *
 * <p>测试策略：
 * <ul>
 *   <li>节点刷新/一致性 hash 路由：使用真实 InMemoryNodeRegistry + 真实 ConsistentHashSharder</li>
 *   <li>evaluate/dryRun/topResult 分片过滤：使用 mock delegate + mock sharder 精确控制 isMine</li>
 *   <li>委托方法：使用 mock delegate 验证调用转发</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 */
@DisplayName("ShardAwareRuleEngine 单元测试")
class ShardAwareRuleEngineTest {

    // ==================== 辅助方法 ====================

    /** 构造 Rule mock 测试桩 */
    private Rule mockRule(String code) {
        Rule rule = Mockito.mock(Rule.class);
        when(rule.getCode()).thenReturn(code);
        when(rule.getName()).thenReturn("规则-" + code);
        when(rule.getCategory()).thenReturn("TEST");
        return rule;
    }

    /** 构造已触发的 Rule mock */
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

    /**
     * 构造分片启用的 mock 测试引擎
     *
     * <p>使用 mock NodeRegistry + mock ConsistentHashSharder，
     * 配置 2 节点集群（self + node-2），分片已启用。
     *
     * @param delegate 被装饰引擎 mock
     * @param sharder  分片器 mock
     * @param registry 节点注册表 mock
     * @return 已刷新节点的 ShardAwareRuleEngine（shardingEnabled=true）
     */
    private ShardAwareRuleEngine mockShardingEngine(RuleEngine delegate,
                                                     ConsistentHashSharder sharder,
                                                     NodeRegistry registry) {
        when(registry.getSelfNodeId()).thenReturn("self");
        when(registry.getAliveNodes()).thenReturn(List.of(
                new ClusterNode("self", "h1"), new ClusterNode("node-2", "h2")));
        when(sharder.getNodeCount()).thenReturn(2);
        ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, registry, sharder);
        engine.refreshNodes();
        return engine;
    }

    /**
     * 构造分片关闭的测试引擎（单节点集群）
     *
     * <p>注册 self 单节点，refreshNodes 后 count=1 → shardingEnabled=false。
     * 注意：空集群首次刷新时签名 "" 等于初始 lastSignature ""，
     * 会直接返回不更新，shardingEnabled 保持初始 true，因此需用单节点而非空集群。
     *
     * @param delegate 被装饰引擎 mock
     * @return 已刷新节点的 ShardAwareRuleEngine（shardingEnabled=false）
     */
    private ShardAwareRuleEngine disableShardingEngine(RuleEngine delegate) {
        InMemoryNodeRegistry registry = new InMemoryNodeRegistry("self");
        registry.register(new ClusterNode("self", "localhost:8080"));
        ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, registry);
        engine.refreshNodes();
        return engine;
    }

    // ==================== 构造器 ====================

    @Nested
    @DisplayName("构造器")
    class ConstructorTest {

        @Test
        @DisplayName("双参构造器 - 使用默认 ConsistentHashSharder")
        void shouldCreateWithDefaultSharder() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            InMemoryNodeRegistry registry = new InMemoryNodeRegistry("self");

            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, registry);

            // 默认 shardingEnabled=true，未刷新节点时 clusterSize=0
            assertThat(engine.isShardingEnabled()).isTrue();
            assertThat(engine.getClusterSize()).isZero();
        }

        @Test
        @DisplayName("三参构造器 - 使用自定义 sharder")
        void shouldCreateWithCustomSharder() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            InMemoryNodeRegistry registry = new InMemoryNodeRegistry("self");
            ConsistentHashSharder sharder = new ConsistentHashSharder(64);

            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, registry, sharder);

            assertThat(engine.isShardingEnabled()).isTrue();
        }
    }

    // ==================== 节点刷新 ====================

    @Nested
    @DisplayName("节点刷新")
    class RefreshNodesTest {

        @Test
        @DisplayName("空集群首次刷新 - 签名与初始值相同，shardingEnabled 保持初始 true")
        void shouldKeepShardingEnabledForEmptyClusterFirstRefresh() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            InMemoryNodeRegistry registry = new InMemoryNodeRegistry("self");
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, registry);

            engine.refreshNodes();

            // 空集群 sig="" 等于初始 lastSignature="" → 直接返回，shardingEnabled 保持初始 true
            assertThat(engine.isShardingEnabled()).isTrue();
            assertThat(engine.getClusterSize()).isZero();
        }

        @Test
        @DisplayName("多节点降为空集群 - 分片关闭")
        void shouldDisableShardingWhenDownscaleToEmpty() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            InMemoryNodeRegistry registry = new InMemoryNodeRegistry("self");
            registry.register(new ClusterNode("self", "h1"));
            registry.register(new ClusterNode("node-2", "h2"));
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, registry);

            engine.refreshNodes();
            assertThat(engine.isShardingEnabled()).isTrue();

            registry.unregister("self");
            registry.unregister("node-2");
            engine.refreshNodes();

            // sig="" ≠ 之前的 "node-2,self," → 更新 → count=0 → shardingEnabled=false
            assertThat(engine.isShardingEnabled()).isFalse();
            assertThat(engine.getClusterSize()).isZero();
        }

        @Test
        @DisplayName("单节点 - 分片关闭")
        void shouldDisableShardingForSingleNode() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            InMemoryNodeRegistry registry = new InMemoryNodeRegistry("self");
            registry.register(new ClusterNode("self", "localhost:8080"));
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, registry);

            engine.refreshNodes();

            assertThat(engine.isShardingEnabled()).isFalse();
            assertThat(engine.getClusterSize()).isEqualTo(1);
        }

        @Test
        @DisplayName("多节点 - 分片启用")
        void shouldEnableShardingForMultipleNodes() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            InMemoryNodeRegistry registry = new InMemoryNodeRegistry("self");
            registry.register(new ClusterNode("self", "localhost:8080"));
            registry.register(new ClusterNode("node-2", "localhost:8081"));
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, registry);

            engine.refreshNodes();

            assertThat(engine.isShardingEnabled()).isTrue();
            assertThat(engine.getClusterSize()).isEqualTo(2);
        }

        @Test
        @DisplayName("签名不变 - 无副作用")
        void shouldNoOpWhenSignatureUnchanged() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            InMemoryNodeRegistry registry = new InMemoryNodeRegistry("self");
            registry.register(new ClusterNode("self", "localhost:8080"));
            registry.register(new ClusterNode("node-2", "localhost:8081"));
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, registry);

            engine.refreshNodes();
            boolean firstState = engine.isShardingEnabled();
            int firstCount = engine.getClusterSize();

            // 再次刷新，签名不变
            engine.refreshNodes();

            assertThat(engine.isShardingEnabled()).isEqualTo(firstState);
            assertThat(engine.getClusterSize()).isEqualTo(firstCount);
        }

        @Test
        @DisplayName("多节点降为单节点 - 分片关闭")
        void shouldDisableShardingWhenDownscaleToSingle() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            InMemoryNodeRegistry registry = new InMemoryNodeRegistry("self");
            registry.register(new ClusterNode("self", "localhost:8080"));
            registry.register(new ClusterNode("node-2", "localhost:8081"));
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, registry);

            engine.refreshNodes();
            assertThat(engine.isShardingEnabled()).isTrue();

            registry.unregister("node-2");
            engine.refreshNodes();

            assertThat(engine.isShardingEnabled()).isFalse();
            assertThat(engine.getClusterSize()).isEqualTo(1);
        }

        @Test
        @DisplayName("单节点扩展为多节点 - 分片启用")
        void shouldEnableShardingWhenUpscaleToMultiple() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            InMemoryNodeRegistry registry = new InMemoryNodeRegistry("self");
            registry.register(new ClusterNode("self", "localhost:8080"));
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, registry);

            engine.refreshNodes();
            assertThat(engine.isShardingEnabled()).isFalse();

            registry.register(new ClusterNode("node-2", "localhost:8081"));
            engine.refreshNodes();

            assertThat(engine.isShardingEnabled()).isTrue();
            assertThat(engine.getClusterSize()).isEqualTo(2);
        }

        @Test
        @DisplayName("getAliveNodes 返回 null - 签名为空串，与初始 lastSignature 相同，直接返回")
        void shouldNoOpWhenAliveNodesNull() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            NodeRegistry registry = Mockito.mock(NodeRegistry.class);
            ConsistentHashSharder sharder = Mockito.mock(ConsistentHashSharder.class);
            when(registry.getAliveNodes()).thenReturn(null);
            when(sharder.getNodeCount()).thenReturn(0);

            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, registry, sharder);
            engine.refreshNodes();

            // sig="" equals lastSignature="" → 直接 return，不调用 updateNodes
            verify(sharder, never()).updateNodes(any());
            // shardingEnabled 保持初始值 true
            assertThat(engine.isShardingEnabled()).isTrue();
        }
    }

    // ==================== 委托方法 ====================

    @Nested
    @DisplayName("委托方法")
    class DelegateTest {

        @Test
        @DisplayName("register - 委托给 delegate")
        void shouldDelegateRegister() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            InMemoryNodeRegistry registry = new InMemoryNodeRegistry("self");
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, registry);
            Rule rule = mockRule("R1");

            engine.register(rule);

            verify(delegate).register(rule);
        }

        @Test
        @DisplayName("unregister - 委托给 delegate")
        void shouldDelegateUnregister() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            InMemoryNodeRegistry registry = new InMemoryNodeRegistry("self");
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, registry);

            engine.unregister("R1");

            verify(delegate).unregister("R1");
        }

        @Test
        @DisplayName("getRules - 委托给 delegate")
        void shouldDelegateGetRules() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            Rule rule = mockRule("R1");
            when(delegate.getRules()).thenReturn(List.of(rule));
            InMemoryNodeRegistry registry = new InMemoryNodeRegistry("self");
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, registry);

            List<Rule> rules = engine.getRules();

            assertThat(rules).containsExactly(rule);
            verify(delegate).getRules();
        }

        @Test
        @DisplayName("getStats - 委托给 delegate")
        void shouldDelegateGetStats() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            RuleEngineStats stats = RuleEngineStats.empty();
            when(delegate.getStats()).thenReturn(stats);
            InMemoryNodeRegistry registry = new InMemoryNodeRegistry("self");
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, registry);

            assertThat(engine.getStats()).isSameAs(stats);
            verify(delegate).getStats();
        }
    }

    // ==================== evaluate 评估流程 ====================

    @Nested
    @DisplayName("evaluate 评估流程")
    class EvaluateTest {

        @Test
        @DisplayName("分片关闭 - 委托给 delegate.evaluate")
        void shouldDelegateWhenShardingDisabled() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            ShardAwareRuleEngine engine = disableShardingEngine(delegate);

            List<RuleResult> expected = List.of(
                    RuleResult.builder().ruleCode("R1").triggered(true).build());
            when(delegate.evaluate(any())).thenReturn(expected);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).isSameAs(expected);
            verify(delegate).evaluate(any());
        }

        @Test
        @DisplayName("分片启用 - 仅评估 mine 规则")
        void shouldEvaluateOnlyMineRules() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            NodeRegistry registry = Mockito.mock(NodeRegistry.class);
            ConsistentHashSharder sharder = Mockito.mock(ConsistentHashSharder.class);
            ShardAwareRuleEngine engine = mockShardingEngine(delegate, sharder, registry);

            Rule r1 = mockTriggeredRule("R1", RuleSeverity.RED);
            Rule r2 = mockTriggeredRule("R2", RuleSeverity.YELLOW);
            when(delegate.getRules()).thenReturn(List.of(r1, r2));
            when(sharder.isMine("R1", "self")).thenReturn(true);
            when(sharder.isMine("R2", "self")).thenReturn(false);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R1");
            verify(r1).evaluate(any());
            verify(r2, never()).evaluate(any());
        }

        @Test
        @DisplayName("分片启用 - 空规则列表返回空结果")
        void shouldReturnEmptyForEmptyRules() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            NodeRegistry registry = Mockito.mock(NodeRegistry.class);
            ConsistentHashSharder sharder = Mockito.mock(ConsistentHashSharder.class);
            ShardAwareRuleEngine engine = mockShardingEngine(delegate, sharder, registry);

            when(delegate.getRules()).thenReturn(List.of());

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("分片启用 - null 结果被跳过")
        void shouldSkipNullResult() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            NodeRegistry registry = Mockito.mock(NodeRegistry.class);
            ConsistentHashSharder sharder = Mockito.mock(ConsistentHashSharder.class);
            ShardAwareRuleEngine engine = mockShardingEngine(delegate, sharder, registry);

            Rule r1 = mockRule("R1");
            when(r1.evaluate(any())).thenReturn(null);
            when(delegate.getRules()).thenReturn(List.of(r1));
            when(sharder.isMine("R1", "self")).thenReturn(true);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("分片启用 - 未触发结果被跳过")
        void shouldSkipNotTriggeredResult() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            NodeRegistry registry = Mockito.mock(NodeRegistry.class);
            ConsistentHashSharder sharder = Mockito.mock(ConsistentHashSharder.class);
            ShardAwareRuleEngine engine = mockShardingEngine(delegate, sharder, registry);

            Rule r1 = mockNotTriggeredRule("R1");
            when(delegate.getRules()).thenReturn(List.of(r1));
            when(sharder.isMine("R1", "self")).thenReturn(true);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("分片启用 - 规则异常被捕获跳过")
        void shouldSkipRuleWhenExceptionThrown() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            NodeRegistry registry = Mockito.mock(NodeRegistry.class);
            ConsistentHashSharder sharder = Mockito.mock(ConsistentHashSharder.class);
            ShardAwareRuleEngine engine = mockShardingEngine(delegate, sharder, registry);

            Rule r1 = mockRule("R1");
            when(r1.evaluate(any())).thenThrow(new RuntimeException("评估异常"));
            Rule r2 = mockTriggeredRule("R2", RuleSeverity.RED);
            when(delegate.getRules()).thenReturn(List.of(r1, r2));
            when(sharder.isMine("R1", "self")).thenReturn(true);
            when(sharder.isMine("R2", "self")).thenReturn(true);

            List<RuleResult> results = engine.evaluate(defaultContext());

            // r1 异常被跳过，r2 正常返回
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R2");
        }

        @Test
        @DisplayName("分片启用 - 结果按严重度倒序排列")
        void shouldSortResultsBySeverityDescending() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            NodeRegistry registry = Mockito.mock(NodeRegistry.class);
            ConsistentHashSharder sharder = Mockito.mock(ConsistentHashSharder.class);
            ShardAwareRuleEngine engine = mockShardingEngine(delegate, sharder, registry);

            Rule info = mockTriggeredRule("INFO_R", RuleSeverity.INFO);
            Rule red = mockTriggeredRule("RED_R", RuleSeverity.RED);
            Rule yellow = mockTriggeredRule("YELLOW_R", RuleSeverity.YELLOW);
            when(delegate.getRules()).thenReturn(List.of(info, red, yellow));
            when(sharder.isMine("INFO_R", "self")).thenReturn(true);
            when(sharder.isMine("RED_R", "self")).thenReturn(true);
            when(sharder.isMine("YELLOW_R", "self")).thenReturn(true);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).extracting(RuleResult::getRuleCode)
                    .containsExactly("RED_R", "YELLOW_R", "INFO_R");
        }

        @Test
        @DisplayName("分片启用 - null severity 排在最后")
        void shouldPutNullSeverityLast() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            NodeRegistry registry = Mockito.mock(NodeRegistry.class);
            ConsistentHashSharder sharder = Mockito.mock(ConsistentHashSharder.class);
            ShardAwareRuleEngine engine = mockShardingEngine(delegate, sharder, registry);

            Rule nullSev = mockRule("NULL_SEV");
            when(nullSev.evaluate(any())).thenReturn(
                    RuleResult.builder().ruleCode("NULL_SEV").triggered(true).build());
            Rule red = mockTriggeredRule("RED_R", RuleSeverity.RED);
            when(delegate.getRules()).thenReturn(List.of(nullSev, red));
            when(sharder.isMine("NULL_SEV", "self")).thenReturn(true);
            when(sharder.isMine("RED_R", "self")).thenReturn(true);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).extracting(RuleResult::getRuleCode)
                    .containsExactly("RED_R", "NULL_SEV");
        }

        @Test
        @DisplayName("分片启用 - null code 规则被保留评估")
        void shouldKeepRuleWithNullCode() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            NodeRegistry registry = Mockito.mock(NodeRegistry.class);
            ConsistentHashSharder sharder = Mockito.mock(ConsistentHashSharder.class);
            ShardAwareRuleEngine engine = mockShardingEngine(delegate, sharder, registry);

            Rule nullCodeRule = Mockito.mock(Rule.class);
            when(nullCodeRule.getCode()).thenReturn(null);
            when(nullCodeRule.evaluate(any())).thenReturn(
                    RuleResult.builder().ruleCode("null").triggered(true).severity(RuleSeverity.RED).build());
            when(delegate.getRules()).thenReturn(List.of(nullCodeRule));
            // null code → filterMineRules 返回 true，保留该规则

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).hasSize(1);
            verify(nullCodeRule).evaluate(any());
        }

        @Test
        @DisplayName("分片启用 - 规则列表含 null 元素时 catch 块 rule.getCode() NPE 传播")
        void shouldPropagateNpeWhenRuleIsNull() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            NodeRegistry registry = Mockito.mock(NodeRegistry.class);
            ConsistentHashSharder sharder = Mockito.mock(ConsistentHashSharder.class);
            ShardAwareRuleEngine engine = mockShardingEngine(delegate, sharder, registry);

            // 列表包含 null 元素
            when(delegate.getRules()).thenReturn(Arrays.asList((Rule) null));

            // filterMineRules 保留 null（r==null → return true），
            // evaluateSubset 中 rule.evaluate NPE → catch 块 rule.getCode() 再次 NPE → 传播
            assertThatThrownBy(() -> engine.evaluate(defaultContext()))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ==================== dryRun 仿真 ====================

    @Nested
    @DisplayName("dryRun 仿真")
    class DryRunTest {

        @Test
        @DisplayName("分片关闭 - 委托给 delegate.dryRun")
        void shouldDelegateWhenShardingDisabled() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            ShardAwareRuleEngine engine = disableShardingEngine(delegate);

            List<RuleResult> expected = List.of(
                    RuleResult.builder().ruleCode("R1").triggered(false).build());
            when(delegate.dryRun(any())).thenReturn(expected);

            List<RuleResult> results = engine.dryRun(defaultContext());

            assertThat(results).isSameAs(expected);
            verify(delegate).dryRun(any());
        }

        @Test
        @DisplayName("分片启用 - 包含未触发结果")
        void shouldIncludeNotTriggeredInDryRun() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            NodeRegistry registry = Mockito.mock(NodeRegistry.class);
            ConsistentHashSharder sharder = Mockito.mock(ConsistentHashSharder.class);
            ShardAwareRuleEngine engine = mockShardingEngine(delegate, sharder, registry);

            Rule triggered = mockTriggeredRule("R1", RuleSeverity.RED);
            Rule notTriggered = mockNotTriggeredRule("R2");
            when(delegate.getRules()).thenReturn(List.of(triggered, notTriggered));
            when(sharder.isMine("R1", "self")).thenReturn(true);
            when(sharder.isMine("R2", "self")).thenReturn(true);

            List<RuleResult> results = engine.dryRun(defaultContext());

            // dryRun 包含所有结果（含未触发）
            assertThat(results).hasSize(2);
            assertThat(results).extracting(RuleResult::getRuleCode)
                    .containsExactlyInAnyOrder("R1", "R2");
        }

        @Test
        @DisplayName("分片启用 - 空规则列表返回空结果")
        void shouldReturnEmptyForEmptyRulesInDryRun() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            NodeRegistry registry = Mockito.mock(NodeRegistry.class);
            ConsistentHashSharder sharder = Mockito.mock(ConsistentHashSharder.class);
            ShardAwareRuleEngine engine = mockShardingEngine(delegate, sharder, registry);

            when(delegate.getRules()).thenReturn(List.of());

            List<RuleResult> results = engine.dryRun(defaultContext());

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("分片启用 - null 结果被跳过")
        void shouldSkipNullResultInDryRun() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            NodeRegistry registry = Mockito.mock(NodeRegistry.class);
            ConsistentHashSharder sharder = Mockito.mock(ConsistentHashSharder.class);
            ShardAwareRuleEngine engine = mockShardingEngine(delegate, sharder, registry);

            Rule r1 = mockRule("R1");
            when(r1.evaluate(any())).thenReturn(null);
            when(delegate.getRules()).thenReturn(List.of(r1));
            when(sharder.isMine("R1", "self")).thenReturn(true);

            List<RuleResult> results = engine.dryRun(defaultContext());

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("分片启用 - 异常被捕获跳过")
        void shouldSkipExceptionInDryRun() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            NodeRegistry registry = Mockito.mock(NodeRegistry.class);
            ConsistentHashSharder sharder = Mockito.mock(ConsistentHashSharder.class);
            ShardAwareRuleEngine engine = mockShardingEngine(delegate, sharder, registry);

            Rule r1 = mockRule("R1");
            when(r1.evaluate(any())).thenThrow(new RuntimeException("dryRun 异常"));
            Rule r2 = mockTriggeredRule("R2", RuleSeverity.RED);
            when(delegate.getRules()).thenReturn(List.of(r1, r2));
            when(sharder.isMine("R1", "self")).thenReturn(true);
            when(sharder.isMine("R2", "self")).thenReturn(true);

            List<RuleResult> results = engine.dryRun(defaultContext());

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("R2");
        }

        @Test
        @DisplayName("分片启用 - 结果按严重度倒序排列")
        void shouldSortBySeverityInDryRun() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            NodeRegistry registry = Mockito.mock(NodeRegistry.class);
            ConsistentHashSharder sharder = Mockito.mock(ConsistentHashSharder.class);
            ShardAwareRuleEngine engine = mockShardingEngine(delegate, sharder, registry);

            Rule yellow = mockTriggeredRule("Y_R", RuleSeverity.YELLOW);
            Rule red = mockTriggeredRule("R_R", RuleSeverity.RED);
            when(delegate.getRules()).thenReturn(List.of(yellow, red));
            when(sharder.isMine("Y_R", "self")).thenReturn(true);
            when(sharder.isMine("R_R", "self")).thenReturn(true);

            List<RuleResult> results = engine.dryRun(defaultContext());

            assertThat(results).extracting(RuleResult::getRuleCode)
                    .containsExactly("R_R", "Y_R");
        }
    }

    // ==================== topResult ====================

    @Nested
    @DisplayName("topResult 顶部结果")
    class TopResultTest {

        @Test
        @DisplayName("无触发 - 返回 null")
        void shouldReturnNullWhenNoTrigger() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            ShardAwareRuleEngine engine = disableShardingEngine(delegate);

            when(delegate.evaluate(any())).thenReturn(List.of());

            RuleResult result = engine.topResult(defaultContext());

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("有触发 - 返回第一个结果")
        void shouldReturnFirstResult() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            ShardAwareRuleEngine engine = disableShardingEngine(delegate);

            RuleResult expected = RuleResult.builder()
                    .ruleCode("R1").triggered(true).severity(RuleSeverity.RED).build();
            when(delegate.evaluate(any())).thenReturn(List.of(expected));

            RuleResult result = engine.topResult(defaultContext());

            assertThat(result).isSameAs(expected);
        }

        @Test
        @DisplayName("evaluate 返回 null - 返回 null")
        void shouldReturnNullWhenEvaluateReturnsNull() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            ShardAwareRuleEngine engine = disableShardingEngine(delegate);

            when(delegate.evaluate(any())).thenReturn(null);

            RuleResult result = engine.topResult(defaultContext());

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("分片启用 - topResult 返回最高严重度结果")
        void shouldReturnHighestSeverityInShardingMode() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            NodeRegistry registry = Mockito.mock(NodeRegistry.class);
            ConsistentHashSharder sharder = Mockito.mock(ConsistentHashSharder.class);
            ShardAwareRuleEngine engine = mockShardingEngine(delegate, sharder, registry);

            Rule red = mockTriggeredRule("RED_R", RuleSeverity.RED);
            Rule yellow = mockTriggeredRule("YELLOW_R", RuleSeverity.YELLOW);
            when(delegate.getRules()).thenReturn(List.of(yellow, red));
            when(sharder.isMine("RED_R", "self")).thenReturn(true);
            when(sharder.isMine("YELLOW_R", "self")).thenReturn(true);

            RuleResult result = engine.topResult(defaultContext());

            assertThat(result).isNotNull();
            assertThat(result.getRuleCode()).isEqualTo("RED_R");
            assertThat(result.getSeverity()).isEqualTo(RuleSeverity.RED);
        }
    }

    // ==================== 分片查询方法 ====================

    @Nested
    @DisplayName("分片查询方法")
    class ShardingQueryTest {

        @Test
        @DisplayName("isMine - 分片关闭返回 true")
        void shouldReturnTrueWhenShardingDisabled() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            ShardAwareRuleEngine engine = disableShardingEngine(delegate);

            assertThat(engine.isMine("ANY_CODE")).isTrue();
        }

        @Test
        @DisplayName("isMine - 分片启用委托 sharder")
        void shouldDelegateToSharderWhenShardingEnabled() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            NodeRegistry registry = Mockito.mock(NodeRegistry.class);
            ConsistentHashSharder sharder = Mockito.mock(ConsistentHashSharder.class);
            ShardAwareRuleEngine engine = mockShardingEngine(delegate, sharder, registry);

            when(sharder.isMine("R1", "self")).thenReturn(true);
            when(sharder.isMine("R2", "self")).thenReturn(false);

            assertThat(engine.isMine("R1")).isTrue();
            assertThat(engine.isMine("R2")).isFalse();
        }

        @Test
        @DisplayName("getClusterSize - 返回 sharder 节点数")
        void shouldReturnClusterSize() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            InMemoryNodeRegistry registry = new InMemoryNodeRegistry("self");
            registry.register(new ClusterNode("self", "h1"));
            registry.register(new ClusterNode("node-2", "h2"));
            registry.register(new ClusterNode("node-3", "h3"));
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, registry);

            engine.refreshNodes();

            assertThat(engine.getClusterSize()).isEqualTo(3);
        }

        @Test
        @DisplayName("isShardingEnabled - 反映分片状态")
        void shouldReflectShardingState() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            InMemoryNodeRegistry registry = new InMemoryNodeRegistry("self");
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, registry);

            // 初始状态：分片启用
            assertThat(engine.isShardingEnabled()).isTrue();

            // 单节点 → 分片关闭
            registry.register(new ClusterNode("self", "localhost:8080"));
            engine.refreshNodes();
            assertThat(engine.isShardingEnabled()).isFalse();
        }
    }

    // ==================== 一致性 hash 路由 ====================

    @Nested
    @DisplayName("一致性 hash 路由")
    class ConsistentHashRoutingTest {

        @Test
        @DisplayName("同一 ruleCode 多次调用 - 路由结果一致")
        void shouldRouteConsistently() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            InMemoryNodeRegistry registry = new InMemoryNodeRegistry("self");
            registry.register(new ClusterNode("self", "h1"));
            registry.register(new ClusterNode("node-2", "h2"));
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, registry);
            engine.refreshNodes();

            boolean first = engine.isMine("R001");
            for (int i = 0; i < 10; i++) {
                assertThat(engine.isMine("R001")).isEqualTo(first);
            }
        }

        @Test
        @DisplayName("多个 ruleCode - 分布到不同节点")
        void shouldDistributeAcrossNodes() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            InMemoryNodeRegistry registry = new InMemoryNodeRegistry("self");
            registry.register(new ClusterNode("self", "h1"));
            registry.register(new ClusterNode("node-2", "h2"));
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, registry);
            engine.refreshNodes();

            boolean hasMine = false;
            boolean hasNotMine = false;
            for (int i = 0; i < 100; i++) {
                if (engine.isMine("RULE_" + i)) {
                    hasMine = true;
                } else {
                    hasNotMine = true;
                }
            }
            // 100 个不同 key 应该同时存在属于和不属于当前节点的
            assertThat(hasMine).isTrue();
            assertThat(hasNotMine).isTrue();
        }

        @Test
        @DisplayName("节点变更后 - 同一 ruleCode 路由可能改变")
        void shouldRerouteAfterNodeChange() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            InMemoryNodeRegistry registry = new InMemoryNodeRegistry("self");
            registry.register(new ClusterNode("self", "h1"));
            registry.register(new ClusterNode("node-2", "h2"));
            ShardAwareRuleEngine engine = new ShardAwareRuleEngine(delegate, registry);
            engine.refreshNodes();

            // 收集 2 节点时的路由结果
            Map<String, Boolean> before = new HashMap<>();
            for (int i = 0; i < 50; i++) {
                String code = "RULE_" + i;
                before.put(code, engine.isMine(code));
            }

            // 扩展到 3 节点
            registry.register(new ClusterNode("node-3", "h3"));
            engine.refreshNodes();
            assertThat(engine.getClusterSize()).isEqualTo(3);

            // 至少有一个 code 的路由结果发生变化
            boolean hasChange = false;
            for (Map.Entry<String, Boolean> e : before.entrySet()) {
                Boolean after = engine.isMine(e.getKey());
                if (!after.equals(e.getValue())) {
                    hasChange = true;
                    break;
                }
            }
            assertThat(hasChange).isTrue();
        }
    }

    // ==================== filterMineRules 边界 ====================

    @Nested
    @DisplayName("filterMineRules 边界")
    class FilterMineRulesTest {

        @Test
        @DisplayName("null code 规则 - 被保留（return true）")
        void shouldKeepRuleWithNullCodeInFilter() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            NodeRegistry registry = Mockito.mock(NodeRegistry.class);
            ConsistentHashSharder sharder = Mockito.mock(ConsistentHashSharder.class);
            ShardAwareRuleEngine engine = mockShardingEngine(delegate, sharder, registry);

            Rule nullCodeRule = Mockito.mock(Rule.class);
            when(nullCodeRule.getCode()).thenReturn(null);
            when(nullCodeRule.evaluate(any())).thenReturn(
                    RuleResult.builder().ruleCode("null-code").triggered(true).severity(RuleSeverity.INFO).build());
            when(delegate.getRules()).thenReturn(List.of(nullCodeRule));

            List<RuleResult> results = engine.evaluate(defaultContext());

            // null code → filterMineRules 保留 → evaluate 正常返回
            assertThat(results).hasSize(1);
            verify(sharder, never()).isMine(any(), any());
        }

        @Test
        @DisplayName("mine 和 notMine 混合 - 仅评估 mine 规则")
        void shouldFilterMixedRules() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            NodeRegistry registry = Mockito.mock(NodeRegistry.class);
            ConsistentHashSharder sharder = Mockito.mock(ConsistentHashSharder.class);
            ShardAwareRuleEngine engine = mockShardingEngine(delegate, sharder, registry);

            Rule mine1 = mockTriggeredRule("MINE_1", RuleSeverity.RED);
            Rule notMine = mockTriggeredRule("NOT_MINE", RuleSeverity.YELLOW);
            Rule mine2 = mockTriggeredRule("MINE_2", RuleSeverity.INFO);
            when(delegate.getRules()).thenReturn(List.of(mine1, notMine, mine2));
            when(sharder.isMine("MINE_1", "self")).thenReturn(true);
            when(sharder.isMine("NOT_MINE", "self")).thenReturn(false);
            when(sharder.isMine("MINE_2", "self")).thenReturn(true);

            List<RuleResult> results = engine.evaluate(defaultContext());

            assertThat(results).hasSize(2);
            assertThat(results).extracting(RuleResult::getRuleCode)
                    .containsExactly("MINE_1", "MINE_2");
            verify(mine1).evaluate(any());
            verify(notMine, never()).evaluate(any());
            verify(mine2).evaluate(any());
        }

        @Test
        @DisplayName("dryRun - mine 和 notMine 混合")
        void shouldFilterMixedRulesInDryRun() {
            RuleEngine delegate = Mockito.mock(RuleEngine.class);
            NodeRegistry registry = Mockito.mock(NodeRegistry.class);
            ConsistentHashSharder sharder = Mockito.mock(ConsistentHashSharder.class);
            ShardAwareRuleEngine engine = mockShardingEngine(delegate, sharder, registry);

            Rule mine = mockTriggeredRule("MINE", RuleSeverity.RED);
            Rule notMine = mockNotTriggeredRule("NOT_MINE");
            when(delegate.getRules()).thenReturn(List.of(mine, notMine));
            when(sharder.isMine("MINE", "self")).thenReturn(true);
            when(sharder.isMine("NOT_MINE", "self")).thenReturn(false);

            List<RuleResult> results = engine.dryRun(defaultContext());

            // dryRun 仅包含 mine 规则（含未触发）
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getRuleCode()).isEqualTo("MINE");
            verify(mine).evaluate(any());
            verify(notMine, never()).evaluate(any());
        }
    }
}
