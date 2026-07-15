package com.njydsz.pmis.workflow.server.engine.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.core.response.BaseResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.lock.annotation.YdszDistributedLock;
import com.njydsz.pmis.common.json.Json;
import com.njydsz.pmis.workflow.domain.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.domain.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.domain.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.domain.entity.FlowSkipDO;
import com.njydsz.pmis.workflow.domain.enums.FlowNodeType;
import com.njydsz.pmis.workflow.infra.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.server.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.server.engine.FlowDefinitionCacheService;
import com.njydsz.pmis.workflow.server.engine.FlowVariableStrategy;
import com.njydsz.pmis.workflow.server.service.FlowDmnDecisionService;
import com.njydsz.pmis.workflow.server.service.FlowInstanceService;
import com.njydsz.pmis.workflow.server.service.FlowJoinTokenService;
import com.njydsz.pmis.workflow.server.service.FlowRoutingService;
import com.njydsz.pmis.workflow.server.service.FlowTaskService;
import com.njydsz.pmis.workflow.server.service.impl.instance.FlowInstanceServiceImpl;

import lombok.extern.slf4j.Slf4j;

/**
 * 流程推进器默认实现
 *
 * <p>P0 修复：排他网关互斥（CONDITION 只取第一条匹配）、并行网关 join 聚合。
 *
 * @since 1.0.0
 */
@Slf4j
@Component
public class DefaultFlowAdvancer implements FlowAdvancer {

    /** P1: 流程定义元数据缓存（节点 + skip），替代直查 nodeMapper/skipMapper */
    private final FlowDefinitionCacheService flowDefinitionCacheService;
    private final FlowInstanceMapper instanceMapper;
    private final FlowTaskService taskService;
    private final FlowInstanceService instanceService;
    private final FlowVariableStrategy variableStrategy;
    private final FlowRunTaskMapper taskMapper;
    /** GAP-P2: 并行网关 join 令牌服务（精确跟踪分支到达状态） */
    private final FlowJoinTokenService joinTokenService;

    /**
     * 智能路由服务（可选注入，literule 不可用时为 null）
     *
     * <p>当 ydsz-pmis-literule 模块在 classpath 中且 RuleEngine/ExpressionEvaluator Bean 存在时，
     * Spring 会自动注入 FlowRoutingService；否则本字段为 null，回退到 variableStrategy。
     */
    private final FlowRoutingService routingService;

    /** P0-1: DMN 决策表服务（可选注入，未启用时为 null） */
    private final FlowDmnDecisionService dmnDecisionService;

    public DefaultFlowAdvancer(FlowDefinitionCacheService flowDefinitionCacheService,
                                FlowInstanceMapper instanceMapper,
                                FlowTaskService taskService,
                                FlowInstanceService instanceService,
                                FlowVariableStrategy variableStrategy,
                                FlowRunTaskMapper taskMapper,
                                FlowJoinTokenService joinTokenService,
                                @Autowired(required = false) FlowRoutingService routingService,
                                @Autowired(required = false) FlowDmnDecisionService dmnDecisionService) {
        this.flowDefinitionCacheService = flowDefinitionCacheService;
        this.instanceMapper = instanceMapper;
        this.taskService = taskService;
        this.instanceService = instanceService;
        this.variableStrategy = variableStrategy;
        this.taskMapper = taskMapper;
        this.joinTokenService = joinTokenService;
        this.routingService = routingService;
        this.dmnDecisionService = dmnDecisionService;
    }

    @Override
    public FlowInstanceService getInstanceService() {
        return instanceService;
    }

    @Override
    @YdszDistributedLock(key = "'flow:instance:op:' + #{#instanceId}", waitTime = 5, leaseTime = 60,
            message = "流程正在处理中，请稍后重试")
    public FlowInstanceViewDTO start(String instanceId) {
        FlowInstanceDO instance = instanceService.getById(instanceId);
        if (instance == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "error.workflow.msg_67a10717", instanceId);
        }
        FlowNodeDO startNode = flowDefinitionCacheService.getStartNode(instance.getDefinitionId());
        if (startNode == null) {
            throw new SysException(BaseResultCode.INTERNAL_ERROR,
                    "error.workflow.msg_560bf118", instance.getDefinitionId());
        }
        List<FlowNodeDO> nextNodes = advance(instance, startNode.getNodeCode(),
                "PASS", null, parseVariable(instance.getVariable()));
        if (nextNodes.isEmpty()) {
            log.info("[Flow] 流程无下游节点，自动完成: instanceId={}", instanceId);
            instanceService.complete(instanceId, startNode.getNodeCode());
            return instanceService.toView(instanceService.getById(instanceId),
                    loadCurrentTasks(instanceId));
        }
        FlowInstanceServiceImpl impl = null;
        if (instanceService instanceof FlowInstanceServiceImpl) {
            impl = (FlowInstanceServiceImpl) instanceService;
        }
        if (impl != null) {
            impl.generateTasksForNodes(instanceId, nextNodes, parseVariable(instance.getVariable()));
        }
        if (nextNodes.get(0).getNodeType() != FlowNodeType.END.getCode()) {
            instanceMapper.updateStatus(instanceId,
                    instance.getFlowStatus(),
                    nextNodes.get(0).getNodeCode(),
                    nextNodes.get(0).getNodeName(),
                    null, null);
        }
        return instanceService.toView(instanceService.getById(instanceId),
                loadCurrentTasks(instanceId));
    }

    @Override
    @YdszDistributedLock(key = "'flow:instance:op:' + #{#currentInstance.id}", waitTime = 5, leaseTime = 60,
            message = "流程正在处理中，请稍后重试")
    public List<FlowNodeDO> advance(FlowInstanceDO currentInstance,
                                     String currentNodeCode,
                                     String skipType,
                                     String targetNodeCode,
                                     Map<String, Object> variables) {
        FlowNodeDO currentNode = flowDefinitionCacheService.getNodeByCode(
                currentInstance.getDefinitionId(), currentNodeCode);
        if (currentNode == null) {
            throw new SysException(BaseResultCode.NOT_FOUND,
                    "error.workflow.msg_d84d389b", currentNodeCode);
        }

        // REJECT 退回
        if ("REJECT".equalsIgnoreCase(skipType)) {
            String rejectTarget = targetNodeCode != null
                    ? targetNodeCode
                    : resolveRejectTarget(currentInstance.getDefinitionId(), currentNodeCode);
            if (rejectTarget == null) {
                throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_241f4a79");
            }
            FlowNodeDO target = flowDefinitionCacheService.getNodeByCode(
                    currentInstance.getDefinitionId(), rejectTarget);
            if (target == null) {
                throw new SysException(BaseResultCode.NOT_FOUND, "error.workflow.msg_6e66716d", rejectTarget);
            }
            return List.of(target);
        }

        // PASS 推进
        List<FlowSkipDO> skips = resolvePassSkips(currentInstance, currentNode, variables);
        if (skips.isEmpty()) {
            log.info("[Flow] 流程无下一节点，结束: instanceId={} nodeCode={}",
                    currentInstance.getId(), currentNodeCode);
            return Collections.emptyList();
        }

        List<FlowNodeDO> nextNodes = new ArrayList<>();
        for (FlowSkipDO skip : skips) {
            FlowNodeDO next = flowDefinitionCacheService.getNodeByCode(
                    currentInstance.getDefinitionId(), skip.getNextNodeCode());
            if (next == null) {
                log.warn("[Flow] 跳转目标节点不存在: skipId={} nextNode={}",
                        skip.getId(), skip.getNextNodeCode());
                continue;
            }
            // P0-5 / GAP-P2 / P0-3: 网关 join 聚合 — 支持 N/M join 策略
            if (isJoinNode(next) && hasMultipleIncoming(currentInstance.getDefinitionId(), next.getNodeCode())) {
                int incomingCount = flowDefinitionCacheService.getSkipsByNextNode(
                        currentInstance.getDefinitionId(), next.getNodeCode()).size();
                String instId = currentInstance.getId();
                String joinCode = next.getNodeCode();
                try {
                    // P0-3: 解析节点 ext 中的 joinRequired 配置
                    int requiredCount = parseJoinRequired(next, incomingCount);
                    // 懒初始化：首次到达时初始化令牌
                    if (!joinTokenService.isInitialized(instId, joinCode)) {
                        if (requiredCount < incomingCount) {
                            // N/M join: 部分分支到达即可聚合
                            joinTokenService.initTokensWithRequired(
                                    instId, joinCode, incomingCount, requiredCount);
                            log.info("[Flow] P0-3 N/M join 初始化: instanceId={} node={} total={} required={}",
                                    instId, joinCode, incomingCount, requiredCount);
                        } else {
                            joinTokenService.initTokens(instId, joinCode, incomingCount);
                        }
                    }
                    // 标记本次到达
                    boolean canJoin;
                    if (requiredCount < incomingCount) {
                        canJoin = joinTokenService.arriveTokenWithRequired(instId, joinCode);
                    } else {
                        canJoin = joinTokenService.arriveToken(instId, joinCode);
                    }
                    if (canJoin) {
                        joinTokenService.clearTokens(instId, joinCode);
                        log.info("[Flow] 并行网关 join 聚合通过: instanceId={} node={} required={}/{}",
                                instId, joinCode, requiredCount, incomingCount);
                    } else {
                        log.info("[Flow] 并行网关 join 等待: instanceId={} node={} required={}/{}",
                                instId, joinCode, requiredCount, incomingCount);
                        continue; // 等待其他分支
                    }
                } catch (Exception e) {
                    // Redis 异常降级：回退到 countPendingByNode 逻辑
                    log.warn("[Flow] join 令牌异常，降级到 countPending: instanceId={} node={} err={}",
                            instId, joinCode, e.getMessage());
                    // P0-2: 降级路径增强 — 统计所有入边源节点的活跃任务数，
                    // 避免某分支已终止（CANCELLED/FAILED）但 join 永久等待。
                    // 只统计 PENDING/CLAIMED 状态任务，已终止分支不计入。
                    int activeIncoming = countActiveIncomingTasks(
                            currentInstance.getDefinitionId(), instId, joinCode);
                    if (activeIncoming > 0) {
                        log.info("[Flow] 并行网关 join 等待（降级）: instanceId={} node={} activeIncoming={}",
                                instId, joinCode, activeIncoming);
                        continue;
                    }
                }
            }
            nextNodes.add(next);
        }
        return nextNodes;
    }

    /**
     * GAP-P0-2: 退回多节点同退
     *
     * <p>对标飞书"退回多节点同退"。当 skipType=REJECT 且 targetNodeCodes 非空时，
     * 在所有指定节点同时创建待办任务，让多个前序节点重新审批。
     * 单节点退回（targetNodeCodes 为空或单元素）降级到原 advance 逻辑。
     */
    @Override
    @YdszDistributedLock(key = "'flow:instance:op:' + #{#currentInstance.id}", waitTime = 5, leaseTime = 60,
            message = "流程正在处理中，请稍后重试")
    public List<FlowNodeDO> advanceMulti(FlowInstanceDO currentInstance,
                                          String currentNodeCode,
                                          String skipType,
                                          List<String> targetNodeCodes,
                                          Map<String, Object> variables) {
        // 非 REJECT 或多节点列表为空：降级到单节点 advance
        if (!"REJECT".equalsIgnoreCase(skipType)
                || targetNodeCodes == null || targetNodeCodes.isEmpty()) {
            String single = (targetNodeCodes == null || targetNodeCodes.isEmpty())
                    ? null : targetNodeCodes.get(0);
            return advance(currentInstance, currentNodeCode, skipType, single, variables);
        }

        // 单元素：降级到单节点 advance（保持原有语义）
        if (targetNodeCodes.size() == 1) {
            return advance(currentInstance, currentNodeCode, skipType,
                    targetNodeCodes.get(0), variables);
        }

        // GAP-P0-2: 多节点同退 — 校验所有目标节点存在，返回全部目标节点列表
        log.info("[Flow] 退回多节点同退: instanceId={} currentNode={} targets={}",
                currentInstance.getId(), currentNodeCode, targetNodeCodes);
        List<FlowNodeDO> targets = new ArrayList<>();
        for (String nodeCode : targetNodeCodes) {
            if (nodeCode == null || nodeCode.isBlank()) {
                continue;
            }
            FlowNodeDO target = flowDefinitionCacheService.getNodeByCode(
                    currentInstance.getDefinitionId(), nodeCode);
            if (target == null) {
                throw new SysException(BaseResultCode.NOT_FOUND,
                        "error.workflow.msg_6e66716d" + nodeCode);
            }
            // 避免重复
            if (targets.stream().noneMatch(t -> t.getNodeCode().equals(nodeCode))) {
                targets.add(target);
            }
        }
        if (targets.isEmpty()) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_241f4a79");
        }
        return targets;
    }

    @Override
    public List<FlowSkipDO> resolvePassSkips(FlowInstanceDO instance,
                                              FlowNodeDO currentNode,
                                              Map<String, Object> variables) {
        // P1: 通过缓存获取当前节点的出发跳转，并在内存中按 skipType=PASS 过滤
        List<FlowSkipDO> all = flowDefinitionCacheService.getSkipsByNodeCode(
                instance.getDefinitionId(), currentNode.getNodeCode()).stream()
                .filter(s -> "PASS".equalsIgnoreCase(s.getSkipType()))
                .toList();
        if (all.isEmpty()) {
            return Collections.emptyList();
        }

        // P0-6: 排他网关互斥 — CONDITION 节点只取第一条匹配
        boolean isExclusive = currentNode.getNodeType() != null
                && currentNode.getNodeType() == FlowNodeType.CONDITION.getCode();
        // GAP-P0: 包容网关 — INCLUSIVE 节点取所有匹配，无匹配时取默认出边
        boolean isInclusive = currentNode.getNodeType() != null
                && currentNode.getNodeType() == FlowNodeType.INCLUSIVE.getCode();

        List<FlowSkipDO> matched = new ArrayList<>();
        for (FlowSkipDO skip : all) {
            String cond = skip.getSkipCondition();
            if (evaluateSkipCondition(cond, variables)) {
                matched.add(skip);
                if (isExclusive) {
                    break; // 排他网关：只取第一条匹配
                }
                // 包容网关：不 break，继续收集所有匹配分支
            }
        }

        // P0-2: 排他/包容网关默认出边 — BPMN 2.0 规范：取 gateway.default 属性指向的
        // sequenceFlow，而非盲目取 all.get(0)，避免设计器边排序不确定导致走错分支。
        // 兜底链：default 属性 → 无条件出边 → 空列表（流程结束）
        if ((isExclusive || isInclusive) && matched.isEmpty()) {
            FlowSkipDO defaultSkip = resolveDefaultSkip(currentNode, all);
            if (defaultSkip != null) {
                log.info("[Flow] {}无匹配条件，取 BPMN default 出边: node={} defaultFlowId={}",
                        isExclusive ? "排他网关" : "包容网关",
                        currentNode.getNodeCode(), extractSequenceFlowId(defaultSkip));
                matched.add(defaultSkip);
            } else {
                // 未配置 default 属性时，取无条件的出边（BPMN 规范：default 边本身不能有 conditionExpression）
                FlowSkipDO fallback = all.stream()
                        .filter(s -> s.getSkipCondition() == null || s.getSkipCondition().isBlank())
                        .findFirst().orElse(null);
                if (fallback != null) {
                    log.info("[Flow] {}无匹配条件且未配置 default，取无条件出边: node={}",
                            isExclusive ? "排他网关" : "包容网关", currentNode.getNodeCode());
                    matched.add(fallback);
                }
                // 若连无条件边也没有，matched 保持空，advance 返回空列表，流程结束
            }
        }

        return matched;
    }

    /**
     * 评估跳转条件表达式
     *
     * <p>评估优先级：
     * <ol>
     *   <li>P0-1: DMN 决策表（condition 以 {@code dmn:} 前缀时，如 {@code dmn:risk_level_decision}）</li>
     *   <li>FlowRoutingService（literule Aviator 引擎）</li>
     *   <li>DefaultFlowVariableStrategy（SpEL）兜底</li>
     * </ol>
     *
     * @param condition 跳转条件表达式
     * @param variables 流程变量
     * @return true=条件成立，false=不成立
     */
    @Override
    public boolean evaluateSkipCondition(String condition, Map<String, Object> variables) {
        if (condition == null || condition.isBlank()) {
            return true;
        }

        // P0-1: DMN 决策表评估（condition 以 "dmn:" 前缀标识）
        if (condition.startsWith("dmn:") && dmnDecisionService != null) {
            String decisionCode = condition.substring(4).trim();
            try {
                // 从变量中提取租户 ID
                String tenantId = "1";
                if (variables != null && variables.get("_tenantId") != null) {
                    tenantId = String.valueOf(variables.get("_tenantId"));
                }
                Map<String, Object> output = dmnDecisionService.evaluate(decisionCode, variables, tenantId);
                boolean result = output != null && !output.isEmpty();
                log.debug("[Flow] 使用 DMN 决策表评估条件: decision={} -> {} output={}",
                        decisionCode, result, output);
                return result;
            } catch (Exception e) {
                log.warn("[Flow] DMN 决策表评估失败，回退到 routingService: decision={} err={}",
                        decisionCode, e.getMessage());
            }
        }

        // 优先使用 literule FlowRoutingService 评估
        if (routingService != null) {
            try {
                boolean result = routingService.evaluateCondition(condition, variables);
                log.debug("[Flow] 使用 FlowRoutingService 评估条件: expr={} -> {}", condition, result);
                return result;
            } catch (Exception e) {
                log.warn("[Flow] FlowRoutingService 评估失败，回退到 variableStrategy: expr={} err={}",
                        condition, e.getMessage());
            }
        }

        // 回退到原有 SpEL 变量策略
        return variableStrategy.evaluate(condition, variables);
    }

    @Override
    public String resolveRejectTarget(String definitionId, String currentNodeCode) {
        List<FlowSkipDO> incoming = flowDefinitionCacheService.getSkipsByNextNode(definitionId, currentNodeCode);
        if (!incoming.isEmpty()) {
            return incoming.get(0).getSkipName() == null
                    ? currentNodeCode
                    : lookupNodeCodeByName(definitionId, incoming.get(0).getSkipName());
        }
        FlowNodeDO start = flowDefinitionCacheService.getStartNode(definitionId);
        return start == null ? null : start.getNodeCode();
    }

    // ============================== 私有 ==============================

    /** 判断是否为 join 节点（并行/包容网关） */
    private boolean isJoinNode(FlowNodeDO node) {
        return node.getNodeType() != null
                && (node.getNodeType() == FlowNodeType.PARALLEL.getCode()
                || node.getNodeType() == FlowNodeType.INCLUSIVE.getCode());
    }

    /**
     * P0-2: 解析网关默认出边
     *
     * <p>BPMN 2.0 规范：exclusiveGateway / inclusiveGateway 的 {@code default} 属性
     * 指向一条无条件的 sequenceFlow。当所有带条件的出边都不匹配时，走这条默认边。
     *
     * @param gatewayNode 网关节点（ext 中可能含 defaultFlowId）
     * @param allSkips 网关的所有 PASS 出边
     * @return 默认出边，未配置或未找到时返回 null
     */
    private FlowSkipDO resolveDefaultSkip(FlowNodeDO gatewayNode, List<FlowSkipDO> allSkips) {
        if (gatewayNode.getExt() == null || gatewayNode.getExt().isBlank()) {
            return null;
        }
        try {
            Map<String, Object> nodeExt = Json.parseMap(gatewayNode.getExt());
            if (nodeExt == null) {
                return null;
            }
            Object defaultFlowId = nodeExt.get("defaultFlowId");
            if (defaultFlowId == null) {
                return null;
            }
            String defaultId = String.valueOf(defaultFlowId);
            for (FlowSkipDO skip : allSkips) {
                String seqFlowId = extractSequenceFlowId(skip);
                if (defaultId.equals(seqFlowId)) {
                    return skip;
                }
            }
        } catch (Exception e) {
            log.warn("[Flow] P0-2 解析默认出边失败: node={} err={}",
                    gatewayNode.getNodeCode(), e.getMessage());
        }
        return null;
    }

    /**
     * P0-2: 从 FlowSkipDO.ext JSON 中提取 sequenceFlowId
     *
     * @param skip 跳转边
     * @return sequenceFlowId，不存在时返回 null
     */
    private String extractSequenceFlowId(FlowSkipDO skip) {
        if (skip.getExt() == null || skip.getExt().isBlank()) {
            return null;
        }
        try {
            Map<String, Object> ext = Json.parseMap(skip.getExt());
            if (ext == null) {
                return null;
            }
            Object val = ext.get("sequenceFlowId");
            return val == null ? null : String.valueOf(val);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * P0-2: 从 FlowSkipDO.ext JSON 中提取 sourceRef（入边源节点编码）
     *
     * @param skip 跳转边
     * @return 源节点编码，不存在时返回 null
     */
    private String extractSourceNodeCode(FlowSkipDO skip) {
        if (skip.getExt() == null || skip.getExt().isBlank()) {
            return null;
        }
        try {
            Map<String, Object> ext = Json.parseMap(skip.getExt());
            if (ext == null) {
                return null;
            }
            Object val = ext.get("sourceRef");
            return val == null ? null : String.valueOf(val);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * P0-2: 统计 join 节点的入边源节点中仍有活跃任务的数量（降级路径使用）
     *
     * <p>当 Redis 令牌服务异常时，通过查询任务表判断其他分支是否仍在处理中。
     * 只统计 PENDING 状态的任务（已 CANCELLED/COMPLETED/FAILED 的不计入），
     * 避免某分支已终止但 join 永久等待。
     *
     * @param definitionId 流程定义 ID
     * @param instanceId 流程实例 ID
     * @param joinNodeCode join 节点编码
     * @return 仍有活跃任务的入边源节点数量
     */
    private int countActiveIncomingTasks(String definitionId, String instanceId, String joinNodeCode) {
        List<FlowSkipDO> incoming = flowDefinitionCacheService.getSkipsByNextNode(definitionId, joinNodeCode);
        if (incoming == null || incoming.isEmpty()) {
            return 0;
        }
        int active = 0;
        for (FlowSkipDO skip : incoming) {
            String sourceNodeCode = extractSourceNodeCode(skip);
            if (sourceNodeCode == null || sourceNodeCode.isBlank()) {
                continue;
            }
            int nodePending = taskMapper.countPendingByNode(instanceId, sourceNodeCode);
            active += nodePending;
        }
        return active;
    }

    /** 判断节点是否有多个入边 */
    private boolean hasMultipleIncoming(String definitionId, String nodeCode) {
        List<FlowSkipDO> incoming = flowDefinitionCacheService.getSkipsByNextNode(definitionId, nodeCode);
        return incoming != null && incoming.size() > 1;
    }

    /**
     * P0-3: 解析节点 ext 中的 joinRequired 配置
     *
     * <p>支持格式：
     * <ul>
     *   <li>{@code "joinRequired": 3} — 数值，表示需要 3 个分支到达</li>
     *   <li>{@code "joinRequired": "3/5"} — 分数，表示 5 个分支中 3 个到达</li>
     *   <li>{@code "joinRequired": "majority"} — 过半数</li>
     *   <li>未配置 — 返回 incomingCount（默认全部到达）</li>
     * </ul>
     */
    private int parseJoinRequired(FlowNodeDO node, int incomingCount) {
        if (node.getExt() == null || node.getExt().isBlank()) {
            return incomingCount;
        }
        try {
            Map<String, Object> ext = Json.parseMap(node.getExt());
            if (ext == null) {
                return incomingCount;
            }
            Object val = ext.get("joinRequired");
            if (val == null) {
                return incomingCount;
            }
            if (val instanceof Number n) {
                int required = n.intValue();
                return Math.min(Math.max(1, required), incomingCount);
            }
            String s = String.valueOf(val).trim();
            if ("majority".equalsIgnoreCase(s)) {
                return incomingCount / 2 + 1;
            }
            if (s.contains("/")) {
                String[] parts = s.split("/");
                int required = Integer.parseInt(parts[0].trim());
                return Math.min(Math.max(1, required), incomingCount);
            }
            return Math.min(Math.max(1, Integer.parseInt(s)), incomingCount);
        } catch (Exception e) {
            log.warn("[Flow] P0-3 解析 joinRequired 失败: node={} ext={} err={}",
                    node.getNodeCode(), node.getExt(), e.getMessage());
            return incomingCount;
        }
    }

    private String lookupNodeCodeByName(String definitionId, String skipName) {
        List<FlowNodeDO> all = flowDefinitionCacheService.getAllNodes(definitionId);
        return all.stream()
                .filter(n -> skipName.equals(n.getNodeName()))
                .map(FlowNodeDO::getNodeCode)
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> parseVariable(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return Json.parseMap(json);
        } catch (Exception e) {
            log.warn("[Flow] 变量解析失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private List<FlowInstanceViewDTO.FlowTaskViewDTO> loadCurrentTasks(String instanceId) {
        return taskService.listPendingByInstance(instanceId).stream()
                .map(taskService::toView)
                .toList();
    }
}
