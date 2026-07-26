package com.njydsz.literule.server.orchestrator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import com.njydsz.literule.api.Rule;

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
 * @since 1.0.0
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
            case CATCH, RETRY -> extractCatchOrRetry(chain, parentId, nodeSeq, nodes, edges);
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
                                : ChainEdgeDTO.EdgeType.WHEN) // WHEN 并行流使用独立边类型
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
        // 在根节点的 metadata 中携带条件表达式，便于反向解析
        ChainNodeDTO rootNode = nodes.stream()
                .filter(n -> parentId.equals(n.getNodeId()))
                .findFirst().orElse(null);
        if (rootNode != null && condition != null) {
            Map<String, Object> meta = rootNode.getMetadata() != null
                    ? new LinkedHashMap<>(rootNode.getMetadata()) : new LinkedHashMap<>();
            meta.put("condition", condition);
            rootNode.setMetadata(meta);
        }
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
     *
     * <p>P0-1 增强：通过 {@link RuleChain#getElifBranches()} 访问多分支条件列表，
     * 抽取每个条件分支为画布节点，并通过 {@link ChainEdgeDTO.EdgeType#ELIF_BRANCH}
     * 边连接，condition 字段携带条件表达式。
     */
    private static void extractElif(RuleChain chain, String parentId,
                                    AtomicInteger nodeSeq,
                                    List<ChainNodeDTO> nodes,
                                    List<ChainEdgeDTO> edges) {
        List<Map.Entry<String, RuleNode>> branches = chain.getElifBranches();
        if (branches != null) {
            for (Map.Entry<String, RuleNode> branch : branches) {
                String nodeId = "node-" + nodeSeq.incrementAndGet();
                ChainNodeDTO node = ruleNodeToDTO(branch.getValue(), nodeId, parentId);
                nodes.add(node);
                edges.add(ChainEdgeDTO.builder()
                        .edgeId("edge-" + nodeSeq.incrementAndGet())
                        .sourceNodeId(parentId)
                        .targetNodeId(nodeId)
                        .edgeType(ChainEdgeDTO.EdgeType.ELIF_BRANCH)
                        .condition(branch.getKey())
                        .label(branch.getKey())
                        .build());
            }
        }
        // 提取 ELSE 兜底分支
        RuleNode elseNode = chain.getElseNode();
        if (elseNode != null) {
            String nodeId = "node-" + nodeSeq.incrementAndGet();
            ChainNodeDTO node = ruleNodeToDTO(elseNode, nodeId, parentId);
            // ELSE 节点用更显眼的 label
            if (node.getLabel() == null || node.getLabel().equals("规则组")) {
                node.setLabel("ELSE 兜底");
            }
            nodes.add(node);
            edges.add(ChainEdgeDTO.builder()
                    .edgeId("edge-" + nodeSeq.incrementAndGet())
                    .sourceNodeId(parentId)
                    .targetNodeId(nodeId)
                    .edgeType(ChainEdgeDTO.EdgeType.DEFAULT_BRANCH)
                    .label("ELSE")
                    .build());
        }
    }

    /**
     * 提取 SWITCH 链：分支选择
     *
     * <p>P0-1 增强：同时提取 defaultBranch 作为 DEFAULT_BRANCH 兜底分支。
     */
    private static void extractSwitch(RuleChain chain, String parentId,
                                      AtomicInteger nodeSeq,
                                      List<ChainNodeDTO> nodes,
                                      List<ChainEdgeDTO> edges) {
        // 在根节点的 metadata 中携带 branchKey，便于反向解析
        String branchKey = chain.getBranchKey();
        ChainNodeDTO rootNode = nodes.stream()
                .filter(n -> parentId.equals(n.getNodeId()))
                .findFirst().orElse(null);
        if (rootNode != null && branchKey != null) {
            Map<String, Object> meta = rootNode.getMetadata() != null
                    ? new LinkedHashMap<>(rootNode.getMetadata()) : new LinkedHashMap<>();
            meta.put("branchKey", branchKey);
            rootNode.setMetadata(meta);
        }
        Map<String, RuleNode> branchMap = chain.getBranchMap();
        if (branchMap != null) {
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
        // 提取默认分支
        RuleNode defaultNode = chain.getDefaultBranch();
        if (defaultNode != null) {
            String nodeId = "node-" + nodeSeq.incrementAndGet();
            ChainNodeDTO node = ruleNodeToDTO(defaultNode, nodeId, parentId);
            if (node.getLabel() == null || node.getLabel().equals("规则组")) {
                node.setLabel("DEFAULT 兜底");
            }
            nodes.add(node);
            edges.add(ChainEdgeDTO.builder()
                    .edgeId("edge-" + nodeSeq.incrementAndGet())
                    .sourceNodeId(parentId)
                    .targetNodeId(nodeId)
                    .edgeType(ChainEdgeDTO.EdgeType.DEFAULT_BRANCH)
                    .label("DEFAULT")
                    .build());
        }
    }

    /**
     * 提取 FOR 链：循环
     *
     * <p>P0-1 增强：使用真实 {@code iterableExpression} 与 {@code iterationVar} 填充 metadata。
     */
    private static void extractFor(RuleChain chain, String parentId,
                                    AtomicInteger nodeSeq,
                                    List<ChainNodeDTO> nodes,
                                    List<ChainEdgeDTO> edges) {
        List<RuleNode> ruleNodes = chain.getNodes();
        if (ruleNodes == null || ruleNodes.isEmpty()) return;
        String iterable = chain.getIterableExpression();
        String iterVar = chain.getIterationVar();
        for (RuleNode rn : ruleNodes) {
            String nodeId = "node-" + nodeSeq.incrementAndGet();
            ChainNodeDTO node = ruleNodeToDTO(rn, nodeId, parentId);
            Map<String, Object> meta = new LinkedHashMap<>();
            if (iterable != null) {
                meta.put("iterableExpression", iterable);
            }
            if (iterVar != null) {
                meta.put("iterationVar", iterVar);
            }
            if (!meta.isEmpty()) {
                node.setMetadata(meta);
            }
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
     *
     * <p>P0-1 增强：在 metadata 中携带 maxIterations，便于前端配置面板展示。
     */
    private static void extractWhile(RuleChain chain, String parentId,
                                      AtomicInteger nodeSeq,
                                      List<ChainNodeDTO> nodes,
                                      List<ChainEdgeDTO> edges) {
        List<RuleNode> ruleNodes = chain.getNodes();
        if (ruleNodes == null || ruleNodes.isEmpty()) return;
        String condition = chain.getConditionExpression();
        int maxIter = chain.getMaxIterations();
        for (RuleNode rn : ruleNodes) {
            String nodeId = "node-" + nodeSeq.incrementAndGet();
            ChainNodeDTO node = ruleNodeToDTO(rn, nodeId, parentId);
            if (maxIter > 0) {
                Map<String, Object> meta = node.getMetadata() != null
                        ? new LinkedHashMap<>(node.getMetadata())
                        : new LinkedHashMap<>();
                meta.put("maxIterations", maxIter);
                node.setMetadata(meta);
            }
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
     * 提取 CATCH/RETRY 链：主节点 + 补偿/回滚节点（2.0.0）
     *
     * <p>CATCH 语义：执行主节点，异常时执行补偿节点。
     * RETRY 语义：执行主节点失败时自动重试，重试耗尽后执行回滚补偿节点。
     *
     * <p>提取两个节点：
     * <ul>
     *   <li>主节点（primaryNode）- 使用 PRIMARY 边类型连接</li>
     *   <li>补偿/回滚节点（catchNode）- 使用 CATCH_COMPENSATION / RETRY_ROLLBACK 边类型连接</li>
     * </ul>
     * RETRY 链会在主节点 metadata 中携带 maxRetries 与 retryIntervalMs，便于前端配置面板展示。
     */
    private static void extractCatchOrRetry(RuleChain chain, String parentId,
                                              AtomicInteger nodeSeq,
                                              List<ChainNodeDTO> nodes,
                                              List<ChainEdgeDTO> edges) {
        RuleNode primaryNode = chain.getPrimaryNode();
        RuleNode catchNode = chain.getCatchNode();
        boolean isRetry = chain.getChainType() == RuleChainType.RETRY;

        // 提取主节点
        if (primaryNode != null) {
            String nodeId = "node-" + nodeSeq.incrementAndGet();
            ChainNodeDTO node = ruleNodeToDTO(primaryNode, nodeId, parentId);
            Map<String, Object> meta = node.getMetadata() != null
                    ? new LinkedHashMap<>(node.getMetadata())
                    : new LinkedHashMap<>();
            meta.put("primary", true);
            if (isRetry) {
                int maxRetries = chain.getMaxRetries();
                long retryIntervalMs = chain.getRetryIntervalMs();
                if (maxRetries > 0) {
                    meta.put("maxRetries", maxRetries);
                }
                if (retryIntervalMs > 0) {
                    meta.put("retryIntervalMs", retryIntervalMs);
                }
            }
            node.setMetadata(meta);
            if (node.getLabel() == null) {
                node.setLabel(isRetry ? "RETRY 主节点" : "CATCH 主节点");
            }
            nodes.add(node);
            edges.add(ChainEdgeDTO.builder()
                    .edgeId("edge-" + nodeSeq.incrementAndGet())
                    .sourceNodeId(parentId)
                    .targetNodeId(nodeId)
                    .edgeType(ChainEdgeDTO.EdgeType.PRIMARY)
                    .label("primary")
                    .build());
        }

        // 提取补偿/回滚节点
        if (catchNode != null) {
            String nodeId = "node-" + nodeSeq.incrementAndGet();
            ChainNodeDTO node = ruleNodeToDTO(catchNode, nodeId, parentId);
            Map<String, Object> meta = node.getMetadata() != null
                    ? new LinkedHashMap<>(node.getMetadata())
                    : new LinkedHashMap<>();
            meta.put(isRetry ? "rollback" : "compensation", true);
            node.setMetadata(meta);
            if (node.getLabel() == null) {
                node.setLabel(isRetry ? "RETRY 回滚节点" : "CATCH 补偿节点");
            }
            nodes.add(node);
            edges.add(ChainEdgeDTO.builder()
                    .edgeId("edge-" + nodeSeq.incrementAndGet())
                    .sourceNodeId(parentId)
                    .targetNodeId(nodeId)
                    .edgeType(isRetry
                            ? ChainEdgeDTO.EdgeType.RETRY_ROLLBACK
                            : ChainEdgeDTO.EdgeType.CATCH_COMPENSATION)
                    .label(isRetry ? "rollback" : "compensation")
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
     * <p>P0-1 增强：支持全部 8 种链类型（THEN/WHEN/IF/ELIF/SWITCH/FOR/WHILE/BREAK）。
     * 复杂链（IF/ELIF/SWITCH/FOR/WHILE/BREAK）的条件/分支/迭代元数据从画布节点的 metadata 还原。
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

        // 找出根节点（CHAIN 类型的根节点）
        ChainNodeDTO root = null;
        for (ChainNodeDTO n : graph.getNodes()) {
            if ("CHAIN".equals(n.getNodeType()) && n.getParentNodeId() == null) {
                root = n;
                break;
            }
        }
        if (root == null) {
            // 兜底 1：取第一个节点作为根
            root = graph.getNodes().get(0);
        }

        // 兜底 2：若兜底出来的根是 SINGLE 节点（说明整个 graph 没有任何 CHAIN 根节点），
        // 则把所有 SINGLE 节点按 nodes 列表顺序组成 THEN 链，
        // 对应 toGraph("扁平化 THEN 链") 的反向还原。
        if ("SINGLE".equals(root.getNodeType())) {
            List<ChainNodeDTO> allSingles = new ArrayList<>();
            for (ChainNodeDTO n : graph.getNodes()) {
                if ("SINGLE".equals(n.getNodeType())) {
                    allSingles.add(n);
                }
            }
            return buildSequenceChain(allSingles, graph, resolver, false);
        }

        String chainType = root.getChainType() != null ? root.getChainType() : "THEN";
        List<ChainNodeDTO> children = findChildren(graph, root.getNodeId());

        switch (chainType) {
            case "THEN":
                return buildSequenceChain(children, graph, resolver, false);
            case "WHEN":
                return buildSequenceChain(children, graph, resolver, true);
            case "IF": {
                // 从边的 condition 字段精确还原 IF 条件表达式（不再依赖 label 启发式解析）
                ChainEdgeDTO firstEdge = findFirstEdge(graph, root.getNodeId());
                String condition = "true";
                if (firstEdge != null && firstEdge.getCondition() != null) {
                    condition = firstEdge.getCondition();
                } else if (root.getMetadata() != null && root.getMetadata().get("condition") != null) {
                    // 优先从 metadata 获取条件表达式（最可靠）
                    condition = String.valueOf(root.getMetadata().get("condition"));
                }
                Rule action = firstChildRule(children, graph, resolver);
                return action != null ? RuleChain.ifThen(condition, action) : null;
            }
            case "ELIF": {
                Map<String, Rule> branchMap = new LinkedHashMap<>();
                Rule elseRule = null;
                for (ChainEdgeDTO edge : graph.getEdges()) {
                    if (!root.getNodeId().equals(edge.getSourceNodeId())) continue;
                    ChainNodeDTO target = findNode(graph, edge.getTargetNodeId());
                    if (target == null) continue;
                    Rule r = resolveNode(target, resolver);
                    if (r == null) continue;
                    if (ChainEdgeDTO.EdgeType.DEFAULT_BRANCH.equals(edge.getEdgeType())) {
                        elseRule = r;
                    } else if (edge.getCondition() != null) {
                        branchMap.put(edge.getCondition(), r);
                    }
                }
                return RuleChain.elif(branchMap, elseRule);
            }
            case "SWITCH": {
                // 优先从 metadata 获取 branchKey（最可靠），其次从 label 启发式解析
                String branchKey = "type";
                if (root.getMetadata() != null && root.getMetadata().get("branchKey") != null) {
                    branchKey = String.valueOf(root.getMetadata().get("branchKey"));
                } else if (root.getLabel() != null && root.getLabel().contains("=")) {
                    branchKey = root.getLabel().split("=")[0].trim();
                }
                Map<String, Rule> branchMap = new LinkedHashMap<>();
                Rule defaultRule = null;
                for (ChainEdgeDTO edge : graph.getEdges()) {
                    if (!root.getNodeId().equals(edge.getSourceNodeId())) continue;
                    ChainNodeDTO target = findNode(graph, edge.getTargetNodeId());
                    if (target == null) continue;
                    Rule r = resolveNode(target, resolver);
                    if (r == null) continue;
                    if (ChainEdgeDTO.EdgeType.DEFAULT_BRANCH.equals(edge.getEdgeType())) {
                        defaultRule = r;
                    } else if (edge.getBranchValue() != null) {
                        branchMap.put(edge.getBranchValue(), r);
                    }
                }
                return RuleChain.switchOn(branchKey, branchMap, defaultRule);
            }
            case "FOR": {
                String iterable = "items";
                String iterVar = "item";
                Map<String, Object> meta = root.getMetadata();
                if (meta != null) {
                    if (meta.get("iterableExpression") != null) {
                        iterable = String.valueOf(meta.get("iterableExpression"));
                    }
                    if (meta.get("iterationVar") != null) {
                        iterVar = String.valueOf(meta.get("iterationVar"));
                    }
                }
                Rule action = firstChildRule(children, graph, resolver);
                return action != null ? RuleChain.forEach(iterable, iterVar, action) : null;
            }
            case "WHILE": {
                String condition = "true";
                ChainEdgeDTO firstEdge = findFirstEdge(graph, root.getNodeId());
                if (firstEdge != null && firstEdge.getCondition() != null) {
                    condition = firstEdge.getCondition();
                }
                int maxIter = 100;
                Map<String, Object> meta = root.getMetadata();
                if (meta != null && meta.get("maxIterations") instanceof Number n) {
                    maxIter = n.intValue();
                }
                Rule action = firstChildRule(children, graph, resolver);
                return action != null ? RuleChain.whileDo(condition, action, maxIter) : null;
            }
            case "BREAK":
                return RuleChain.breakChain();
            default:
                return buildSequenceChain(children, graph, resolver, false);
        }
    }

    /**
     * 构建顺序链（THEN / WHEN）
     */
    private static RuleChain buildSequenceChain(List<ChainNodeDTO> children,
                                                RuleChainGraph graph,
                                                RuleResolver resolver,
                                                boolean parallel) {
        List<Rule> rules = new ArrayList<>();
        for (ChainNodeDTO c : children) {
            Rule r = resolveNode(c, resolver);
            if (r != null) {
                rules.add(r);
            }
        }
        if (rules.isEmpty()) {
            return null;
        }
        return parallel
                ? RuleChain.when(rules.toArray(new Rule[0]))
                : RuleChain.then(rules.toArray(new Rule[0]));
    }

    /**
     * 解析单个节点为 Rule
     *
     * <p>P1-7 增强：支持 SINGLE 和 CHAIN 嵌套节点的递归解析：
     * <ul>
     *   <li>SINGLE - 通过 ruleCode 回调 resolver 获取规则实例</li>
     *   <li>CHAIN - 查找该节点的子节点，递归构建子 RuleChain，包装为 {@link ChainAsRule} 适配器</li>
     *   <li>GROUP - 将 GROUP 下全部子节点解析为规则列表，包装为 {@link ChainAsRule}（THEN 顺序执行）</li>
     * </ul>
     */
    private static Rule resolveNode(ChainNodeDTO node, RuleResolver resolver) {
        if (node == null) return null;
        if ("SINGLE".equals(node.getNodeType()) && node.getRuleCode() != null) {
            return resolver.resolve(node.getRuleCode());
        }
        // CHAIN/GROUP 类型暂不支持直接 resolve 为单一 Rule
        // 嵌套子链解析需要图上下文，在外部 toChain 方法中处理
        return null;
    }

    /**
     * 解析单个节点为 Rule（带图上下文，支持嵌套子链递归解析）
     *
     * <p>P1-7 增强：CHAIN 类型节点会递归查找子节点并构建子 RuleChain，
     * 包装为 {@link ChainAsRule} 适配器后返回。
     *
     * @param node     节点
     * @param graph    画布图（用于查找子节点）
     * @param resolver 规则解析器
     * @return Rule 实例；无法解析返回 null
     * @since 1.0.0
     */
    private static Rule resolveNodeWithContext(ChainNodeDTO node, RuleChainGraph graph, RuleResolver resolver) {
        if (node == null) return null;
        if ("SINGLE".equals(node.getNodeType()) && node.getRuleCode() != null) {
            return resolver.resolve(node.getRuleCode());
        }
        if ("CHAIN".equals(node.getNodeType())) {
            // 递归构建子链
            List<ChainNodeDTO> children = findChildren(graph, node.getNodeId());
            String chainType = node.getChainType() != null ? node.getChainType() : "THEN";
            RuleChain subChain = buildChainFromChildren(chainType, children, graph, resolver, node);
            if (subChain != null) {
                return new ChainAsRule(subChain);
            }
        }
        if ("GROUP".equals(node.getNodeType())) {
            // GROUP 节点：将子节点解析为规则列表，构建 THEN 链
            List<ChainNodeDTO> children = findChildren(graph, node.getNodeId());
            if (children == null || children.isEmpty()) return null;
            List<Rule> rules = new ArrayList<>();
            for (ChainNodeDTO child : children) {
                Rule r = resolveNodeWithContext(child, graph, resolver);
                if (r != null) rules.add(r);
            }
            if (!rules.isEmpty()) {
                return new ChainAsRule(RuleChain.then(rules.toArray(new Rule[0])));
            }
        }
        return null;
    }

    /**
     * 根据链类型和子节点构建 RuleChain
     *
     * <p>P1-7：支持嵌套 CHAIN 节点的递归解析
     */
    private static RuleChain buildChainFromChildren(String chainType, List<ChainNodeDTO> children,
                                                     RuleChainGraph graph, RuleResolver resolver,
                                                     ChainNodeDTO parentNode) {
        if (children == null || children.isEmpty()) return null;

        switch (chainType) {
            case "THEN":
            case "WHEN": {
                List<Rule> rules = new ArrayList<>();
                for (ChainNodeDTO child : children) {
                    Rule r = resolveNodeWithContext(child, graph, resolver);
                    if (r != null) rules.add(r);
                }
                if (rules.isEmpty()) return null;
                return "WHEN".equals(chainType)
                        ? RuleChain.when(rules.toArray(new Rule[0]))
                        : RuleChain.then(rules.toArray(new Rule[0]));
            }
            case "IF": {
                String condition = "true";
                if (parentNode.getMetadata() != null && parentNode.getMetadata().get("condition") != null) {
                    condition = String.valueOf(parentNode.getMetadata().get("condition"));
                }
                ChainEdgeDTO firstEdge = findFirstEdge(graph, parentNode.getNodeId());
                if (firstEdge != null && firstEdge.getCondition() != null) {
                    condition = firstEdge.getCondition();
                }
                Rule action = resolveNodeWithContext(children.get(0), graph, resolver);
                return action != null ? RuleChain.ifThen(condition, action) : null;
            }
            default:
                // 其他类型降级为 THEN
                List<Rule> rules = new ArrayList<>();
                for (ChainNodeDTO child : children) {
                    Rule r = resolveNodeWithContext(child, graph, resolver);
                    if (r != null) rules.add(r);
                }
                return rules.isEmpty() ? null : RuleChain.then(rules.toArray(new Rule[0]));
        }
    }

    /**
     * 查找节点的所有直接子节点
     */
    private static List<ChainNodeDTO> findChildren(RuleChainGraph graph, String parentId) {
        List<ChainNodeDTO> result = new ArrayList<>();
        if (graph.getNodes() == null) return result;
        for (ChainNodeDTO n : graph.getNodes()) {
            if (parentId != null && parentId.equals(n.getParentNodeId())) {
                result.add(n);
            }
        }
        return result;
    }

    /**
     * 按 nodeId 查找节点
     */
    private static ChainNodeDTO findNode(RuleChainGraph graph, String nodeId) {
        if (graph.getNodes() == null || nodeId == null) return null;
        for (ChainNodeDTO n : graph.getNodes()) {
            if (nodeId.equals(n.getNodeId())) {
                return n;
            }
        }
        return null;
    }

    /**
     * 查找指定源节点的第一条出边
     */
    private static ChainEdgeDTO findFirstEdge(RuleChainGraph graph, String sourceId) {
        if (graph.getEdges() == null) return null;
        for (ChainEdgeDTO e : graph.getEdges()) {
            if (sourceId != null && sourceId.equals(e.getSourceNodeId())) {
                return e;
            }
        }
        return null;
    }

    /**
     * 取第一个子节点解析为 Rule
     */
    private static Rule firstChildRule(List<ChainNodeDTO> children,
                                       RuleChainGraph graph,
                                       RuleResolver resolver) {
        if (children == null || children.isEmpty()) return null;
        return resolveNode(children.get(0), resolver);
    }
}
