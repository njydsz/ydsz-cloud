package com.njydsz.cronjob.server.core.dag;

import java.util.Objects;

import com.njydsz.common.json.annotation.JsonClass;
import com.njydsz.common.json.annotation.JsonProperty;

/**
 * DAG 节点定义（P1-5/P1-6 子工作流/审批节点）。
 *
 * <p>对应 dag_definition JSON 中的 nodes 数组元素，描述一个任务节点及其在前端可视化画布上的坐标位置。
 *
 * <p>支持五种节点类型：
 *
 * <ul>
 *   <li>{@link NodeType#TASK}：普通任务节点，调用 handler 执行
 *   <li>{@link NodeType#CONDITION}：条件分支节点，根据 SpEL 表达式结果选择分支（P1-1）
 *   <li>{@link NodeType#PARALLEL_GATEWAY}：并行网关节点，Fork/Join 并行执行（P1-1）
 *   <li>{@link NodeType#SUB_WORKFLOW}：子工作流节点，嵌套触发另一个 DAG 工作流（P1-5）
 *   <li>{@link NodeType#APPROVAL}：审批节点，等待人工审批后继续执行（P1-6）
 * </ul>
 *
 * @param jobKey 任务 KEY（唯一标识节点，边通过 jobKey 引用）
 * @param jobId 任务 ID（冗余，便于直接派发）
 * @param label 节点显示名称（前端画布展示）
 * @param x 画布 X 坐标（前端可视化用）
 * @param y 画布 Y 坐标（前端可视化用）
 * @param paramsJson 节点级参数 JSON（覆盖任务默认 paramsJson，null 表示用任务默认值）
 * @param nodeType 节点类型（null 默认 TASK）
 * @param conditionExpression 条件表达式（CONDITION 节点必填，SpEL 语法，如 "${a.result=='success'}"）
 * @param loopCount 保留字段（已废弃，序列化兼容用）
 * @param parallelBranches 并行分支数（PARALLEL_GATEWAY 节点可选，默认按出边数确定）
 * @param subWorkflowDagKey 子工作流 DAG KEY（SUB_WORKFLOW 节点，P1-5）
 * @param approvalUsers 审批人列表（APPROVAL 节点，逗号分隔，P1-6）
 * @param approvalTimeoutMinutes 审批超时时间（分钟，超时自动拒绝，P1-6）
 * @author ydsz-team
 * @since 26.09.01
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
   * P1-1: 工厂方法：创建 CONDITION 条件分支节点。
   *
   * <p>条件节点根据 SpEL 表达式求值结果决定触发哪些后继分支。
   * 表达式返回 boolean 类型：true 触发条件为真的分支，false 触发条件为假的分支。
   *
   * <p>SpEL 表达式可通过 {@code #{}} 引用 DAG 上下文中的变量，如：
   * <pre>{@code
   * "#{context['a'].result == 'success'}"
   * "#{context['b'].count > 100}"
   * }</pre>
   *
   * @param jobKey 节点 KEY
   * @param label 显示名称
   * @param conditionExpression SpEL 条件表达式（必填）
   * @return CONDITION 节点
   */
  public static DagNode condition(String jobKey, String label, String conditionExpression) {
    return new DagNode(
        jobKey,
        null,
        label,
        0,
        0,
        null,
        NodeType.CONDITION.name(),
        conditionExpression,
        null,
        null,
        null,
        null,
        null);
  }

  /**
   * P1-1: 工厂方法：创建 PARALLEL_GATEWAY 并行网关节点。
   *
   * <p>并行网关支持 Fork（分叉）和 Join（汇合）两种模式：
   *
   * <ul>
   *   <li><b>Fork</b>：一个入边，多个出边 → 同时触发所有后继分支并行执行</li>
   *   <li><b>Join</b>：多个入边，一个出边 → 等待所有入边完成后触发后继</li>
   * </ul>
   *
   * @param jobKey 节点 KEY
   * @param label 显示名称
   * @param parallelBranches 并行分支数（可选，null 表示按出边数自动确定）
   * @return PARALLEL_GATEWAY 节点
   */
  public static DagNode parallelGateway(String jobKey, String label, Integer parallelBranches) {
    return new DagNode(
        jobKey,
        null,
        label,
        0,
        0,
        null,
        NodeType.PARALLEL_GATEWAY.name(),
        null,
        null,
        parallelBranches,
        null,
        null,
        null);
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
   * <p>支持 5 种节点类型：TASK（任务）、CONDITION（条件分支）、PARALLEL_GATEWAY（并行网关）、
   * SUB_WORKFLOW（子工作流）、APPROVAL（审批）。
   *
   * <p>LOOP 类型已废弃，保留枚举值仅用于反序列化兼容旧数据。
   */
  public enum NodeType {
    /** 普通任务节点：调用 handler 执行 */
    TASK,
    /** P1-1: 条件分支节点：根据 SpEL 表达式结果选择分支 */
    CONDITION,
    /** 已废弃：循环节点（26.09.01 移除，反序列化时降级为 TASK） */
    @Deprecated
    LOOP,
    /** P1-1: 并行网关节点：Fork/Join 并行执行 */
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
