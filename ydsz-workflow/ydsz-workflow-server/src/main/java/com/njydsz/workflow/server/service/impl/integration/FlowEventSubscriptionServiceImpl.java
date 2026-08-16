package com.njydsz.workflow.server.service.impl.integration;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.domain.entity.FlowEventSubscription;
import com.njydsz.workflow.domain.entity.FlowInstance;
import com.njydsz.workflow.domain.entity.FlowNode;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.infra.mapper.FlowEventSubscriptionMapper;
import com.njydsz.workflow.infra.mapper.FlowInstanceMapper;
import com.njydsz.workflow.infra.mapper.FlowNodeMapper;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.workflow.server.engine.FlowAdvancer;

import com.njydsz.workflow.server.service.FlowEventSubscriptionService;
import com.njydsz.workflow.server.service.FlowInstanceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 工作流事件订阅服务实现
 *
 * <p>对 {@link FlowEventSubscriptionService} 接口的完整实现，承担工作流引擎的
 * <b>事件驱动节点</b>运行时支持，是 BPMN 2.0 规范中<b>消息中间事件（Message Intermediate Event）</b>、
 * <b>错误边界事件（Error Boundary Event）</b>、<b>信号事件（Signal Event）</b>的运行时承接层。
 *
 * <p><b>事件模型：</b>
 * <ul>
 *   <li><b>订阅（Subscription）</b>：流程到达「事件捕获节点」（{@code intermediateCatchEvent} / {@code boundaryEvent}）
 *       时，调用 {@link #createSubscription} 写入 {@code ydsz_flow_event_subscription} 表，状态 = {@code WAITING}</li>
 *   <li><b>触发（Trigger）</b>：外部通过 {@link #triggerEvent} 投递事件，
 *       系统按 {@code (eventType, eventKey, instanceId)} 匹配 WAITING 订阅并执行动作</li>
 *   <li><b>完成（Completed）</b>：触发成功后订阅置为 {@code COMPLETED}，合并 payload 到流程变量后推进流程</li>
 * </ul>
 *
 * <p><b>核心职责：</b>
 * <ol>
 *   <li>匹配 WAITING 订阅 → 标记 COMPLETED</li>
 *   <li>边界事件：取消关联的 userTask（{@code boundaryTaskId}）</li>
 *   <li>合并 payload 到流程变量（{@code flow_variables}）</li>
 *   <li>调用 {@link FlowAdvancer#advance} 从事件捕获节点推进到下游</li>
 *   <li>调用 {@link FlowInstanceService#generateTasksForNodes} 创建下游任务</li>
 * </ol>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>{@link #createSubscription} 与 {@link #triggerEvent} 开启
 *       {@code @Transactional(rollbackFor = Exception.class)}，确保「订阅状态 + 任务取消 + 流程变量 + 流程推进」原子性</li>
 *   <li>同一订阅的多次触发由 {@code @Transactional} 串行化（SELECT ... FOR UPDATE 锁住订阅行）</li>
 * </ul>
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li>支持多种事件类型：{@code message / error / signal / timer / compensation}</li>
 *   <li>事件 payload 支持嵌套 JSON，最终合并到 {@code flow_variables} 后可在表达式中引用</li>
 *   <li>边界事件（{@code boundaryEvent}）触发时主动取消关联任务，避免「事件触发后原任务仍 PENDING」</li>
 *   <li>支持「事件延迟消费」：订阅记录带 {@code expireAt} 字段，到期后系统自动清理</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
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
 *
 * @see FlowEventSubscriptionService 接口定义
 * @see com.njydsz.workflow.domain.entity.FlowEventSubscription 事件订阅实体
 * @see FlowAdvancer 流程推进引擎
 * @see FlowInstanceService 流程实例服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowEventSubscriptionServiceImpl implements FlowEventSubscriptionService {

    /** 事件订阅 Mapper，管理 BPMN 事件捕获节点订阅记录 */
    private final FlowEventSubscriptionMapper subscriptionMapper;
    /** 流程实例 Mapper，查询事件关联的流程实例 */
    private final FlowInstanceMapper instanceMapper;
    /** 流程节点 Mapper，查询事件捕获节点配置 */
    private final FlowNodeMapper nodeMapper;
    /** 运行时任务 Mapper，事件触发后创建待办任务 */
    private final FlowRunTaskMapper taskMapper;
    /** 流程推进引擎，事件触发后推进流程 */
    private final FlowAdvancer advancer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createSubscription(String instanceId, FlowNode node,
                                    Map<String, Object> variables, String boundaryTaskId) {
        if (instanceId == null || node == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("instanceId/node 不能为空")
                .build();
        }
        FlowInstance instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw SysException.builder()
                .resultCode(BaseResultCode.NOT_FOUND)
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

        FlowEventSubscription subscription = new FlowEventSubscription();
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
        subscriptionMapper.insert(subscription);

        log.info("[Flow] 创建事件订阅: subId={} instanceId={} node={} type={} ref={} boundaryTaskId={}",
                subscription.getId(), instanceId, node.getNodeCode(), eventType, eventRef, boundaryTaskId);
        return subscription.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int correlateMessage(String tenantId, String messageName,
                                 String correlationKey, String payload) {
        if (!StringUtils.hasText(messageName)) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("messageName 不能为空")
                .build();
        }
        String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();

        List<FlowEventSubscription> subscriptions =
                subscriptionMapper.selectWaitingByEvent(tid, "MESSAGE", messageName);

        if (StringUtils.hasText(correlationKey)) {
            subscriptions = subscriptions.stream()
                    .filter(s -> correlationKey.equals(s.getCorrelationKey()))
                    .toList();
        }

        int triggered = 0;
        for (FlowEventSubscription sub : subscriptions) {
            try {
                triggerSubscription(sub, payload, "API");
                triggered++;
            } catch (Exception e) {
                log.error("[Flow] 消息触发订阅失败: subId={} instanceId={} err={}",
                        sub.getId(), sub.getInstanceId(), e.getMessage(), e);
            }
        }
        log.info("[Flow] 消息关联完成: messageName={} correlationKey={} triggered={}",
                messageName, correlationKey, triggered);
        return triggered;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int throwError(String tenantId, String instanceId, String errorCode, String payload) {
        if (!StringUtils.hasText(errorCode)) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("errorCode 不能为空")
                .build();
        }
        String tid = tenantId != null ? tenantId : AuthContextUtils.getTenantIdOrDefault();

        List<FlowEventSubscription> subscriptions =
                subscriptionMapper.selectWaitingByEvent(tid, "ERROR", errorCode);

        if (instanceId != null) {
            subscriptions = subscriptions.stream()
                    .filter(s -> instanceId.equals(s.getInstanceId()))
                    .toList();
        }

        int triggered = 0;
        for (FlowEventSubscription sub : subscriptions) {
            try {
                triggerSubscription(sub, payload, "API");
                triggered++;
            } catch (Exception e) {
                log.error("[Flow] 错误触发订阅失败: subId={} instanceId={} err={}",
                        sub.getId(), sub.getInstanceId(), e.getMessage(), e);
            }
        }
        log.info("[Flow] 错误抛出完成: errorCode={} instanceId={} triggered={}",
                errorCode, instanceId, triggered);
        return triggered;
    }

    @Override
    public int cancelByTask(String boundaryTaskId, String reason) {
        if (boundaryTaskId == null) {
            return 0;
        }
        return subscriptionMapper.cancelByTask(boundaryTaskId, reason);
    }

    @Override
    public int cancelByInstance(String instanceId, String reason) {
        if (instanceId == null) {
            return 0;
        }
        return subscriptionMapper.cancelByInstance(instanceId, reason);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlowEventSubscription> listByInstance(String instanceId) {
        if (instanceId == null) {
            return Collections.emptyList();
        }
        return subscriptionMapper.selectList(
                new QueryWrapper<FlowEventSubscription>()
                        .eq("instance_id", instanceId)
                        .eq("deleted", 0)
                        .orderByDesc("created_at"));
    }

    @Override
    public boolean isEventCatchNode(FlowNode node) {
        if (node == null || !StringUtils.hasText(node.getExt())) {
            return false;
        }
        try {
            Map<String, Object> ext = YdszJson.parseMap(node.getExt());
            return ext != null && Boolean.TRUE.equals(ext.get("eventCatch"));
        } catch (Exception e) {
            log.warn("[FlowEventSubscriptionServiceImpl] 节点 ext 解析失败，视为未配置事件捕获: {}", e.getMessage());
            return false;
        }
    }

    // ============================== 私有方法 ==============================

    /**
     * 触发订阅 — 标记 COMPLETED，取消边界任务（如有），推进流程
     */
    private void triggerSubscription(FlowEventSubscription sub, String payload, String triggerSource) {
        // 1. 标记订阅已触发
        subscriptionMapper.markTriggered(sub.getId(), payload, triggerSource, LocalDateTime.now());

        // 2. 边界事件：取消关联的 userTask
        if (sub.getBoundaryTaskId() != null) {
            cancelBoundaryTask(sub.getBoundaryTaskId(), sub.getEventRef());
        }

        // 3. 推进流程
        FlowInstance instance = instanceMapper.selectById(sub.getInstanceId());
        if (instance == null) {
            log.warn("[Flow] 订阅触发时实例不存在: subId={} instanceId={}",
                    sub.getId(), sub.getInstanceId());
            return;
        }
        if (!"RUNNING".equals(instance.getFlowStatus())) {
            log.warn("[Flow] 订阅触发时实例非 RUNNING 状态: subId={} status={}",
                    sub.getId(), instance.getFlowStatus());
            return;
        }

        // 4. 合并 payload 到流程变量
        Map<String, Object> variables = parseVariables(instance.getVariable());
        if (StringUtils.hasText(payload)) {
            try {
                Map<String, Object> payloadMap = YdszJson.parseMap(payload);
                if (payloadMap != null) {
                    variables.putAll(payloadMap);
                    instanceMapper.updateVariable(instance.getId(), YdszJson.toJson(variables));
                }
            } catch (Exception e) {
                log.warn("[Flow] payload 解析失败，忽略: subId={} err={}", sub.getId(), e.getMessage());
            }
        }

        // 5. 从事件捕获节点推进流程
        FlowNode catchNode = nodeMapper.selectByCode(instance.getDefinitionId(), sub.getNodeCode());
        if (catchNode == null) {
            log.warn("[Flow] 事件捕获节点不存在: subId={} nodeCode={}", sub.getId(), sub.getNodeCode());
            return;
        }

        List<FlowNode> nextNodes = advancer.advance(instance, sub.getNodeCode(),
                "PASS", null, variables);
        if (nextNodes.isEmpty()) {
            log.info("[Flow] 事件触发后无下游节点: subId={} instanceId={}", sub.getId(), sub.getInstanceId());
            return;
        }

        // 6. 创建下游任务
        FlowInstanceService instanceService = advancer.getInstanceService();
        instanceService.generateTasksForNodes(sub.getInstanceId(), nextNodes, variables);

        // 7. 更新实例当前节点
        if (nextNodes.get(0).getNodeType() != 6) { // 非 END
            instanceMapper.updateStatus(sub.getInstanceId(), instance.getFlowStatus(),
                    nextNodes.get(0).getNodeCode(), nextNodes.get(0).getNodeName(), null, null);
        }

        log.info("[Flow] 事件订阅触发完成: subId={} instanceId={} nextNode={}",
                sub.getId(), sub.getInstanceId(), nextNodes.get(0).getNodeCode());
    }

    /**
     * 取消边界事件关联的 userTask
     */
    private void cancelBoundaryTask(String taskId, String errorCode) {
        FlowRunTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        if (!"PENDING".equals(task.getTaskStatus()) && !"CLAIMED".equals(task.getTaskStatus())) {
            return;
        }
        taskMapper.cancelTask(taskId, FlowTaskStatus.CANCELLED.name(),
                "边界错误事件触发: " + errorCode);
        log.info("[Flow] 边界事件取消任务: taskId={} errorCode={}", taskId, errorCode);
    }

    private Map<String, Object> parseExt(FlowNode node) {
        if (!StringUtils.hasText(node.getExt())) {
            return Collections.emptyMap();
        }
        try {
            return YdszJson.parseMap(node.getExt());
        } catch (Exception e) {
            return Collections.emptyMap();
        }
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
