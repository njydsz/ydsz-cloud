package com.njydsz.agent.domain.agent;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * DAG 执行检查点
 *
 * <p>快照编排执行的中间状态，支持从断点续跑。 记录已完成节点的输出、失败节点集合以及原始请求上下文（DSL / 用户输入）， 以便在原执行超时或中断后跳过已成功的节点、仅重试失败及未执行的节点。
 *
 * <p><b>线程安全</b>：所有字段 final，集合经不可变封装，实例不可变、可安全跨线程传递。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class DagCheckpoint implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 执行 ID（与 {@link DagOrchestrationExecutor.DagExecutionResult#executionId()} 一致） */
  private final String executionId;

  /** DAG 名称 */
  private final String dagName;

  /** 原始 YAML DSL（用于续跑时重建 DAG 拓扑） */
  private final String dsl;

  /** 用户原始输入 */
  private final String userInput;

  /** 各节点执行结果（节点 ID → 输出），不可变映射 */
  private final Map<String, String> nodeResults;

  /** 成功完成的节点集合 */
  private final Set<String> completedNodes;

  /** 执行失败的节点集合 */
  private final Set<String> failedNodes;

  /** 快照时间（最后更新时间） */
  private final LocalDateTime snapshotTime;

  public DagCheckpoint(
      String executionId,
      String dagName,
      String dsl,
      String userInput,
      Map<String, String> nodeResults,
      Set<String> completedNodes,
      Set<String> failedNodes,
      LocalDateTime snapshotTime) {
    this.executionId = Objects.requireNonNull(executionId, "executionId 不能为 null");
    this.dagName = dagName;
    this.dsl = dsl;
    this.userInput = userInput;
    this.nodeResults = nodeResults != null ? Map.copyOf(nodeResults) : Collections.emptyMap();
    this.completedNodes = completedNodes != null ? Set.copyOf(completedNodes) : Set.of();
    this.failedNodes = failedNodes != null ? Set.copyOf(failedNodes) : Set.of();
    this.snapshotTime = snapshotTime != null ? snapshotTime : LocalDateTime.now();
  }

  public String getExecutionId() {
    return executionId;
  }

  public String getDagName() {
    return dagName;
  }

  public String getDsl() {
    return dsl;
  }

  public String getUserInput() {
    return userInput;
  }

  public Map<String, String> getNodeResults() {
    return nodeResults;
  }

  public Set<String> getCompletedNodes() {
    return completedNodes;
  }

  public Set<String> getFailedNodes() {
    return failedNodes;
  }

  public LocalDateTime getSnapshotTime() {
    return snapshotTime;
  }

  /**
   * 判断该检查点是否对应一个已完成的编排（无失败节点且至少有一个节点完成）。
   *
   * @return {@code true} 表示全部成功，无需续跑
   */
  public boolean isFullyCompleted() {
    return failedNodes.isEmpty() && !completedNodes.isEmpty();
  }

  /**
   * 判断节点是否已成功完成（结果存在于检查点中）。
   *
   * @param nodeId 节点 ID
   * @return {@code true} 表示该节点已产出结果，续跑时应跳过
   */
  public boolean isNodeCompleted(String nodeId) {
    return completedNodes.contains(nodeId);
  }

  @Override
  public String toString() {
    return "DagCheckpoint{executionId='"
        + executionId
        + "', dagName='"
        + dagName
        + "', completed="
        + completedNodes.size()
        + ", failed="
        + failedNodes.size()
        + "}";
  }
}
