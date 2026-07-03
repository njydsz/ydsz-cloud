package com.njydsz.pmis.literule.orchestrator;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChainGraphConverter 双向转换单元测试
 *
 * <p>覆盖 {@link ChainGraphConverter} 的核心转换场景：
 * <ul>
 *   <li>THEN/WHEN/IF/SWITCH/FOR/WHILE/BREAK 链的 toGraph 转换</li>
 *   <li>toChain 还原（含空图、无 SINGLE 节点）</li>
 *   <li>指定 graphId/name 与默认 graphId 生成策略</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
class ChainGraphConverterTest {

    // ---------- 场景 1：THEN 链转换 ----------

    @Test
    @DisplayName("THEN 链转换为 graph：1 个根 CHAIN 节点 + 3 个 SINGLE 子节点 + 2 条 THEN 边")
    void thenChainToGraphShouldProduceRootAndThreeSingleNodesWithThenEdges() {
        Rule r1 = mockRule("R-001", "CPI 预警", "EVM");
        Rule r2 = mockRule("R-002", "成本超支预警", "COST");
        Rule r3 = mockRule("R-003", "进度延迟", "BENCH");
        RuleChain chain = RuleChain.then(r1, r2, r3);

        RuleChainGraph graph = ChainGraphConverter.toGraph(chain);

        assertThat(graph).isNotNull();
        assertThat(graph.getGraphId()).startsWith("graph-");
        assertThat(graph.getStatus()).isEqualTo("DRAFT");
        // 1 个根 CHAIN 节点 + 3 个 SINGLE 子节点
        assertThat(graph.getNodes()).hasSize(4);
        ChainNodeDTO root = graph.getNodes().get(0);
        assertThat(root.getNodeType()).isEqualTo("CHAIN");
        assertThat(root.getChainType()).isEqualTo("THEN");
        assertThat(root.getParentNodeId()).isNull();
        // 子节点均为 SINGLE，且 ruleCode 按传入顺序
        List<ChainNodeDTO> children = graph.getNodes().subList(1, 4);
        assertThat(children).allSatisfy(n ->
                assertThat(n.getNodeType()).isEqualTo("SINGLE"));
        assertThat(children).extracting(ChainNodeDTO::getRuleCode)
                .containsExactly("R-001", "R-002", "R-003");
        // 子节点间通过 THEN 边连接（共 2 条）
        assertThat(graph.getEdges()).hasSize(2);
        assertThat(graph.getEdges()).allSatisfy(e ->
                assertThat(e.getEdgeType()).isEqualTo(ChainEdgeDTO.EdgeType.THEN));
        // 验证连线首尾相接：children[0] → children[1] → children[2]
        assertThat(graph.getEdges().get(0).getSourceNodeId()).isEqualTo(children.get(0).getNodeId());
        assertThat(graph.getEdges().get(0).getTargetNodeId()).isEqualTo(children.get(1).getNodeId());
        assertThat(graph.getEdges().get(1).getSourceNodeId()).isEqualTo(children.get(1).getNodeId());
        assertThat(graph.getEdges().get(1).getTargetNodeId()).isEqualTo(children.get(2).getNodeId());
    }

    // ---------- 场景 2：WHEN 链转换 ----------

    @Test
    @DisplayName("WHEN 链转换为 graph：节点数正确，边类型为 THEN")
    void whenChainToGraphShouldUseThenEdgeType() {
        Rule r1 = mockRule("R-101", "并行规则A", "EVM");
        Rule r2 = mockRule("R-102", "并行规则B", "EVM");
        RuleChain chain = RuleChain.when(r1, r2);

        RuleChainGraph graph = ChainGraphConverter.toGraph(chain);

        assertThat(graph).isNotNull();
        // 1 个根 CHAIN 节点 + 2 个 SINGLE 子节点
        assertThat(graph.getNodes()).hasSize(3);
        ChainNodeDTO root = graph.getNodes().get(0);
        assertThat(root.getChainType()).isEqualTo("WHEN");
        // WHEN 链也用 THEN 边类型表示并行
        assertThat(graph.getEdges()).hasSize(1);
        assertThat(graph.getEdges().get(0).getEdgeType()).isEqualTo(ChainEdgeDTO.EdgeType.THEN);
    }

    // ---------- 场景 3：IF 链转换 ----------

    @Test
    @DisplayName("IF 链转换为 graph：IF_BRANCH 边携带 condition")
    void ifChainToGraphShouldCarryConditionOnIfBranchEdge() {
        Rule action = mockRule("R-201", "条件动作", "EVM");
        RuleChain chain = RuleChain.ifThen("amount > 1000", action);

        RuleChainGraph graph = ChainGraphConverter.toGraph(chain);

        assertThat(graph).isNotNull();
        assertThat(graph.getNodes()).hasSize(2); // 1 根 + 1 动作节点
        assertThat(graph.getNodes().get(0).getChainType()).isEqualTo("IF");
        assertThat(graph.getEdges()).hasSize(1);
        ChainEdgeDTO edge = graph.getEdges().get(0);
        assertThat(edge.getEdgeType()).isEqualTo(ChainEdgeDTO.EdgeType.IF_BRANCH);
        assertThat(edge.getCondition()).isEqualTo("amount > 1000");
        assertThat(edge.getLabel()).isEqualTo("amount > 1000");
        // IF_BRANCH 边起点是根节点
        assertThat(edge.getSourceNodeId()).isEqualTo("node-1");
        assertThat(edge.getTargetNodeId()).isEqualTo("node-2");
    }

    // ---------- 场景 4：SWITCH 链转换 ----------

    @Test
    @DisplayName("SWITCH 链转换为 graph：2 条 SWITCH_BRANCH 边，branchValue 与分支 key 一致")
    void switchChainToGraphShouldProduceSwitchBranchEdgesWithBranchValue() {
        Map<String, Rule> branches = new LinkedHashMap<>();
        branches.put("A", mockRule("R-301", "分支A", "EVM"));
        branches.put("B", mockRule("R-302", "分支B", "EVM"));
        RuleChain chain = RuleChain.switchOn("type", branches);

        RuleChainGraph graph = ChainGraphConverter.toGraph(chain);

        assertThat(graph).isNotNull();
        // 1 根 + 2 分支
        assertThat(graph.getNodes()).hasSize(3);
        assertThat(graph.getNodes().get(0).getChainType()).isEqualTo("SWITCH");
        assertThat(graph.getEdges()).hasSize(2);
        assertThat(graph.getEdges()).allSatisfy(e ->
                assertThat(e.getEdgeType()).isEqualTo(ChainEdgeDTO.EdgeType.SWITCH_BRANCH));
        // branchValue 与分支 key 一致
        assertThat(graph.getEdges()).extracting(ChainEdgeDTO::getBranchValue)
                .containsExactlyInAnyOrder("A", "B");
    }

    // ---------- 场景 5：FOR 链转换 ----------

    @Test
    @DisplayName("FOR 链转换为 graph：FOR_ITER 边 + metadata 含 iterableExpression/iterationVar")
    void forChainToGraphShouldCarryIterMetadataOnForIterEdge() {
        Rule action = mockRule("R-401", "循环动作", "EVM");
        RuleChain chain = RuleChain.forEach("items", "item", action);

        RuleChainGraph graph = ChainGraphConverter.toGraph(chain);

        assertThat(graph).isNotNull();
        assertThat(graph.getNodes()).hasSize(2); // 1 根 + 1 循环体
        assertThat(graph.getNodes().get(0).getChainType()).isEqualTo("FOR");
        ChainNodeDTO bodyNode = graph.getNodes().get(1);
        assertThat(bodyNode.getMetadata())
                .containsEntry("iterableExpression", "items")
                .containsEntry("iterationVar", "item");
        assertThat(graph.getEdges()).hasSize(1);
        assertThat(graph.getEdges().get(0).getEdgeType()).isEqualTo(ChainEdgeDTO.EdgeType.FOR_ITER);
    }

    // ---------- 场景 6：WHILE 链转换 ----------

    @Test
    @DisplayName("WHILE 链转换为 graph：WHILE_ITER 边携带 condition")
    void whileChainToGraphShouldCarryConditionOnWhileIterEdge() {
        Rule action = mockRule("R-501", "循环动作", "EVM");
        RuleChain chain = RuleChain.whileDo("count < 10", action);

        RuleChainGraph graph = ChainGraphConverter.toGraph(chain);

        assertThat(graph).isNotNull();
        assertThat(graph.getNodes()).hasSize(2);
        assertThat(graph.getNodes().get(0).getChainType()).isEqualTo("WHILE");
        assertThat(graph.getEdges()).hasSize(1);
        ChainEdgeDTO edge = graph.getEdges().get(0);
        assertThat(edge.getEdgeType()).isEqualTo(ChainEdgeDTO.EdgeType.WHILE_ITER);
        assertThat(edge.getCondition()).isEqualTo("count < 10");
        assertThat(edge.getLabel()).isEqualTo("count < 10");
    }

    // ---------- 场景 7：BREAK 链转换 ----------

    @Test
    @DisplayName("BREAK 链转换为 graph：仅 1 个根节点，无子节点")
    void breakChainToGraphShouldProduceOnlyRootNode() {
        RuleChain chain = RuleChain.breakChain();

        RuleChainGraph graph = ChainGraphConverter.toGraph(chain);

        assertThat(graph).isNotNull();
        assertThat(graph.getNodes()).hasSize(1);
        ChainNodeDTO root = graph.getNodes().get(0);
        assertThat(root.getNodeType()).isEqualTo("CHAIN");
        assertThat(root.getChainType()).isEqualTo("BREAK");
        assertThat(graph.getEdges()).isEmpty();
    }

    // ---------- 场景 8：toChain 还原 ----------

    @Test
    @DisplayName("toChain 还原：含 3 个 SINGLE 节点的 graph 还原为 THEN 链")
    void toChainShouldRestoreThenChainFromSingleNodes() {
        ChainNodeDTO n1 = ChainNodeDTO.builder()
                .nodeId("n-1").nodeType("SINGLE").ruleCode("R-001").build();
        ChainNodeDTO n2 = ChainNodeDTO.builder()
                .nodeId("n-2").nodeType("SINGLE").ruleCode("R-002").build();
        ChainNodeDTO n3 = ChainNodeDTO.builder()
                .nodeId("n-3").nodeType("SINGLE").ruleCode("R-003").build();
        RuleChainGraph graph = RuleChainGraph.builder()
                .graphId("g-1")
                .nodes(List.of(n1, n2, n3))
                .build();

        Rule r1 = mockRule("R-001", "规则1", "EVM");
        Rule r2 = mockRule("R-002", "规则2", "EVM");
        Rule r3 = mockRule("R-003", "规则3", "EVM");
        ChainGraphConverter.RuleResolver resolver = code -> switch (code) {
            case "R-001" -> r1;
            case "R-002" -> r2;
            case "R-003" -> r3;
            default -> null;
        };

        RuleChain chain = ChainGraphConverter.toChain(graph, resolver);

        assertThat(chain).isNotNull();
        assertThat(chain.getChainType()).isEqualTo(RuleChainType.THEN);
        assertThat(chain.getNodes()).hasSize(3);
        assertThat(chain.getNodes()).extracting(n -> n.getRule().getCode())
                .containsExactly("R-001", "R-002", "R-003");
    }

    // ---------- 场景 9：toChain 空图 ----------

    @Test
    @DisplayName("toChain 空图：传入空 nodes 的 graph 返回 null")
    void toChainWithEmptyGraphShouldReturnNull() {
        RuleChainGraph graph = RuleChainGraph.builder()
                .graphId("g-empty")
                .nodes(new ArrayList<>())
                .build();

        RuleChain chain = ChainGraphConverter.toChain(graph, code -> null);

        assertThat(chain).isNull();
    }

    // ---------- 场景 10：toChain 无 SINGLE 节点 ----------

    @Test
    @DisplayName("toChain 无 SINGLE 节点：全是 CHAIN 节点的 graph 返回 null")
    void toChainWithNoSingleNodesShouldReturnNull() {
        ChainNodeDTO n1 = ChainNodeDTO.builder()
                .nodeId("n-1").nodeType("CHAIN").chainType("THEN").build();
        ChainNodeDTO n2 = ChainNodeDTO.builder()
                .nodeId("n-2").nodeType("CHAIN").chainType("IF").build();
        RuleChainGraph graph = RuleChainGraph.builder()
                .graphId("g-no-single")
                .nodes(List.of(n1, n2))
                .build();

        RuleChain chain = ChainGraphConverter.toChain(graph, code -> null);

        assertThat(chain).isNull();
    }

    // ---------- 场景 11：指定 graphId/name ----------

    @Test
    @DisplayName("toGraph(chain, graphId, graphName)：自定义 ID 与名称生效")
    void toGraphWithCustomIdAndNameShouldApply() {
        Rule r = mockRule("R-601", "测试规则", "EVM");
        RuleChain chain = RuleChain.then(r);

        RuleChainGraph graph = ChainGraphConverter.toGraph(chain, "test-id", "test-name");

        assertThat(graph.getGraphId()).isEqualTo("test-id");
        assertThat(graph.getName()).isEqualTo("test-name");
        // 自定义 name 时根节点 label 应使用自定义 name
        assertThat(graph.getNodes().get(0).getLabel()).isEqualTo("test-name");
    }

    // ---------- 场景 12：BREAK 链 + 默认 graphId ----------

    @Test
    @DisplayName("toGraph(breakChain)：默认 graphId 以 'graph-' 开头")
    void toGraphWithBreakChainShouldGenerateDefaultGraphId() {
        RuleChain chain = RuleChain.breakChain();

        RuleChainGraph graph = ChainGraphConverter.toGraph(chain);

        assertThat(graph.getGraphId()).startsWith("graph-");
        assertThat(graph.getNodes()).hasSize(1);
        assertThat(graph.getNodes().get(0).getChainType()).isEqualTo("BREAK");
    }

    // ---------- 辅助方法 ----------

    /**
     * 构造匿名 Rule 测试桩
     *
     * @param code     规则编码
     * @param name     规则名称
     * @param category 规则类别
     * @return Rule 实例（evaluate 返回未触发结果）
     */
    private Rule mockRule(String code, String name, String category) {
        return new Rule() {
            @Override
            public String getCode() { return code; }
            @Override
            public String getName() { return name; }
            @Override
            public String getCategory() { return category; }
            @Override
            public RuleResult evaluate(RuleContext context) { return RuleResult.notTriggered(code); }
        };
    }
}
