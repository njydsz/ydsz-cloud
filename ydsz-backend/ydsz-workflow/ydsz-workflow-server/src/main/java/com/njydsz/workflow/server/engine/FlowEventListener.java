package com.njydsz.workflow.server.engine;

import java.util.Map;

/**
 * 流程事件监听器
 *
 * <p>在关键节点发布事件，监听方实现本接口即可。
 *
 * <p>P2-34: 补全关键操作事件（催办/终止/挂起/激活/撤回/转办/委派/加签/跳转）。
 * <p>P2-36: 超时事件 onTaskTimeout。
 * <p>P2-37: 事件元数据携带 FlowEventContext（新增重载方法，保留旧签名兼容）。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public interface FlowEventListener {

    /** 实例启动前 */
    default void onInstanceStart(String instanceId, Map<String, Object> variables) {}

    /** 任务创建后 */
    default void onTaskCreated(String taskId) {}

    /** 任务完成后（业务侧可在此做状态联动） */
    default void onTaskCompleted(String taskId, String action, Map<String, Object> variables) {}

    /** 实例完成时 */
    default void onInstanceCompleted(String instanceId) {}

    /** 实例被驳回到终止时 */
    default void onInstanceRejected(String instanceId, String reason) {}

    /** 流程异常时 */
    default void onError(String instanceId, Throwable t) {}

    // ============================== P2-34: 关键操作事件 ==============================

    /** 催办时触发（实例级催办，taskId 可传 null） */
    default void onTaskUrged(String instanceId, String taskId) {}

    /** 实例终止时触发 */
    default void onInstanceTerminated(String instanceId, String reason) {}

    /** 实例挂起时触发 */
    default void onInstanceSuspended(String instanceId) {}

    /** 实例激活时触发 */
    default void onInstanceActivated(String instanceId) {}

    /** 实例撤回时触发 */
    default void onInstanceRecalled(String instanceId, String initiatorId) {}

    /**
     * P2-3: 实例回滚时触发（已完成实例被发起人/管理员撤销）
     *
     * <p>业务侧（如 ProjectInitiationFlowListener）可监听本事件执行补偿逻辑：
     * 例如流程审批通过的"项目立项"被回滚，需将项目状态改回"待审批"。
     *
     * @param instanceId 实例 ID
     * @param operatorId 操作人 ID（发起人或管理员）
     * @param reason     回滚原因
     */
    default void onInstanceRolledBack(String instanceId, String operatorId, String reason) {}

    /** 任务转办时触发 */
    default void onTaskTransferred(String taskId, String fromUserId, String toUserId) {}

    /** 任务委派时触发 */
    default void onTaskDelegated(String taskId, String fromUserId, String toUserId) {}

    /** 任务加签时触发（action=BEFORE/AFTER） */
    default void onTaskCountersigned(String taskId, String targetUserId, String action) {}

    /** 任务自由跳转时触发 */
    default void onTaskJumped(String taskId, String fromNodeCode, String toNodeCode) {}

    // ============================== P2-36: 超时事件 ==============================

    /** 任务超时时触发 */
    default void onTaskTimeout(String taskId, String instanceId) {}

    // ============================== P2-37: 携带上下文的重载方法 ==============================

    /** 任务完成后（携带上下文元数据） */
    default void onTaskCompleted(String taskId, FlowEventContext ctx) {}

    /** 实例终止时（携带上下文元数据） */
    default void onInstanceTerminated(String instanceId, String reason, FlowEventContext ctx) {}
}
