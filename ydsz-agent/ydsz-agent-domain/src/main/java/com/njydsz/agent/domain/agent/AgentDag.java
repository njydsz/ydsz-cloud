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
 * <p>每个节点（{@link Node}）代表一个 Agent 执行步骤，边（依赖关系）决定执行顺序。 支持并行执行无依赖的节点。
 *
 * <p><b>线程安全</b>：构造后 nodes/edges 以 unmodifiableMap 封装，全字段 final，实例不可变、可安全共享。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class AgentDag implements Serializable {

  private static final long serialVersionUID = 1L;

  /** DAG 唯一标识 */
  private final String id;

  /** DAG 名称（null 时取 id） */
  private final String name;

  /** 节点表（nodeId -> Node，不可变） */
  private final Map<String, Node> nodes;

  /** 边表（nodeId -> 依赖的节点 ID 列表，不可变） */
  private final Map<String, List<String>> edges;

  /**
   * 构造 DAG。
   *
   * @param id DAG 唯一标识（不可为 null）
   * @param name DAG 名称（null 时取 id）
   * @param nodes 节点表
   * @param edges 边表（nodeId -> 依赖节点 ID 列表）
   */
  public AgentDag(
      String id, String name, Map<String, Node> nodes, Map<String, List<String>> edges) {
    this.id = Objects.requireNonNull(id, "id 不能为 null");
    this.name = name != null ? name : id;
    this.nodes = Collections.unmodifiableMap(new HashMap<>(nodes));
    this.edges = Collections.unmodifiableMap(new HashMap<>(edges));
  }

  /**
   * 获取 DAG 唯一标识。
   *
   * @return DAG 唯一标识
   */
  public String getId() {
    return id;
  }

  /**
   * 获取 DAG 名称。
   *
   * @return DAG 名称
   */
  public String getName() {
    return name;
  }

  /**
   * 获取节点表。
   *
   * @return 不可变节点表（nodeId -> Node）
   */
  public Map<String, Node> getNodes() {
    return nodes;
  }

  /**
   * 获取边表。
   *
   * @return 不可变边表（nodeId -> 依赖节点 ID 列表）
   */
  public Map<String, List<String>> getEdges() {
    return edges;
  }

  /**
   * 获取所有入度为 0 的节点（可立即执行的节点）。
   *
   * @return 根节点列表
   */
  public List<Node> getRootNodes() {
    Set<String> hasIncoming = new HashSet<>(16);
    for (List<String> deps : edges.values()) {
      hasIncoming.addAll(deps);
    }
    List<Node> roots = new ArrayList<>(16);
    for (String nodeId : nodes.keySet()) {
      // 不在任何边依赖集合中的节点入度为 0，无前置依赖、可立即调度执行
      if (!hasIncoming.contains(nodeId)) {
        roots.add(nodes.get(nodeId));
      }
    }
    return roots;
  }

  /**
   * 获取指定节点的所有依赖节点。
   *
   * @param nodeId 节点 ID
   * @return 依赖节点列表（定义不一致的依赖 ID 静默跳过）
   */
  public List<Node> getDependencies(String nodeId) {
    List<String> depIds = edges.getOrDefault(nodeId, List.of());
    List<Node> deps = new ArrayList<>(16);
    for (String depId : depIds) {
      Node dep = nodes.get(depId);
      // 依赖 ID 在节点表中缺失时静默跳过，避免 DAG 定义不一致导致 NPE
      if (dep != null) {
        deps.add(dep);
      }
    }
    return deps;
  }

  /** DAG 中的一个节点 */
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

    /**
     * 构造节点。
     *
     * @param id 节点 ID（不可为 null）
     * @param agentType Agent 类型（不可为 null）
     * @param prompt 节点提示词（null 时取空串）
     * @param inputFrom 输入来源节点 ID（可为 null）
     * @param config 节点额外配置（null 时为空 Map）
     */
    public Node(
        String id, String agentType, String prompt, String inputFrom, Map<String, Object> config) {
      this.id = Objects.requireNonNull(id, "id 不能为 null");
      this.agentType = Objects.requireNonNull(agentType, "agentType 不能为 null");
      this.prompt = prompt != null ? prompt : "";
      this.inputFrom = inputFrom;
      this.config = config != null ? Map.copyOf(config) : Map.of();
    }

    /**
     * 获取节点 ID。
     *
     * @return 节点 ID
     */
    public String getId() {
      return id;
    }

    /**
     * 获取 Agent 类型。
     *
     * @return Agent 类型
     */
    public String getAgentType() {
      return agentType;
    }

    /**
     * 获取节点提示词。
     *
     * @return 节点提示词
     */
    public String getPrompt() {
      return prompt;
    }

    /**
     * 获取输入来源节点 ID。
     *
     * @return 输入来源节点 ID（无来源时为 null）
     */
    public String getInputFrom() {
      return inputFrom;
    }

    /**
     * 获取节点额外配置。
     *
     * @return 不可变配置映射
     */
    public Map<String, Object> getConfig() {
      return config;
    }

    @Override
    public String toString() {
      return "Node{id='" + id + "', type=" + agentType + "'}";
    }
  }
}
