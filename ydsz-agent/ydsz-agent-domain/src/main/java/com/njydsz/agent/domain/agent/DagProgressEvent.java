package com.njydsz.agent.domain.agent;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * DAG 节点执行进度事件
 *
 * <p>封装 DAG 编排过程中单个节点的生命周期事件，通过流式通道实时推送至前端，实现多节点编排的进度感知。
 *
 * <p>事件类型：
 *
 * <ul>
 *   <li>{@link #NODE_STARTED} — 节点开始执行
 *   <li>{@link #NODE_COMPLETED} — 节点执行成功
 *   <li>{@link #NODE_FAILED} — 节点执行失败（可据此定位失败节点并重试）
 *   <li>{@link #DAG_STARTED} — 编排启动（含总节点数）
 *   <li>{@link #DAG_COMPLETED} — 编排全部完成
 * </ul>
 *
 * <p>通过 SSE {@code progress} 事件推送，前端据此渲染节点级进度条 / 状态指示灯。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class DagProgressEvent implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 编排启动 */
  public static final String DAG_STARTED = "dag_started";

  /** 编排全部完成 */
  public static final String DAG_COMPLETED = "dag_completed";

  /** 节点开始执行 */
  public static final String NODE_STARTED = "node_started";

  /** 节点执行成功 */
  public static final String NODE_COMPLETED = "node_completed";

  /** 节点执行失败 */
  public static final String NODE_FAILED = "node_failed";

  /** 事件类型 */
  private final String eventType;

  /** 节点 ID（编排级事件可为 null） */
  private final String nodeId;

  /** 节点类型（CHAT / REACT / RAG 等） */
  private final String nodeType;

  /** 已完成的节点计数 */
  private final int completedCount;

  /** 总节点计数 */
  private final int totalCount;

  /** 错误信息（仅 NODE_FAILED） */
  private final String error;

  /** 事件时间 */
  private final LocalDateTime timestamp;

  /**
   * 全参构造。
   *
   * @param eventType 事件类型
   * @param nodeId 节点 ID（编排级事件可为 null）
   * @param nodeType 节点类型（CHAT / REACT / RAG 等）
   * @param completedCount 已完成的节点计数
   * @param totalCount 总节点计数
   * @param error 错误信息（仅 NODE_FAILED）
   * @param timestamp 事件时间（null 时取当前时间）
   */
  public DagProgressEvent(
      String eventType,
      String nodeId,
      String nodeType,
      int completedCount,
      int totalCount,
      String error,
      LocalDateTime timestamp) {
    this.eventType = Objects.requireNonNull(eventType, "eventType 不能为 null");
    this.nodeId = nodeId;
    this.nodeType = nodeType;
    this.completedCount = completedCount;
    this.totalCount = totalCount;
    this.error = error;
    this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
  }

  /**
   * 便捷工厂：创建编排启动事件。
   *
   * @param executionId 执行 ID
   * @param totalCount 总节点计数
   * @return 编排启动事件
   */
  public static DagProgressEvent dagStarted(String executionId, int totalCount) {
    return new DagProgressEvent(DAG_STARTED, executionId, null, 0, totalCount, null, LocalDateTime.now());
  }

  /**
   * 便捷工厂：创建编排完成事件。
   *
   * @param executionId 执行 ID
   * @param totalCount 总节点计数
   * @return 编排完成事件
   */
  public static DagProgressEvent dagCompleted(String executionId, int totalCount) {
    return new DagProgressEvent(DAG_COMPLETED, executionId, null, totalCount, totalCount, null, LocalDateTime.now());
  }

  /**
   * 便捷工厂：创建节点启动事件。
   *
   * @param nodeId 节点 ID
   * @param nodeType 节点类型
   * @param completed 已完成的节点计数
   * @param total 总节点计数
   * @return 节点启动事件
   */
  public static DagProgressEvent nodeStarted(String nodeId, String nodeType, int completed, int total) {
    return new DagProgressEvent(NODE_STARTED, nodeId, nodeType, completed, total, null, LocalDateTime.now());
  }

  /**
   * 便捷工厂：创建节点完成事件。
   *
   * @param nodeId 节点 ID
   * @param nodeType 节点类型
   * @param completed 已完成的节点计数
   * @param total 总节点计数
   * @return 节点完成事件
   */
  public static DagProgressEvent nodeCompleted(String nodeId, String nodeType, int completed, int total) {
    return new DagProgressEvent(NODE_COMPLETED, nodeId, nodeType, completed, total, null, LocalDateTime.now());
  }

  /**
   * 便捷工厂：创建节点失败事件。
   *
   * @param nodeId 节点 ID
   * @param nodeType 节点类型
   * @param completed 已完成的节点计数
   * @param total 总节点计数
   * @param error 错误信息
   * @return 节点失败事件
   */
  public static DagProgressEvent nodeFailed(String nodeId, String nodeType, int completed, int total, String error) {
    return new DagProgressEvent(NODE_FAILED, nodeId, nodeType, completed, total, error, LocalDateTime.now());
  }

  /**
   * 获取事件类型。
   *
   * @return 事件类型
   */
  public String getEventType() {
    return eventType;
  }

  /**
   * 获取节点 ID。
   *
   * @return 节点 ID（编排级事件为 null）
   */
  public String getNodeId() {
    return nodeId;
  }

  /**
   * 获取节点类型。
   *
   * @return 节点类型（CHAT / REACT / RAG 等）
   */
  public String getNodeType() {
    return nodeType;
  }

  /**
   * 获取已完成节点计数。
   *
   * @return 已完成的节点计数
   */
  public int getCompletedCount() {
    return completedCount;
  }

  /**
   * 获取总节点计数。
   *
   * @return 总节点计数
   */
  public int getTotalCount() {
    return totalCount;
  }

  /**
   * 获取错误信息。
   *
   * @return 错误信息（仅 NODE_FAILED，其余为 null）
   */
  public String getError() {
    return error;
  }

  /**
   * 获取事件时间。
   *
   * @return 事件时间
   */
  public LocalDateTime getTimestamp() {
    return timestamp;
  }

  @Override
  public String toString() {
    return "DagProgressEvent{type='"
        + eventType
        + "', node='"
        + nodeId
        + "', progress="
        + completedCount
        + "/"
        + totalCount
        + "}";
  }
}
