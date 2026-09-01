package com.njydsz.workflow.server.engine;

import java.util.Map;

/**
 * 流程事件监听器
 *
 * <p>在关键节点发布事件，监听方实现本接口即可。
 *
 * <p>P2-34: 补全关键操作事件（催办/终止/挂起/激活/撤回/转办/委派/加签/跳转）。
 *
 * <p>P2-36: 超时事件 onTaskTimeout。
 *
 * <p>P2-37: 事件元数据携带 FlowEventContext（新增重载方法，保留旧签名兼容）。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
public interface FlowEventListener {

  /**
   * 实例启动前。
   *
   * @param instanceId 实例 ID
   * @param variables 流程变量
   */
  default void onInstanceStart(String instanceId, Map<String, Object> variables) {}

  /**
   * 任务创建后。
   *
   * @param taskId 任务 ID
   */
  default void onTaskCreated(String taskId) {}

  /**
   * 任务完成后（业务侧可在此做状态联动）。
   *
   * @param taskId 任务 ID
   * @param action 完成动作
   * @param variables 流程变量
   */
  default void onTaskCompleted(String taskId, String action, Map<String, Object> variables) {}

  /**
   * 实例完成时。
   *
   * @param instanceId 实例 ID
   */
  default void onInstanceCompleted(String instanceId) {}

  /**
   * 实例被驳回到终止时。
   *
   * @param instanceId 实例 ID
   * @param reason 驳回原因
   */
  default void onInstanceRejected(String instanceId, String reason) {}

  /**
   * 流程异常时。
   *
   * @param instanceId 实例 ID
   * @param t 异常
   */
  default void onError(String instanceId, Throwable t) {}

  // ============================== P2-34: 关键操作事件 ==============================

  /**
   * 催办时触发（实例级催办，taskId 可传 null）。
   *
   * @param instanceId 实例 ID
   * @param taskId 任务 ID（可为 null）
   */
  default void onTaskUrged(String instanceId, String taskId) {}

  /**
   * 实例终止时触发。
   *
   * @param instanceId 实例 ID
   * @param reason 终止原因
   */
  default void onInstanceTerminated(String instanceId, String reason) {}

  /**
   * 实例挂起时触发。
   *
   * @param instanceId 实例 ID
   */
  default void onInstanceSuspended(String instanceId) {}

  /**
   * 实例激活时触发。
   *
   * @param instanceId 实例 ID
   */
  default void onInstanceActivated(String instanceId) {}

  /**
   * 实例撤回时触发。
   *
   * @param instanceId 实例 ID
   * @param initiatorId 发起人 ID
   */
  default void onInstanceRecalled(String instanceId, String initiatorId) {}

  /**
   * P2-3: 实例回滚时触发（已完成实例被发起人/管理员撤销）
   *
   * <p>业务侧（如 ProjectInitiationFlowListener）可监听本事件执行补偿逻辑： 例如流程审批通过的"项目立项"被回滚，需将项目状态改回"待审批"。
   *
   * @param instanceId 实例 ID
   * @param operatorId 操作人 ID（发起人或管理员）
   * @param reason 回滚原因
   */
  default void onInstanceRolledBack(String instanceId, String operatorId, String reason) {}

  /**
   * P3-1: 实例重审时触发（已完成实例被重新打开并回填到指定节点）
   *
   * <p>业务侧可监听本事件执行补偿逻辑：例如流程审批通过后又被重审，需将相关业务状态调整为"审批中"。
   *
   * @param instanceId    实例 ID
   * @param operatorId    操作人 ID（发起人或管理员）
   * @param targetNodeCode 重审目标节点编码
   * @param reason        重审原因
   */
  default void onInstanceReopened(
      String instanceId, String operatorId, String targetNodeCode, String reason) {}

  /**
   * 任务转办时触发
   *
   * @param taskId 被转办的任务 ID
   * @param fromUserId 原办理人 ID
   * @param toUserId 目标办理人 ID
   */
  default void onTaskTransferred(String taskId, String fromUserId, String toUserId) {}

  /**
   * 任务委派时触发
   *
   * @param taskId 被委派任务 ID
   * @param fromUserId 原办理人 ID
   * @param toUserId 代理人 ID
   */
  default void onTaskDelegated(String taskId, String fromUserId, String toUserId) {}

  /**
   * 任务加签时触发（action=BEFORE/AFTER）
   *
   * @param taskId 被加签的任务 ID
   * @param targetUserId 加签目标人 ID
   * @param action 加签类型（BEFORE/AFTER/PARALLEL）
   */
  default void onTaskCountersigned(String taskId, String targetUserId, String action) {}

  /**
   * 任务自由跳转时触发
   *
   * @param taskId 跳转的任务 ID
   * @param fromNodeCode 源节点编码
   * @param toNodeCode 目标节点编码
   */
  default void onTaskJumped(String taskId, String fromNodeCode, String toNodeCode) {}

  // ============================== P2-36: 超时事件 ==============================

  /**
   * 任务超时时触发
   *
   * @param taskId 超时任务 ID
   * @param instanceId 所属实例 ID
   */
  default void onTaskTimeout(String taskId, String instanceId) {}

  // ============================== P2-37: 携带上下文的重载方法 ==============================

  /**
   * 任务完成后（携带上下文元数据）
   *
   * @param taskId 任务 ID
   * @param ctx 事件上下文元数据
   */
  default void onTaskCompleted(String taskId, FlowEventContext ctx) {}

  /**
   * 实例终止时（携带上下文元数据）
   *
   * @param instanceId 实例 ID
   * @param reason 终止原因
   * @param ctx 事件上下文元数据
   */
  default void onInstanceTerminated(String instanceId, String reason, FlowEventContext ctx) {}

  // ============================== P2-38: 会签个人完成事件 ==============================

  /**
   * 会签中单个办理人完成审批时触发（携带上下文元数据）
   *
   * <p>仅在会签场景（PARALLEL / 多人审批）下，某个办理人完成审批时触发， 早于 {@link #onTaskCompleted} 触发（后者需等全部会签人完成才触发）。
   * 业务方可通过此事件实时跟踪会签进度。
   *
   * @param taskId    当前办理人的任务 ID
   * @param ctx       事件上下文，其中 {@code operatorId} 为当前办理人，{@code action} 为操作类型，
   *                  {@code approveFinished} 和 {@code approveCount} 为会签进度
   */
  default void onTaskPersonalCompleted(String taskId, FlowEventContext ctx) {}
}
