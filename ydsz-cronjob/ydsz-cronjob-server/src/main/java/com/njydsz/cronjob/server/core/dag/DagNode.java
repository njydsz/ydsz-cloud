package com.njydsz.cronjob.server.core.dag;

import java.util.Objects;

import com.njydsz.common.json.annotation.JsonClass;
import com.njydsz.common.json.annotation.JsonProperty;

/**
 * DAG 节点定义（P1-5/P1-6 子工作流/审批节点）。
 *
 * <p>对应 dag_definition JSON 中的 nodes 数组元素，描述一个任务节点及其在前端可视化画布上的坐标位置。
 *
 * <p>支持三种节点类型：
 *
 * <ul>
 *   <li>{@link NodeType#TASK}：普通任务节点，调用 handler 执行
 *   <li>{@link NodeType#SUB_WORKFLOW}：子工作流节点，嵌套触发另一个 DAG 工作流（P1-5）
 *   <li>{@link NodeType#APPROVAL}：审批节点，等待人工审批后继续执行（P1-6）
 * </ul>
 *
 * <p><b>注意</b>：CONDITION / LOOP / PARALLEL_GATEWAY 控制节点已于 1.0.0 移除，建议使用工作流引擎替代复杂编排场景。
 * 若反序列化时遇到旧数据中的控制节点类型，{@link #resolveNodeType()} 会降级为 {@link NodeType#TASK}。
 *
 * @param jobKey 任务 KEY（唯一标识节点，边通过 jobKey 引用）
 * @param jobId 任务 ID（冗余，便于直接派发）
 * @param label 节点显示名称（前端画布展示）
 * @param x 画布 X 坐标（前端可视化用）
 * @param y 画布 Y 坐标（前端可视化用）
 * @param paramsJson 节点级参数 JSON（覆盖任务默认 paramsJson，null 表示用任务默认值）
 * @param nodeType 节点类型（null 默认 TASK）
 * @param conditionExpression 保留字段（已废弃，序列化兼容用）
 * @param loopCount 保留字段（已废弃，序列化兼容用）
 * @param parallelBranches 保留字段（已废弃，序列化兼容用）
 * @param subWorkflowDagKey 子工作流 DAG KEY（SUB_WORKFLOW 节点，P1-5）
 * @param approvalUsers 审批人列表（APPROVAL 节点，逗号分隔，P1-6）
 * @param approvalTimeoutMinutes 审批超时时间（分钟，超时自动拒绝，P1-6）
 * @author ydsz-team
 * @since 1.0.0
 */
@JsonClass(description = "DAG 节点定义，标记可安全反序列化")
public record DagNode(
    @JsonProperty("jobKey") String jobKey,
    String jobId,
    String label,
    int x,
    int y,
    String paramsJson,
    String nodeType,
    String conditionExpression,
    Integer loopCount,
    Integer parallelBranches,
    String subWorkflowDagKey,
    String approvalUsers,
    Integer approvalTimeoutMinutes) {

  /** 默认节点类型：TASK */
  public static final String DEFAULT_NODE_TYPE = NodeType.TASK.name();

  /** 紧凑构造器：校验 jobKey 非空；nodeType 为空时默认 TASK。 */
  public DagNode {
    Objects.requireNonNull(jobKey, "jobKey 不能为空");
    if (nodeType == null || nodeType.isBlank()) {
      nodeType = DEFAULT_NODE_TYPE;
    }
  }

  // ==================== 兼容旧构造器（4 参数版，新字段为 null） ====================

  /** 兼容构造器：旧 10 参数版本（无 subWorkflowDagKey/approvalUsers/approvalTimeoutMinutes）。 */
  public DagNode(
      String jobKey,
      String jobId,
      String label,
      int x,
      int y,
      String paramsJson,
      String nodeType,
      String conditionExpression,
      Integer loopCount,
      Integer parallelBranches) {
    this(
        jobKey,
        jobId,
        label,
        x,
        y,
        paramsJson,
        nodeType,
        conditionExpression,
        loopCount,
        parallelBranches,
        null,
        null,
        null);
  }

  /** 工厂方法：创建节点（坐标默认 0,0）。 */
  public static DagNode of(String jobKey, String jobId, String label) {
    return new DagNode(jobKey, jobId, label, 0, 0, null, DEFAULT_NODE_TYPE, null, null, null);
  }

  /** 工厂方法：创建带坐标的节点。 */
  public static DagNode of(String jobKey, String jobId, String label, int x, int y) {
    return new DagNode(jobKey, jobId, label, x, y, null, DEFAULT_NODE_TYPE, null, null, null);
  }

  /** 工厂方法：创建 TASK 节点（兼容旧调用，无新字段）。 */
  public static DagNode of(
      String jobKey, String jobId, String label, int x, int y, String paramsJson) {
    return new DagNode(jobKey, jobId, label, x, y, paramsJson, DEFAULT_NODE_TYPE, null, null, null);
  }

  /**
   * P1-5: 工厂方法：创建 SUB_WORKFLOW 子工作流节点。
   *
   * @param jobKey 节点 KEY
   * @param label 显示名称
   * @param subWorkflowDagKey 子工作流 DAG KEY（必须存在于 ydsz_job_dag 表）
   * @return SUB_WORKFLOW 节点
   */
  public static DagNode subWorkflow(String jobKey, String label, String subWorkflowDagKey) {
    return new DagNode(
        jobKey,
        null,
        label,
        0,
        0,
        null,
        NodeType.SUB_WORKFLOW.name(),
        null,
        null,
        null,
        subWorkflowDagKey,
        null,
        null);
  }

  /**
   * P1-6: 工厂方法：创建 APPROVAL 审批节点。
   *
   * @param jobKey 节点 KEY
   * @param label 显示名称
   * @param approvalUsers 审批人列表（逗号分隔，如 "user1,user2"）
   * @param approvalTimeoutMinutes 审批超时时间（分钟，超时自动拒绝）
   * @return APPROVAL 节点
   */
  public static DagNode approval(
      String jobKey, String label, String approvalUsers, int approvalTimeoutMinutes) {
    return new DagNode(
        jobKey,
        null,
        label,
        0,
        0,
        null,
        NodeType.APPROVAL.name(),
        null,
        null,
        null,
        null,
        approvalUsers,
        approvalTimeoutMinutes);
  }

  /**
   * 解析节点类型字符串，无效值返回 {@link NodeType#TASK}。
   *
   * @return 节点类型枚举（永不为 null）
   */
  public NodeType resolveNodeType() {
    return NodeType.parse(nodeType);
  }

  /**
   * DAG 节点类型枚举。
   *
   * <p>P2-O2: CONDITION / LOOP / PARALLEL_GATEWAY 已于 1.0.0 移除，标注 {@link Deprecated}，
   * 枚举值保留仅用于反序列化兼容旧数据。遇到旧数据中的控制节点类型时，
   * {@link #parse(String)} 会降级返回 {@link #TASK}。新增节点类型请勿复用废弃值。
   */
  public enum NodeType {
    /** 普通任务节点：调用 handler 执行 */
    TASK,
    /** 已废弃：条件分支节点（1.0.0 移除，反序列化时降级为 TASK） */
    @Deprecated
    CONDITION,
    /** 已废弃：循环节点（1.0.0 移除，反序列化时降级为 TASK） */
    @Deprecated
    LOOP,
    /** 已废弃：并行网关节点（1.0.0 移除，反序列化时降级为 TASK） */
    @Deprecated
    PARALLEL_GATEWAY,
    /** P1-5: 子工作流节点：嵌套触发另一个 DAG 工作流 */
    SUB_WORKFLOW,
    /** P1-6: 审批节点：等待人工审批后继续执行 */
    APPROVAL;

    /**
     * 安全解析节点类型字符串，无效值返回 {@link #TASK}。
     *
     * @param value 节点类型字符串
     * @return 解析后的节点类型；无效值返回 TASK
     */
    public static NodeType parse(String value) {
      if (value == null || value.isBlank()) {
        return TASK;
      }
      try {
        return NodeType.valueOf(value.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        return TASK;
      }
    }
  }
}
