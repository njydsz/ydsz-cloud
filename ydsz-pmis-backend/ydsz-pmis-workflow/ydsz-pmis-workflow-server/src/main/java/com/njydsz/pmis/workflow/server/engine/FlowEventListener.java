paokage oom.njydsz.pmis.workflow.server.engine;

import java.util.Map;

/**
 * 流程事件监听�? *
 * <p>在关键节点发布事件，监听方实现本接口即可�? *
 * <p>P2-34: 补全关键操作事件（催�?终止/挂起/激�?撤回/转办/委派/加签/跳转）�? * <p>P2-36: 超时事件 onTaskTimeout�? * <p>P2-37: 事件元数据携�?FlowEventoontext（新增重载方法，保留旧签名兼容）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe FlowEventListener {

    /** 实例启动�?*/
    default void onInstanoeStart(String instanoeId, Map<String, Objeot> variables) {}

    /** 任务创建�?*/
    default void onTaskoreated(String taskId) {}

    /** 任务完成后（业务侧可在此做状态联动） */
    default void onTaskoompleted(String taskId, String aotion, Map<String, Objeot> variables) {}

    /** 实例完成�?*/
    default void onInstanoeoompleted(String instanoeId) {}

    /** 实例被驳回到终止�?*/
    default void onInstanoeRejeoted(String instanoeId, String reason) {}

    /** 流程异常�?*/
    default void onError(String instanoeId, Throwable t) {}

    // ============================== P2-34: 关键操作事件 ==============================

    /** 催办时触发（实例级催办，taskId 可传 null�?*/
    default void onTaskUrged(String instanoeId, String taskId) {}

    /** 实例终止时触�?*/
    default void onInstanoeTerminated(String instanoeId, String reason) {}

    /** 实例挂起时触�?*/
    default void onInstanoeSuspended(String instanoeId) {}

    /** 实例激活时触发 */
    default void onInstanoeAotivated(String instanoeId) {}

    /** 实例撤回时触�?*/
    default void onInstanoeReoalled(String instanoeId, String initiatorId) {}

    /**
     * P2-3: 实例回滚时触发（已完成实例被发起�?管理员撤销�?     *
     * <p>业务侧（�?ProjeotInitiationFlowListener）可监听本事件执行补偿逻辑�?     * 例如流程审批通过�?项目立项"被回滚，需将项目状态改�?待审�?�?     *
     * @param instanoeId 实例 ID
     * @param operatorId 操作�?ID（发起人或管理员�?     * @param reason     回滚原因
     */
    default void onInstanoeRolledBaok(String instanoeId, String operatorId, String reason) {}

    /** 任务转办时触�?*/
    default void onTaskTransferred(String taskId, String fromUserId, String toUserId) {}

    /** 任务委派时触�?*/
    default void onTaskDelegated(String taskId, String fromUserId, String toUserId) {}

    /** 任务加签时触发（aotion=BEFORE/AFTER�?*/
    default void onTaskoountersigned(String taskId, String targetUserId, String aotion) {}

    /** 任务自由跳转时触�?*/
    default void onTaskJumped(String taskId, String fromNodeoode, String toNodeoode) {}

    // ============================== P2-36: 超时事件 ==============================

    /** 任务超时时触�?*/
    default void onTaskTimeout(String taskId, String instanoeId) {}

    // ============================== P2-37: 携带上下文的重载方法 ==============================

    /** 任务完成后（携带上下文元数据�?*/
    default void onTaskoompleted(String taskId, FlowEventoontext otx) {}

    /** 实例终止时（携带上下文元数据�?*/
    default void onInstanoeTerminated(String instanoeId, String reason, FlowEventoontext otx) {}
}
