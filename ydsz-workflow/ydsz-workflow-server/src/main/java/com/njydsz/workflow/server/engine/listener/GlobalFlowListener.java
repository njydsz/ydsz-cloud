package com.njydsz.workflow.server.engine.listener;

import java.util.Map;

import com.njydsz.workflow.server.engine.FlowEventContext;

/**
 * 全局流程监听器 SPI。
 *
 * <p>与 {@link FlowListenerPlugin}（节点级监听器）的区别：
 *
 * <ul>
 *   <li><b>节点级监听器</b>：绑定到特定节点，仅该节点事件触发时回调
 *   <li><b>全局监听器</b>：绑定到整个引擎，<b>所有</b>流程实例的生命周期事件均触发
 * </ul>
 *
 * <p><b>典型使用场景：</b>
 *
 * <ul>
 *   <li>全局审计日志：记录所有流程操作到外部审计系统
 *   <li>全局通知：所有流程状态变更时推送通知到消息中心
 *   <li>全局数据同步：流程状态变更时同步到业务系统
 *   <li>全局监控指标：统计流程发起/完成率
 * </ul>
 *
 * <p><b>实现方式：</b>业务方实现此接口并注册为 Spring Bean，引擎自动检测并注册。
 * 多个全局监听器按 {@link #getOrder()} 顺序执行。
 *
 * <p><b>注意事项：</b>
 *
 * <ul>
 *   <li>全局监听器应保持轻量，避免阻塞主流程</li>
 *   <li>耗时操作应异步执行（如使用 {@code @Async} 或消息队列）</li>
 *   <li>监听器异常不会中断主流程，仅记录错误日志</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see FlowListenerPlugin 节点级监听器
 * @see GlobalFlowListenerExecutor 全局监听器执行器
 */
public interface GlobalFlowListener {

  /**
   * 获取监听器执行顺序（升序）。
   *
   * <p>数值越小越先执行。建议间隔取值（如 10、20、30），便于后续插入自定义监听器。
   *
   * @return 执行顺序
   */
  default int getOrder() {
    return 100;
  }

  /**
   * 任务创建后全局回调。
   *
   * <p>所有流程实例的任务创建时触发，无论任务属于哪个节点。
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
   * 任务完成时全局回调（通过 / 驳回 / 自动通过）。
   *
   * <p>所有流程实例的任务完成时触发。
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
   * 实例启动时全局回调。
   *
   * <p>所有流程实例启动时触发。
   *
   * @param instanceId 流程实例 ID
   * @param variables  流程变量
   * @param ctx        事件上下文
   */
  default void onInstanceStarted(String instanceId, Map<String, Object> variables,
      FlowEventContext ctx) {}

  /**
   * 实例完成时全局回调（所有审批通过、结束节点触发）。
   *
   * <p>所有流程实例完成时触发。
   *
   * @param instanceId 流程实例 ID
   * @param ctx        事件上下文
   */
  default void onInstanceFinished(String instanceId, FlowEventContext ctx) {}

  /**
   * 实例拒绝时全局回调（驳回到终止）。
   *
   * @param instanceId 流程实例 ID
   * @param reason     拒绝原因
   * @param ctx        事件上下文
   */
  default void onInstanceRejected(String instanceId, String reason, FlowEventContext ctx) {}

  /**
   * 实例终止时全局回调。
   *
   * @param instanceId 流程实例 ID
   * @param reason     终止原因
   * @param ctx        事件上下文
   */
  default void onInstanceTerminated(String instanceId, String reason, FlowEventContext ctx) {}
}
