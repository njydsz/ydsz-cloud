package com.njydsz.workflow.server.engine.listener;

import java.util.Map;

import com.njydsz.workflow.server.engine.FlowEventContext;

/**
 * 流程监听器插件 SPI
 *
 * <p>业务方实现此接口并注册为 Spring Bean，即可在设计器中按节点绑定为监听器。
 * 引擎在对应生命周期事件触发时，按优先级依次回调所有已绑定的插件。
 *
 * <p><b>命名规范：</b>Bean 名称即为插件名称，如 {@code @Component("notifyListenerPlugin")}
 *
 * <p><b>生命周期回调：</b>只需覆写关心的方法，其余走默认空实现。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FlowListenerPluginExecutor 插件执行器
 * @see FlowListenerConfig 设计器中的配置绑定
 */
public interface FlowListenerPlugin {

  /**
   * 任务创建后回调
   *
   * @param instanceId 流程实例 ID
   * @param taskId     任务 ID
   * @param nodeCode   节点编码
   * @param variables  流程变量
   * @param ctx        事件上下文（可空）
   */
  default void onTaskCreated(String instanceId, String taskId, String nodeCode,
      Map<String, Object> variables, FlowEventContext ctx) {}

  /**
   * 任务开始办理时回调（分派到具体审批人）
   *
   * @param instanceId  流程实例 ID
   * @param taskId      任务 ID
   * @param nodeCode    节点编码
   * @param assigneeId  办理人 ID
   * @param variables   流程变量
   * @param ctx         事件上下文
   */
  default void onTaskStarted(String instanceId, String taskId, String nodeCode,
      String assigneeId, Map<String, Object> variables, FlowEventContext ctx) {}

  /**
   * 任务完成时回调（通过 / 驳回 / 自动通过）
   *
   * @param instanceId 流程实例 ID
   * @param taskId     任务 ID
   * @param nodeCode   节点编码
   * @param action     操作类型（PASS / REJECT / AUTO_PASS）
   * @param variables  流程变量
   * @param ctx        事件上下文
   */
  default void onTaskFinished(String instanceId, String taskId, String nodeCode,
      String action, Map<String, Object> variables, FlowEventContext ctx) {}

  /**
   * 实例启动时回调
   *
   * @param instanceId 流程实例 ID
   * @param variables  流程变量
   * @param ctx        事件上下文
   */
  default void onInstanceStarted(String instanceId, Map<String, Object> variables, FlowEventContext ctx) {}

  /**
   * 实例完成时回调（所有审批通过、结束节点触发）
   *
   * @param instanceId 流程实例 ID
   * @param ctx        事件上下文
   */
  default void onInstanceFinished(String instanceId, FlowEventContext ctx) {}

  /**
   * 实例拒绝时回调（驳回到终止）
   *
   * @param instanceId 流程实例 ID
   * @param reason     拒绝原因
   * @param ctx        事件上下文
   */
  default void onInstanceRejected(String instanceId, String reason, FlowEventContext ctx) {}

  /**
   * 实例终止时回调
   *
   * @param instanceId 流程实例 ID
   * @param reason     终止原因
   * @param ctx        事件上下文
   */
  default void onInstanceTerminated(String instanceId, String reason, FlowEventContext ctx) {}

  /**
   * 会签中单个办理人完成审批时回调
   *
   * <p>仅在会签场景（PARALLEL / 多人审批）下，某个办理人点击「通过」但 会签尚未全部完成时触发。业务方可通过此事件实时跟踪
   * 会签进度（如展示"3/5 人已通过"），无需等全部会签完成再感知。
   *
   * @param instanceId    流程实例 ID
   * @param taskId        当前办理人的任务 ID（运行时任务，非归档任务）
   * @param nodeCode      节点编码
   * @param personalUserId 当前办理人用户 ID
   * @param action        操作类型（PASS / REJECT）
   * @param approveFinished 当前已通过人数（含本次）
   * @param approveCount  会签总人数
   * @param variables     流程变量
   * @param ctx           事件上下文
   */
  default void onTaskPersonalFinished(String instanceId, String taskId, String nodeCode,
      String personalUserId, String action, int approveFinished, int approveCount,
      Map<String, Object> variables, FlowEventContext ctx) {}
}
