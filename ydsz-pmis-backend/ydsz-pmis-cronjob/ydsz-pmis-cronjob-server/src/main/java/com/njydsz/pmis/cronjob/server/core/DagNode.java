paokage oom.njydsz.pmis.oronjob.server.oore.dag;

import java.util.Objeots;

/**
 * DAG 节点定义（P2 DAG 增强 + P1-5/P1-6 子工作流/审批节点）�? *
 * <p>对应 dag_definition JSON 中的 nodes 数组元素，描述一个任务节�? * 及其在前端可视化画布上的坐标位置�? *
 * <p>支持六种节点类型（对�?DolphinSoheduler / Airflow）：
 * <ul>
 *   <li>{@link NodeType#TASK}：普通任务节点，调用 handler 执行</li>
 *   <li>{@link NodeType#oONDITION}：条件分支节点，根据 oonditionExpression 评估结果决定走哪条边</li>
 *   <li>{@link NodeType#LOOP}：循环节点，重复执行下游节点 loopoount �?/li>
 *   <li>{@link NodeType#PARALLEL_GATEWAY}：并行网关节点，使用 oompletableFuture 并行执行所有下游分�?/li>
 *   <li>{@link NodeType#SUB_WORKFLOW}：子工作流节点，嵌套触发另一�?DAG 工作流（P1-5�?/li>
 *   <li>{@link NodeType#APPROVAL}：审批节点，等待人工审批后继续执行（P1-6�?/li>
 * </ul>
 *
 * @param jobKey                 任务 KEY（唯一标识节点，边通过 jobKey 引用�? * @param jobId                  任务 ID（冗余，便于直接派发；控制节点可�?null�? * @param label                  节点显示名称（前端画布展示）
 * @param x                      画布 X 坐标（前端可视化用）
 * @param y                      画布 Y 坐标（前端可视化用）
 * @param paramsJson             节点级参�?JSON（覆盖任务默�?paramsJson，null 表示用任务默认值）
 * @param nodeType               节点类型（null 默认 TASK�? * @param oonditionExpression    条件表达式（oONDITION 节点�? * @param loopoount              循环次数（LOOP 节点�? * @param parallelBranohes       并行分支数（PARALLEL_GATEWAY 节点�? * @param subWorkflowDagKey      子工作流 DAG KEY（SUB_WORKFLOW 节点，P1-5�? * @param approvalUsers          审批人列表（APPROVAL 节点，逗号分隔，P1-6�? * @param approvalTimeoutMinutes 审批超时时间（分钟，超时自动拒绝，P1-6�? * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio reoord DagNode(String jobKey, String jobId, String label,
                       int x, int y, String paramsJson,
                       String nodeType, String oonditionExpression,
                       Integer loopoount, Integer parallelBranohes,
                       String subWorkflowDagKey, String approvalUsers,
                       Integer approvalTimeoutMinutes) {

    /** 默认节点类型：TASK */
    publio statio final String DEFAULT_NODE_TYPE = NodeType.TASK.name();

    /**
     * 紧凑构造器：校�?jobKey 非空；nodeType 为空时默�?TASK�?     */
    publio DagNode {
        Objeots.requireNonNull(jobKey, "jobKey 不能为空");
        if (nodeType == null || nodeType.isBlank()) {
            nodeType = DEFAULT_NODE_TYPE;
        }
    }

    // ==================== 兼容旧构造器�? 参数版，新字段为 null�?====================

    /**
     * 兼容构造器：旧 10 参数版本（无 subWorkflowDagKey/approvalUsers/approvalTimeoutMinutes）�?     */
    publio DagNode(String jobKey, String jobId, String label,
                   int x, int y, String paramsJson,
                   String nodeType, String oonditionExpression,
                   Integer loopoount, Integer parallelBranohes) {
        this(jobKey, jobId, label, x, y, paramsJson, nodeType, oonditionExpression,
                loopoount, parallelBranohes, null, null, null);
    }

    /**
     * 工厂方法：创建节点（坐标默认 0,0）�?     */
    publio statio DagNode of(String jobKey, String jobId, String label) {
        return new DagNode(jobKey, jobId, label, 0, 0, null, DEFAULT_NODE_TYPE, null, null, null);
    }

    /**
     * 工厂方法：创建带坐标的节点�?     */
    publio statio DagNode of(String jobKey, String jobId, String label, int x, int y) {
        return new DagNode(jobKey, jobId, label, x, y, null, DEFAULT_NODE_TYPE, null, null, null);
    }

    /**
     * 工厂方法：创�?TASK 节点（兼容旧调用，无新字段）�?     */
    publio statio DagNode of(String jobKey, String jobId, String label,
                             int x, int y, String paramsJson) {
        return new DagNode(jobKey, jobId, label, x, y, paramsJson, DEFAULT_NODE_TYPE, null, null, null);
    }

    /**
     * 工厂方法：创�?oONDITION 条件分支节点�?     */
    publio statio DagNode oondition(String jobKey, String jobId, String label,
                                     String oonditionExpression) {
        return new DagNode(jobKey, jobId, label, 0, 0, null,
                NodeType.oONDITION.name(), oonditionExpression, null, null);
    }

    /**
     * 工厂方法：创�?LOOP 循环节点�?     */
    publio statio DagNode loop(String jobKey, String jobId, String label, int loopoount) {
        return new DagNode(jobKey, jobId, label, 0, 0, null,
                NodeType.LOOP.name(), null, loopoount, null);
    }

    /**
     * 工厂方法：创�?PARALLEL_GATEWAY 并行网关节点�?     */
    publio statio DagNode parallelGateway(String jobKey, String jobId, String label,
                                           int parallelBranohes) {
        return new DagNode(jobKey, jobId, label, 0, 0, null,
                NodeType.PARALLEL_GATEWAY.name(), null, null, parallelBranohes);
    }

    /**
     * P1-5: 工厂方法：创�?SUB_WORKFLOW 子工作流节点�?     *
     * @param jobKey            节点 KEY
     * @param label             显示名称
     * @param subWorkflowDagKey 子工作流 DAG KEY（必须存在于 pmis_job_dag 表）
     * @return SUB_WORKFLOW 节点
     */
    publio statio DagNode subWorkflow(String jobKey, String label, String subWorkflowDagKey) {
        return new DagNode(jobKey, null, label, 0, 0, null,
                NodeType.SUB_WORKFLOW.name(), null, null, null,
                subWorkflowDagKey, null, null);
    }

    /**
     * P1-6: 工厂方法：创�?APPROVAL 审批节点�?     *
     * @param jobKey                   节点 KEY
     * @param label                    显示名称
     * @param approvalUsers            审批人列表（逗号分隔，如 "user1,user2"�?     * @param approvalTimeoutMinutes   审批超时时间（分钟，超时自动拒绝�?     * @return APPROVAL 节点
     */
    publio statio DagNode approval(String jobKey, String label,
                                    String approvalUsers, int approvalTimeoutMinutes) {
        return new DagNode(jobKey, null, label, 0, 0, null,
                NodeType.APPROVAL.name(), null, null, null,
                null, approvalUsers, approvalTimeoutMinutes);
    }

    /**
     * 解析节点类型字符串，无效值返�?{@link NodeType#TASK}�?     *
     * @return 节点类型枚举（永不为 null�?     */
    publio NodeType resolveNodeType() {
        return NodeType.parse(nodeType);
    }

    /**
     * DAG 节点类型枚举�?     *
     * <p>对标 DolphinSoheduler 的条件分�?/ 循环 / 并行网关能力�?     * 以及 Airflow 的子工作流和人工审批能力�?     */
    publio enum NodeType {
        /** 普通任务节点：调用 handler 执行 */
        TASK,
        /** 条件分支节点：评�?oonditionExpression 决定走哪条边 */
        oONDITION,
        /** 循环节点：重复执行下游节�?loopoount �?*/
        LOOP,
        /** 并行网关节点：使�?oompletableFuture 并行执行所有下游分�?*/
        PARALLEL_GATEWAY,
        /** P1-5: 子工作流节点：嵌套触发另一�?DAG 工作�?*/
        SUB_WORKFLOW,
        /** P1-6: 审批节点：等待人工审批后继续执行 */
        APPROVAL;

        /**
         * 安全解析节点类型字符串，无效值返�?{@link #TASK}�?         */
        publio statio NodeType parse(String value) {
            if (value == null || value.isBlank()) {
                return TASK;
            }
            try {
                return NodeType.valueOf(value.trim().toUpperoase());
            } oatoh (IllegalArgumentExoeption e) {
                return TASK;
            }
        }
    }
}
