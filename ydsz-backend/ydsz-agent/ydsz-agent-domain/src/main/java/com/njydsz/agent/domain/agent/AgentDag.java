package com.njydsz.agent.domain.agent;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Agent 编排 DAG（有向无环图）
 *
 * <p>由 YAML DSL 解析而来，描述多个 Agent 步骤之间的依赖关系和执行顺序。
 *
 * <p>每个节点（{@link Node}）代表一个 Agent 执行步骤，边（依赖关系）决定执行顺序。
 * 支持并行执行无依赖的节点。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class AgentDag implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final String name;
    private final Map<String, Node> nodes;
    private final Map<String, List<String>> edges;

    public AgentDag(String id, String name, Map<String, Node> nodes, Map<String, List<String>> edges) {
        this.id = Objects.requireNonNull(id, "id 不能为 null");
        this.name = name != null ? name : id;
        this.nodes = Collections.unmodifiableMap(new HashMap<>(nodes));
        this.edges = Collections.unmodifiableMap(new HashMap<>(edges));
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Map<String, Node> getNodes() { return nodes; }
    public Map<String, List<String>> getEdges() { return edges; }

    /**
     * 获取所有入度为 0 的节点（可立即执行的节点）
     */
    public List<Node> getRootNodes() {
        Set<String> hasIncoming = new HashSet<>();
        for (List<String> deps : edges.values()) {
            hasIncoming.addAll(deps);
        }
        List<Node> roots = new ArrayList<>();
        for (String nodeId : nodes.keySet()) {
            if (!hasIncoming.contains(nodeId)) {
                roots.add(nodes.get(nodeId));
            }
        }
        return roots;
    }

    /**
     * 获取指定节点的所有依赖节点
     */
    public List<Node> getDependencies(String nodeId) {
        List<String> depIds = edges.getOrDefault(nodeId, List.of());
        List<Node> deps = new ArrayList<>();
        for (String depId : depIds) {
            Node dep = nodes.get(depId);
            if (dep != null) {
                deps.add(dep);
            }
        }
        return deps;
    }

    /**
     * DAG 中的一个节点
     */
    public static final class Node implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 节点 ID */
        private final String id;
        /** Agent 类型 */
        private final String agentType;
        /** 节点提示词 */
        private final String prompt;
        /** 输入来源节点 ID */
        private final String inputFrom;
        /** 节点额外配置 */
        private final Map<String, Object> config;

        public Node(String id, String agentType, String prompt,
                    String inputFrom, Map<String, Object> config) {
            this.id = Objects.requireNonNull(id, "id 不能为 null");
            this.agentType = Objects.requireNonNull(agentType, "agentType 不能为 null");
            this.prompt = prompt != null ? prompt : "";
            this.inputFrom = inputFrom;
            this.config = config != null ? Map.copyOf(config) : Map.of();
        }

        public String getId() { return id; }
        public String getAgentType() { return agentType; }
        public String getPrompt() { return prompt; }
        public String getInputFrom() { return inputFrom; }
        public Map<String, Object> getConfig() { return config; }

        @Override
        public String toString() {
            return "Node{id='" + id + "', type=" + agentType + "'}";
        }
    }
}
