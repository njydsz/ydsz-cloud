package com.njydsz.cronjob.server.core.dag;

import java.util.Objects;

/**
 * DAG 节点定义（P2 DAG 增强 + P1-5/P1-6 子工作流/审批节点）。
 *
 * <p>对应 dag_definition JSON 中的 nodes 数组元素，描述一个任务节点
 * 及其在前端可视化画布上的坐标位置。
 *
 * <p>支持六种节点类型（对标 DolphinScheduler / Airflow）：
 * <ul>
 *   <li>{@link NodeType#TASK}：普通任务节点，调用 handler 执行</li>
 *   <li>{@link NodeType#CONDITION}：条件分支节点，根据 conditionExpression 评估结果决定走哪条边</li>
 *   <li>{@link NodeType#LOOP}：循环节点，重复执行下游节点 loopCount 次</li>
 *   <li>{@link NodeType#PARALLEL_GATEWAY}：并行网关节点，使用 CompletableFuture 并行执行所有下游分支</li>
 *   <li>{@link NodeType#SUB_WORKFLOW}：子工作流节点，嵌套触发另一个 DAG 工作流（P1-5）</li>
 *   <li>{@link NodeType#APPROVAL}：审批节点，等待人工审批后继续执行（P1-6）</li>
 * </ul>
 *
 * @param jobKey                 任务 KEY（唯一标识节点，边通过 jobKey 引用）
 * @param jobId                  任务 ID（冗余，便于直接派发；控制节点可为 null）
 * @param label                  节点显示名称（前端画布展示）
 * @param x                      画布 X 坐标（前端可视化用）
 * @param y                      画布 Y 坐标（前端可视化用）
 * @param paramsJson             节点级参数 JSON（覆盖任务默认 paramsJson，null 表示用任务默认值）
 * @param nodeType               节点类型（null 默认 TASK）
 * @param conditionExpression    条件表达式（CONDITION 节点）
 * @param loopCount              循环次数（LOOP 节点）
 * @param parallelBranches       并行分支数（PARALLEL_GATEWAY 节点）
 * @param subWorkflowDagKey      子工作流 DAG KEY（SUB_WORKFLOW 节点，P1-5）
 * @param approvalUsers          审批人列表（APPROVAL 节点，逗号分隔，P1-6）
 * @param approvalTimeoutMinutes 审批超时时间（分钟，超时自动拒绝，P1-6）
 * @author ydsz-team
 * @since 1.0.0
 */
public record DagNode(String jobKey, String jobId, String label,
                       int x, int y, String paramsJson,
                       String nodeType, String conditionExpression,
                       Integer loopCount, Integer parallelBranches,
                       String subWorkflowDagKey, String approvalUsers,
                       Integer approvalTimeoutMinutes) {

    /** 默认节点类型：TASK */
    public static final String DEFAULT_NODE_TYPE = NodeType.TASK.name();

    /**
     * 紧凑构造器：校验 jobKey 非空；nodeType 为空时默认 TASK。
     */
    public DagNode {
        Objects.requireNonNull(jobKey, "jobKey 不能为空");
        if (nodeType == null || nodeType.isBlank()) {
            nodeType = DEFAULT_NODE_TYPE;
        }
    }

    // ==================== 兼容旧构造器（4 参数版，新字段为 null） ====================

    /**
     * 兼容构造器：旧 10 参数版本（无 subWorkflowDagKey/approvalUsers/approvalTimeoutMinutes）。
     */
    public DagNode(String jobKey, String jobId, String label,
                   int x, int y, String paramsJson,
                   String nodeType, String conditionExpression,
                   Integer loopCount, Integer parallelBranches) {
        this(jobKey, jobId, label, x, y, paramsJson, nodeType, conditionExpression,
                loopCount, parallelBranches, null, null, null);
    }

    /**
     * 工厂方法：创建节点（坐标默认 0,0）。
     */
    public static DagNode of(String jobKey, String jobId, String label) {
        return new DagNode(jobKey, jobId, label, 0, 0, null, DEFAULT_NODE_TYPE, null, null, null);
    }

    /**
     * 工厂方法：创建带坐标的节点。
     */
    public static DagNode of(String jobKey, String jobId, String label, int x, int y) {
        return new DagNode(jobKey, jobId, label, x, y, null, DEFAULT_NODE_TYPE, null, null, null);
    }

    /**
     * 工厂方法：创建 TASK 节点（兼容旧调用，无新字段）。
     */
    public static DagNode of(String jobKey, String jobId, String label,
                             int x, int y, String paramsJson) {
        return new DagNode(jobKey, jobId, label, x, y, paramsJson, DEFAULT_NODE_TYPE, null, null, null);
    }

    /**
     * 工厂方法：创建 CONDITION 条件分支节点。
     */
    public static DagNode condition(String jobKey, String jobId, String label,
                                     String conditionExpression) {
        return new DagNode(jobKey, jobId, label, 0, 0, null,
                NodeType.CONDITION.name(), conditionExpression, null, null);
    }

    /**
     * 工厂方法：创建 LOOP 循环节点。
     */
    public static DagNode loop(String jobKey, String jobId, String label, int loopCount) {
        return new DagNode(jobKey, jobId, label, 0, 0, null,
                NodeType.LOOP.name(), null, loopCount, null);
    }

    /**
     * 工厂方法：创建 PARALLEL_GATEWAY 并行网关节点。
     */
    public static DagNode parallelGateway(String jobKey, String jobId, String label,
                                           int parallelBranches) {
        return new DagNode(jobKey, jobId, label, 0, 0, null,
                NodeType.PARALLEL_GATEWAY.name(), null, null, parallelBranches);
    }

    /**
     * P1-5: 工厂方法：创建 SUB_WORKFLOW 子工作流节点。
     *
     * @param jobKey            节点 KEY
     * @param label             显示名称
     * @param subWorkflowDagKey 子工作流 DAG KEY（必须存在于 ydsz_job_dag 表）
     * @return SUB_WORKFLOW 节点
     */
    public static DagNode subWorkflow(String jobKey, String label, String subWorkflowDagKey) {
        return new DagNode(jobKey, null, label, 0, 0, null,
                NodeType.SUB_WORKFLOW.name(), null, null, null,
                subWorkflowDagKey, null, null);
    }

    /**
     * P1-6: 工厂方法：创建 APPROVAL 审批节点。
     *
     * @param jobKey                   节点 KEY
     * @param label                    显示名称
     * @param approvalUsers            审批人列表（逗号分隔，如 "user1,user2"）
     * @param approvalTimeoutMinutes   审批超时时间（分钟，超时自动拒绝）
     * @return APPROVAL 节点
     */
    public static DagNode approval(String jobKey, String label,
                                    String approvalUsers, int approvalTimeoutMinutes) {
        return new DagNode(jobKey, null, label, 0, 0, null,
                NodeType.APPROVAL.name(), null, null, null,
                null, approvalUsers, approvalTimeoutMinutes);
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
     * <p>对标 DolphinScheduler 的条件分支 / 循环 / 并行网关能力，
     * 以及 Airflow 的子工作流和人工审批能力。
     */
    public enum NodeType {
        /** 普通任务节点：调用 handler 执行 */
        TASK,
        /** 条件分支节点：评估 conditionExpression 决定走哪条边 */
        CONDITION,
        /** 循环节点：重复执行下游节点 loopCount 次 */
        LOOP,
        /** 并行网关节点：使用 CompletableFuture 并行执行所有下游分支 */
        PARALLEL_GATEWAY,
        /** P1-5: 子工作流节点：嵌套触发另一个 DAG 工作流 */
        SUB_WORKFLOW,
        /** P1-6: 审批节点：等待人工审批后继续执行 */
        APPROVAL;

        /**
         * 安全解析节点类型字符串，无效值返回 {@link #TASK}。
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
