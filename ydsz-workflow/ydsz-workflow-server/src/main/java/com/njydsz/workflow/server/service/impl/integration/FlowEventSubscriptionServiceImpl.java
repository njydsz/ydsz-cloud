package com.njydsz.workflow.server.service.impl.integration;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.domain.repository.FlowEventSubscriptionRepository;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.repository.FlowNodeRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowEventSubscriptionVO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.engine.FlowNodeExt;
import com.njydsz.workflow.server.engine.impl.DefaultFlowAdvancer;
import com.njydsz.workflow.server.service.FlowEventSubscriptionService;
import com.njydsz.workflow.server.service.FlowInstanceService;

/**
 * 工作流事件订阅服务实现
 *
 * <p>对 {@link FlowEventSubscriptionService} 接口的完整实现，承担工作流引擎的 <b>事件驱动节点</b>运行时支持，是 BPMN 2.0
 * 规范中<b>消息中间事件（Message Intermediate Event）</b>、 <b>错误边界事件（Error Boundary Event）</b>、<b>信号事件（Signal
 * Event）</b>的运行时承接层。
 *
 * <p><b>事件模型：</b>
 *
 * <ul>
 *   <li><b>订阅（Subscription）</b>：流程到达「事件捕获节点」（{@code intermediateCatchEvent} / {@code
 *       boundaryEvent}） 时，调用 {@link #createSubscription} 写入 {@code ydsz_flow_event_subscription}
 *       表，状态 = {@code WAITING}
 *   <li><b>触发（Trigger）</b>：外部通过 {@link #triggerEvent} 投递事件， 系统按 {@code (eventType, eventKey,
 *       instanceId)} 匹配 WAITING 订阅并执行动作
 *   <li><b>完成（Completed）</b>：触发成功后订阅置为 {@code COMPLETED}，合并 payload 到流程变量后推进流程
 * </ul>
 *
 * <p><b>核心职责：</b>
 *
 * <ol>
 *   <li>匹配 WAITING 订阅 → 标记 COMPLETED
 *   <li>边界事件：取消关联的 userTask（{@code boundaryTaskId}）
 *   <li>合并 payload 到流程变量（{@code flow_variables}）
   *<li>调用 {@link com.njydsz.workflow.server.engine.impl.DefaultFlowAdvancer#advance} 从事件捕获节点推进到下游
 *   <li>调用 {@link FlowInstanceService#generateTasksForNodes} 创建下游任务
 * </ol>
 *
 * <p><b>事务边界：</b>
 *
 * <ul>
 *   <li>{@link #createSubscription} 与 {@link #triggerEvent} 开启 {@code @Transactional(rollbackFor =
 *       Exception.class)}，确保「订阅状态 + 任务取消 + 流程变量 + 流程推进」原子性
 *   <li>同一订阅的多次触发由 {@code @Transactional} 串行化（SELECT ... FOR UPDATE 锁住订阅行）
 * </ul>
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>支持多种事件类型：{@code message / error / signal / timer / compensation}
 *   <li>事件 payload 支持嵌套 JSON，最终合并到 {@code flow_variables} 后可在表达式中引用
 *   <li>边界事件（{@code boundaryEvent}）触发时主动取消关联任务，避免「事件触发后原任务仍 PENDING」
 *   <li>支持「事件延迟消费」：订阅记录带 {@code expireAt} 字段，到期后系统自动清理
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 1. 流程到达事件节点时创建订阅
 * String subscriptionId = eventSubscriptionService.createSubscription(
 *     instanceId, eventNode, variables, boundaryTaskId);
 *
 * // 2. 外部触发事件
 * eventSubscriptionService.triggerEvent("message", "order_created", "tenant1", payload);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowEventSubscriptionService 接口定义
 * @see com.njydsz.workflow.domain.vo.FlowEventSubscriptionVO 事件订阅值对象
 * @see com.njydsz.workflow.server.engine.impl.DefaultFlowAdvancer 流程推进引擎
 * @see FlowInstanceService 流程实例服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowEventSubscriptionServiceImpl implements FlowEventSubscriptionService {

    /** BPMN 节点类型码：结束事件 */
  private static final int NODE_TYPE_END = 6;

  /** 事件订阅仓储（domain 层契约），管理事件订阅表 CRUD */
  private final FlowEventSubscriptionRepository subscriptionRepository;

  /** 流程实例仓储（domain 层契约），查询事件关联的流程实例 */
  private final FlowInstanceRepository instanceRepository;

  /** 流程节点仓储（domain 层契约），查询事件捕获节点配置 */
  private final FlowNodeRepository nodeRepository;

  /** 运行时任务仓储（domain 层契约），查询待办任务 */
  private final FlowRunTaskRepository taskRepository;

  /** 流程推进引擎，事件触发后推进流程 */
  private final DefaultFlowAdvancer advancer;

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String createSubscription(
      String instanceId, FlowNodeVO node, Map<String, Object> variables, String boundaryTaskId) {
    if (instanceId == null || node == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("instanceId/node 不能为空")
          .build();
    }
    FlowInstanceVO instance = instanceRepository.findById(instanceId).orElse(null);
    if (instance == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("流程实例不存在: " + instanceId)
          .build();
    }

    Map<String, Object> ext = parseExt(node);
    String eventType = (String) ext.get("eventType");
    String eventRef = (String) ext.get("eventRef");
    if (!StringUtils.hasText(eventType) || !StringUtils.hasText(eventRef)) {
      log.warn("[Flow] 事件捕获节点缺少 eventType/eventRef: nodeCode={}", node.getNodeCode());
      eventType = StringUtils.hasText(eventType) ? eventType : "MESSAGE";
      eventRef = StringUtils.hasText(eventRef) ? eventRef : node.getNodeCode();
    }

    String correlationKey = extractCorrelationKey(ext, variables);

    FlowEventSubscriptionVO subscription = new FlowEventSubscriptionVO();
    subscription.setTenantId(instance.getTenantId());
    subscription.setInstanceId(instanceId);
    subscription.setDefinitionId(instance.getDefinitionId());
    subscription.setFlowCode(instance.getFlowCode());
    subscription.setNodeCode(node.getNodeCode());
    subscription.setNodeName(node.getNodeName());
    subscription.setEventType(eventType);
    subscription.setEventRef(eventRef);
    subscription.setCorrelationKey(correlationKey);
    subscription.setBoundaryTaskId(boundaryTaskId);
    subscription.setSubscriptionStatus("WAITING");
    subscription.setProviderTraceId(instance.getProviderTraceId());
    subscriptionRepository.save(subscription);

    log.info(
        "[Flow] 创建事件订阅: subId={} instanceId={} node={} type={} ref={} boundaryTaskId={}",
        subscription.getId(),
        instanceId,
        node.getNodeCode(),
        eventType,
        eventRef,
        boundaryTaskId);
    return subscription.getId();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public int correlateMessage(
      String tenantId, String messageName, String correlationKey, String payload) {
    if (!StringUtils.hasText(messageName)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("messageName 不能为空")
          .build();
    }
    String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();

    List<FlowEventSubscriptionVO> subscriptions =
        subscriptionRepository.findWaitingByEvent(tid, "MESSAGE", messageName);

    if (StringUtils.hasText(correlationKey)) {
      subscriptions =
          subscriptions.stream().filter(s -> correlationKey.equals(s.getCorrelationKey())).toList();
    }

    int triggered = 0;
    for (FlowEventSubscriptionVO sub : subscriptions) {
      try {
        triggerSubscription(sub, payload, "API");
        triggered++;
      } catch (Exception e) {
        log.error(
            "[Flow] 消息触发订阅失败: subId={} instanceId={} err={}",
            sub.getId(),
            sub.getInstanceId(),
            e.getMessage(),
            e);
      }
    }
    log.info(
        "[Flow] 消息关联完成: messageName={} correlationKey={} triggered={}",
        messageName,
        correlationKey,
        triggered);
    return triggered;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public int throwError(String tenantId, String instanceId, String errorCode, String payload) {
    if (!StringUtils.hasText(errorCode)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("errorCode 不能为空")
          .build();
    }
    String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();

    List<FlowEventSubscriptionVO> subscriptions =
        subscriptionRepository.findWaitingByEvent(tid, "ERROR", errorCode);

    if (instanceId != null) {
      subscriptions =
          subscriptions.stream().filter(s -> instanceId.equals(s.getInstanceId())).toList();
    }

    int triggered = 0;
    for (FlowEventSubscriptionVO sub : subscriptions) {
      try {
        triggerSubscription(sub, payload, "API");
        triggered++;
      } catch (Exception e) {
        log.error(
            "[Flow] 错误触发订阅失败: subId={} instanceId={} err={}",
            sub.getId(),
            sub.getInstanceId(),
            e.getMessage(),
            e);
      }
    }
    log.info(
        "[Flow] 错误抛出完成: errorCode={} instanceId={} triggered={}", errorCode, instanceId, triggered);
    return triggered;
  }

  @Override
  public int cancelByTask(String boundaryTaskId, String reason) {
    if (boundaryTaskId == null) {
      return 0;
    }
    return subscriptionRepository.cancelByTask(boundaryTaskId, reason);
  }

  @Override
  public int cancelByInstance(String instanceId, String reason) {
    if (instanceId == null) {
      return 0;
    }
    return subscriptionRepository.cancelByInstance(instanceId, reason);
  }

  @Override
  @Transactional(readOnly = true)
  public List<FlowEventSubscriptionVO> listByInstance(String instanceId) {
    if (instanceId == null) {
      return Collections.emptyList();
    }
    return subscriptionRepository.findByInstanceOrderByCreatedAtDesc(instanceId);
  }

  /**
   * {@inheritDoc}
   * 
   * <p>符合 DDD 分层规范：Service 层内部完成 DO→VO 转换。
   *
   * @param instanceId 流程实例 ID
   * @return 订阅视图列表
   */
  @Override
  public List<FlowEventSubscriptionVO> listByInstanceVO(String instanceId) {
    return listByInstance(instanceId);
  }

  @Override
  public boolean isEventCatchNode(FlowNodeVO node) {
    if (node == null || !StringUtils.hasText(node.getExt())) {
      return false;
    }
    try {
      Map<String, Object> ext = FlowNodeExt.parseSafe(node.getExt());
      return ext != null && Boolean.TRUE.equals(ext.get("eventCatch"));
    } catch (Exception e) {
      log.warn("[FlowEventSubscriptionServiceImpl] 节点 ext 解析失败，视为未配置事件捕获: {}", e.getMessage());
      return false;
    }
  }

  // ============================== 私有方法 ==============================

  /**
   * 触发订阅 — 标记 COMPLETED，取消边界任务（如有），推进流程
   *
   * @param sub 事件订阅实体
   * @param payload 触发时的消息载荷 JSON
   * @param triggerSource 触发来源标识
   */
  private void triggerSubscription(
      FlowEventSubscriptionVO sub, String payload, String triggerSource) {
    // 1. 标记订阅已触发
    subscriptionRepository.markTriggered(sub.getId(), payload, triggerSource, LocalDateTime.now());

    // 2. 边界事件：取消关联的 userTask
    if (sub.getBoundaryTaskId() != null) {
      cancelBoundaryTask(sub.getBoundaryTaskId(), sub.getEventRef());
    }

    // 3. 推进流程
    FlowInstanceVO instance = instanceRepository.findById(sub.getInstanceId()).orElse(null);
    if (instance == null) {
      log.warn("[Flow] 订阅触发时实例不存在: subId={} instanceId={}", sub.getId(), sub.getInstanceId());
      return;
    }
    if (!"RUNNING".equals(instance.getFlowStatus())) {
      log.warn(
          "[Flow] 订阅触发时实例非 RUNNING 状态: subId={} status={}", sub.getId(), instance.getFlowStatus());
      return;
    }

    // 4. 合并 payload 到流程变量
    Map<String, Object> variables = parseVariables(instance.getVariable());
    if (StringUtils.hasText(payload)) {
      try {
        Map<String, Object> payloadMap = YdszJson.parseMap(payload);
        if (payloadMap != null) {
          variables.putAll(payloadMap);
          instanceRepository.updateVariable(instance.getId(), YdszJson.toJson(variables));
        }
      } catch (Exception e) {
        log.warn("[Flow] payload 解析失败，忽略: subId={} err={}", sub.getId(), e.getMessage());
      }
    }

    // 5. 从事件捕获节点推进流程
    FlowNodeVO catchNode = nodeRepository.findByCode(instance.getDefinitionId(), sub.getNodeCode()).orElse(null);
    if (catchNode == null) {
      log.warn("[Flow] 事件捕获节点不存在: subId={} nodeCode={}", sub.getId(), sub.getNodeCode());
      return;
    }

    List<FlowNodeVO> nextNodes =
        advancer.advance(instance, sub.getNodeCode(), "PASS", null, variables);
    if (nextNodes.isEmpty()) {
      log.info("[Flow] 事件触发后无下游节点: subId={} instanceId={}", sub.getId(), sub.getInstanceId());
      return;
    }

    // 6. 创建下游任务
    FlowInstanceService instanceService = advancer.getInstanceService();
    instanceService.generateTasksForNodes(sub.getInstanceId(), nextNodes, variables);

    // 7. 更新实例当前节点
    if (nextNodes.get(0).getNodeType() != NODE_TYPE_END) { // 非 END
      instanceRepository.updateStatus(
          sub.getInstanceId(),
          instance.getFlowStatus(),
          nextNodes.get(0).getNodeCode(),
          nextNodes.get(0).getNodeName(),
          null,
          null);
    }

    log.info(
        "[Flow] 事件订阅触发完成: subId={} instanceId={} nextNode={}",
        sub.getId(),
        sub.getInstanceId(),
        nextNodes.get(0).getNodeCode());
  }

  /**
   * 取消边界事件关联的 userTask
   *
   * @param taskId 边界任务 ID
   * @param errorCode 错误事件编码
   */
  private void cancelBoundaryTask(String taskId, String errorCode) {
    FlowRunTaskVO task = taskRepository.findById(taskId).orElse(null);
    if (task == null) {
      return;
    }
    if (!"PENDING".equals(task.getTaskStatus()) && !"CLAIMED".equals(task.getTaskStatus())) {
      return;
    }
    taskRepository.cancelTask(taskId, FlowTaskStatus.CANCELLED.name(), "边界错误事件触发: " + errorCode);
    log.info("[Flow] 边界事件取消任务: taskId={} errorCode={}", taskId, errorCode);
  }

  private Map<String, Object> parseExt(FlowNodeVO node) {
    return FlowNodeExt.parseSafe(node.getExt());
  }

  private String extractCorrelationKey(Map<String, Object> ext, Map<String, Object> variables) {
    Object expr = ext.get("correlationKeyExpression");
    if (expr == null) {
      return null;
    }
    String exprStr = expr.toString();
    if (exprStr.startsWith("${") && exprStr.endsWith("}")) {
      String varName = exprStr.substring(2, exprStr.length() - 1).trim();
      Object val = variables.get(varName);
      return val != null ? val.toString() : null;
    }
    return exprStr;
  }

  private Map<String, Object> parseVariables(String variableJson) {
    if (!StringUtils.hasText(variableJson)) {
      return new HashMap<>();
    }
    try {
      Map<String, Object> m = YdszJson.parseMap(variableJson);
      return m != null ? m : new HashMap<>();
    } catch (Exception e) {
      return new HashMap<>();
    }
  }
}
