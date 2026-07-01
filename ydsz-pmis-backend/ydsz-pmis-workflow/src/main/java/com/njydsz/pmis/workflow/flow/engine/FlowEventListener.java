package com.njydsz.pmis.workflow.flow.engine;

/**
 * 流程事件监听器
 *
 * <p>在关键节点发布事件，监听方实现本接口即可。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface FlowEventListener {

    /** 实例启动前 */
    default void onInstanceStart(Long instanceId, java.util.Map<String, Object> variables) {}

    /** 任务创建后 */
    default void onTaskCreated(Long taskId) {}

    /** 任务完成后（业务侧可在此做状态联动） */
    default void onTaskCompleted(Long taskId, String action, java.util.Map<String, Object> variables) {}

    /** 实例完成时 */
    default void onInstanceCompleted(Long instanceId) {}

    /** 实例被驳回到终止时 */
    default void onInstanceRejected(Long instanceId, String reason) {}

    /** 流程异常时 */
    default void onError(Long instanceId, Throwable t) {}
}
