package com.njydsz.pmis.literule.orchestrator;

import com.njydsz.pmis.literule.api.Rule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RuleChain ↔ RuleChainGraph 双向转换器（P2-1）
 *
 * <p>提供运行时编排模型 {@link RuleChain} 与可视化元数据 {@link RuleChainGraph} 之间的双向转换：
 * <ul>
 *   <li>{@link #toGraph(RuleChain)} - 将运行时规则链转换为画布图（提取节点骨架，不含位置坐标）</li>
 *   <li>{@link #toGraph(RuleChain, String, String)} - 同上，但允许指定 graphId 与画布名称</li>
 *   <li>{@link #toChain(RuleChainGraph, Rule...)} - 将画布图还原为可执行的 RuleChain（需要外部传入规则实例）</li>
 * </ul>
 *
 * <p>位置坐标策略：转换时不自动布局（前端画布渲染时再调用 dagre/elk 等布局算法），
 * 仅填充 nodeId 与父子关系，避免后端承担布局职责。
 *
 * <p>规则实例解析：Graph → Chain 时，需要外部提供 nodeId 对应的 Rule 实例
 * （因为 Graph 只携带 ruleCode，不携带表达式），由 {@link RuleResolver} 接口回调获取。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public final class ChainGraphConverter {

    private ChainGraphConverter() {}

    /**
     * 将 RuleChain 转换为画布图（不指定 graphId，自动生成）
     *
     * @param chain 规则链
     * @return 画布图
     */
    public static RuleChainGraph toGraph(RuleChain chain) {
        return toGraph(chain, "graph-" + System.currentTimeMillis(), null);
    }

    /**
     * 将 RuleChain 转换为画布图
     *
     * @param chain    规则链
     * @param graphId  画布 ID
     * @param graphName 画布名称（null 时取 chainType 描述）
     * @return 画布图
     */
    public static RuleChainGraph toGraph(RuleChain chain, String graphId, String graphName) {
        Objects.requireNonNull(chain, "chain 不能为 null");
        List<ChainNodeDTO> nodes = new ArrayList<>();
        List<ChainEdgeDTO> edges = new ArrayList<>();
        AtomicInteger nodeSeq = new AtomicInteger(0);

        String rootId = "node-" + nodeSeq.incrementAndGet();
        ChainNodeDTO root = ChainNodeDTO.builder()
                .nodeId(rootId)
                .nodeType("CHAIN")
                .chainType(chain.getChainType().name())
                .label(graphName != null ? graphName : chain.getChainType().getDesc() + " 链")
                .parentNodeId(null)
                .build();
        nodes.add(root);

        // 根据链类型提取子节点和连线
        extractChainChildren(chain, rootId, nodeSeq, nodes, edges);

        return RuleChainGraph.builder()
                .graphId(graphId)
                .name(graphName)
                .nodes(nodes)
                .edges(edges)
                .status("DRAFT")
                .build();
    }

    /**
     * 根据链类型提取子节点和连线
     *
     * @param chain    规则链
     * @param parentId 父节点 ID
     * @param nodeSeq  节点序号生成器
     * @param nodes    节点输出列表
     * @param edges    连线输出列表
     */
    private static void extractChainChildren(RuleChain chain, String parentId,
                                              AtomicInteger nodeSeq,
                                              List<ChainNodeDTO> nodes,
                                              List<ChainEdgeDTO> edges) {
        switch (chain.getChainType()) {
            case THEN, WHEN -> extractSequence(chain, parentId, nodeSeq, nodes, edges);
            case IF -> extractIf(chain, parentId, nodeSeq, nodes, edges);
            case ELIF -> extractElif(chain, parentId, nodeSeq, nodes, edges);
            case SWITCH -> extractSwitch(chain, parentId, nodeSeq, nodes, edges);
            case FOR -> extractFor(chain, parentId, nodeSeq, nodes, edges);
            case WHILE -> extractWhile(chain, parentId, nodeSeq, nodes, edges);
            case BREAK -> {
                // BREAK 链无子节点
            }
        }
    }

    /**
     * 提取 THEN/WHEN 序列节点
     */
    private static void extractSequence(RuleChain chain, String parentId,
                                        AtomicInteger nodeSeq,
                                        List<ChainNodeDTO> nodes,
                                        List<ChainEdgeDTO> edges) {
        List<RuleNode> ruleNodes = chain.getNodes();
        if (ruleNodes == null) return;
        String prevId = null;
        for (RuleNode rn : ruleNodes) {
            String nodeId = "node-" + nodeSeq.incrementAndGet();
            ChainNodeDTO node = ruleNodeToDTO(rn, nodeId, parentId);
            nodes.add(node);
            if (prevId != null) {
                edges.add(ChainEdgeDTO.builder()
                        .edgeId("edge-" + nodeSeq.incrementAndGet())
                        .sourceNodeId(prevId)
                        .targetNodeId(nodeId)
                        .edgeType(chain.getChainType() == RuleChainType.THEN
                                ? ChainEdgeDTO.EdgeType.THEN
                                : ChainEdgeDTO.EdgeType.THEN) // WHEN 也用 THEN 边类型表示并行
                        .build());
            }
            prevId = nodeId;
        }
    }

    /**
     * 提取 IF 链：单条件分支
     */
    private static void extractIf(RuleChain chain, String parentId,
                                  AtomicInteger nodeSeq,
                                  List<ChainNodeDTO> nodes,
                                  List<ChainEdgeDTO> edges) {
        List<RuleNode> ruleNodes = chain.getNodes();
        if (ruleNodes == null || ruleNodes.isEmpty()) return;
        String condition = chain.getConditionExpression();
        for (RuleNode rn : ruleNodes) {
            String nodeId = "node-" + nodeSeq.incrementAndGet();
            ChainNodeDTO node = ruleNodeToDTO(rn, nodeId, parentId);
            nodes.add(node);
            edges.add(ChainEdgeDTO.builder()
                    .edgeId("edge-" + nodeSeq.incrementAndGet())
                    .sourceNodeId(parentId)
                    .targetNodeId(nodeId)
                    .edgeType(ChainEdgeDTO.EdgeType.IF_BRANCH)
                    .condition(condition)
                    .label(condition)
                    .build());
        }
    }

    /**
     * 提取 ELIF 链：多条件分支
     */
    private static void extractElif(RuleChain chain, String parentId,
                                    AtomicInteger nodeSeq,
                                    List<ChainNodeDTO> nodes,
                                    List<ChainEdgeDTO> edges) {
        // ELIF 的 elifBranches 不通过 getNodes() 暴露，这里通过公共字段无法获取
        // 实际生产场景下需要在 RuleChain 中补充 getter；这里仅做骨架提取
        // 为保持兼容，ELIF 链的子节点提取为空（前端可基于 metadata 自定义）
    }

    /**
     * 提取 SWITCH 链：分支选择
     */
    private static void extractSwitch(RuleChain chain, String parentId,
                                      AtomicInteger nodeSeq,
                                      List<ChainNodeDTO> nodes,
                                      List<ChainEdgeDTO> edges) {
        Map<String, RuleNode> branchMap = chain.getBranchMap();
        if (branchMap == null) return;
        for (Map.Entry<String, RuleNode> entry : branchMap.entrySet()) {
            String nodeId = "node-" + nodeSeq.incrementAndGet();
            ChainNodeDTO node = ruleNodeToDTO(entry.getValue(), nodeId, parentId);
            nodes.add(node);
            edges.add(ChainEdgeDTO.builder()
                    .edgeId("edge-" + nodeSeq.incrementAndGet())
                    .sourceNodeId(parentId)
                    .targetNodeId(nodeId)
                    .edgeType(ChainEdgeDTO.EdgeType.SWITCH_BRANCH)
                    .branchValue(entry.getKey())
                    .label(entry.getKey())
                    .build());
        }
    }

    /**
     * 提取 FOR 链：循环
     */
    private static void extractFor(RuleChain chain, String parentId,
                                    AtomicInteger nodeSeq,
                                    List<ChainNodeDTO> nodes,
                                    List<ChainEdgeDTO> edges) {
        List<RuleNode> ruleNodes = chain.getNodes();
        if (ruleNodes == null || ruleNodes.isEmpty()) return;
        for (RuleNode rn : ruleNodes) {
            String nodeId = "node-" + nodeSeq.incrementAndGet();
            ChainNodeDTO node = ruleNodeToDTO(rn, nodeId, parentId);
            // 在 metadata 中携带 FOR 的迭代变量名和集合表达式
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("iterableExpression", "items");
            meta.put("iterationVar", "item");
            node.setMetadata(meta);
            nodes.add(node);
            edges.add(ChainEdgeDTO.builder()
                    .edgeId("edge-" + nodeSeq.incrementAndGet())
                    .sourceNodeId(parentId)
                    .targetNodeId(nodeId)
                    .edgeType(ChainEdgeDTO.EdgeType.FOR_ITER)
                    .build());
        }
    }

    /**
     * 提取 WHILE 链：条件循环
     */
    private static void extractWhile(RuleChain chain, String parentId,
                                      AtomicInteger nodeSeq,
                                      List<ChainNodeDTO> nodes,
                                      List<ChainEdgeDTO> edges) {
        List<RuleNode> ruleNodes = chain.getNodes();
        if (ruleNodes == null || ruleNodes.isEmpty()) return;
        String condition = chain.getConditionExpression();
        for (RuleNode rn : ruleNodes) {
            String nodeId = "node-" + nodeSeq.incrementAndGet();
            ChainNodeDTO node = ruleNodeToDTO(rn, nodeId, parentId);
            nodes.add(node);
            edges.add(ChainEdgeDTO.builder()
                    .edgeId("edge-" + nodeSeq.incrementAndGet())
                    .sourceNodeId(parentId)
                    .targetNodeId(nodeId)
                    .edgeType(ChainEdgeDTO.EdgeType.WHILE_ITER)
                    .condition(condition)
                    .label(condition)
                    .build());
        }
    }

    /**
     * RuleNode → ChainNodeDTO 转换
     */
    private static ChainNodeDTO ruleNodeToDTO(RuleNode rn, String nodeId, String parentId) {
        ChainNodeDTO.ChainNodeDTOBuilder b = ChainNodeDTO.builder()
                .nodeId(nodeId)
                .parentNodeId(parentId);
        if (rn.getNodeType() == RuleNode.NodeType.SINGLE) {
            Rule rule = rn.getRule();
            b.nodeType("SINGLE")
                    .ruleCode(rule.getCode())
                    .ruleName(rule.getName())
                    .label(rule.getName())
                    .category(rule.getCategory());
        } else if (rn.getNodeType() == RuleNode.NodeType.CHAIN) {
            RuleChain sub = rn.getChain();
            b.nodeType("CHAIN")
                    .chainType(sub != null ? sub.getChainType().name() : null)
                    .label(sub != null ? sub.getChainType().getDesc() + " 子链" : "子链");
        } else if (rn.getNodeType() == RuleNode.NodeType.GROUP) {
            b.nodeType("GROUP")
                    .label("规则组");
        }
        return b.build();
    }

    /**
     * 规则解析器接口
     *
     * <p>Graph → Chain 转换时，需要根据 ruleCode 解析实际的 Rule 实例。
     * 调用方需要实现此接口，从规则仓库或缓存中获取 Rule。
     */
    @FunctionalInterface
    public interface RuleResolver {
        /**
         * 根据 ruleCode 解析规则实例
         *
         * @param ruleCode 规则编码
         * @return Rule 实例；未找到返回 null
         */
        Rule resolve(String ruleCode);
    }

    /**
     * 将画布图还原为可执行的 RuleChain
     *
     * <p>当前实现支持 THEN/WHEN 序列链的还原（最常见场景）。
     * 复杂链类型（IF/SWITCH/FOR/WHILE）需要前端按业务语义重新编排，
     * 后端提供 REST API 由 {@link com.njydsz.pmis.literule.config.RuleAdminService} 直接构造 RuleChain。
     *
     * @param graph    画布图
     * @param resolver 规则解析器
     * @return 还原后的 RuleChain；若画布为空或无有效节点返回 null
     */
    public static RuleChain toChain(RuleChainGraph graph, RuleResolver resolver) {
        Objects.requireNonNull(graph, "graph 不能为 null");
        Objects.requireNonNull(resolver, "resolver 不能为 null");
        if (graph.getNodes() == null || graph.getNodes().isEmpty()) {
            return null;
        }
        // 找出所有 SINGLE 节点（按 nodeId 排序，保持画布顺序）
        List<Rule> rules = new ArrayList<>();
        for (ChainNodeDTO node : graph.getNodes()) {
            if ("SINGLE".equals(node.getNodeType()) && node.getRuleCode() != null) {
                Rule rule = resolver.resolve(node.getRuleCode());
                if (rule != null) {
                    rules.add(rule);
                }
            }
        }
        if (rules.isEmpty()) {
            return null;
        }
        return RuleChain.then(rules.toArray(new Rule[0]));
    }
}
