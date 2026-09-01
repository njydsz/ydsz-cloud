package com.njydsz.workflow.server.service;

import java.util.List;
import java.util.Map;

import com.njydsz.workflow.domain.vo.FlowEventSubscriptionVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;

/**
 * 流程事件订阅服务。
 *
 * <p>外部系统订阅流程实例/任务事件。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowEventSubscriptionService {

  /**
   * 创建事件订阅（流程到达事件捕获节点时调用）
   *
   * @param instanceId 实例 ID
   * @param node 事件捕获节点
   * @param variables 流程变量
   * @param boundaryTaskId 边界事件关联的 userTask ID（中间事件传 null）
   * @return 订阅 ID
   */
  String createSubscription(
      String instanceId, FlowNodeVO node, Map<String, Object> variables, String boundaryTaskId);

  /**
   * 关联消息 — 匹配 WAITING 的 MESSAGE 订阅并触发
   *
   * @param tenantId 租户 ID
   * @param messageName 消息名称（对应 BPMN messageRef）
   * @param correlationKey 关联键（业务标识，可空）
   * @param payload 消息载荷 JSON
   * @return 触发的订阅数量
   */
  int correlateMessage(String tenantId, String messageName, String correlationKey, String payload);

  /**
   * 抛出错误 — 匹配 WAITING 的 ERROR 订阅并触发
   *
   * @param tenantId 租户 ID
   * @param instanceId 实例 ID（可空，为空则按 errorCode 全局匹配）
   * @param errorCode 错误代码（对应 BPMN errorRef）
   * @param payload 错误载荷 JSON
   * @return 触发的订阅数量
   */
  int throwError(String tenantId, String instanceId, String errorCode, String payload);

  /**
   * 取消某 userTask 关联的所有边界事件订阅（userTask 完成时调用）
   *
   * @param boundaryTaskId 边界事件关联的任务 ID
   * @param reason 取消原因
   * @return 取消的订阅数量
   */
  int cancelByTask(String boundaryTaskId, String reason);

  /**
   * 取消某实例所有 WAITING 订阅（实例终止/驳回时调用）
   *
   * @param instanceId 流程实例 ID
   * @param reason 取消原因
   * @return 取消的订阅数量
   */
  int cancelByInstance(String instanceId, String reason);

  /**
   * 查询实例的事件订阅列表（返回 DO，供 Service 层内部使用）
   *
   * @param instanceId 流程实例 ID
   * @return 事件订阅 VO 列表
   */
  List<FlowEventSubscriptionVO> listByInstance(String instanceId);

  /**
   * 查询实例的事件订阅列表（返回 VO，符合 DDD 分层规范）
   *
   * @param instanceId 实例 ID
   * @return 事件订阅 VO 列表
   * @since 1.0.0
   */
  List<FlowEventSubscriptionVO> listByInstanceVO(String instanceId);

  /**
   * 判断节点是否为事件捕获节点（ext JSON 中包含 eventCatch: true）
   *
   * @param node 流程节点 VO
   * @return true 表示该节点为事件捕获节点（ext 中含 eventCatch: true）
   */
  boolean isEventCatchNode(FlowNodeVO node);
}
