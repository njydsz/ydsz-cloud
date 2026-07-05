package com.njydsz.pmis.workflow.engine.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.util.JsonUtils;
import com.njydsz.pmis.workflow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.engine.FlowDefinitionCacheService;
import com.njydsz.pmis.workflow.engine.FlowVariableStrategy;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.entity.FlowSkipDO;
import com.njydsz.pmis.workflow.enums.FlowNodeType;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.mapper.FlowTaskMapper;
import com.njydsz.pmis.workflow.service.FlowInstanceService;
import com.njydsz.pmis.workflow.service.FlowJoinTokenService;
import com.njydsz.pmis.workflow.service.impl.FlowInstanceServiceImpl;
import com.njydsz.pmis.workflow.service.FlowRoutingService;
import com.njydsz.pmis.workflow.service.FlowTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 流程推进器默认实现
 *
 * <p>P0 修复：排他网关互斥（CONDITION 只取第一条匹配）、并行网关 join 聚合。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
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
    private final FlowTaskMapper taskMapper;
    /** GAP-P2: 并行网关 join 令牌服务（精确跟踪分支到达状态） */
    private final FlowJoinTokenService joinTokenService;

    /**
     * 智能路由服务（可选注入，literule 不可用时为 null）
     *
     * <p>当 ydsz-pmis-literule 模块在 classpath 中且 RuleEngine/ExpressionEvaluator Bean 存在时，
     * Spring 会自动注入 FlowRoutingService；否则本字段为 null，回退到 variableStrategy。
     */
    private final FlowRoutingService routingService;

    public DefaultFlowAdvancer(FlowDefinitionCacheService flowDefinitionCacheService,
                                FlowInstanceMapper instanceMapper,
                                FlowTaskService taskService,
                                FlowInstanceService instanceService,
                                FlowVariableStrategy variableStrategy,
                                FlowTaskMapper taskMapper,
                                FlowJoinTokenService joinTokenService,
                                @Autowired(required = false) FlowRoutingService routingService) {
        this.flowDefinitionCacheService = flowDefinitionCacheService;
        this.instanceMapper = instanceMapper;
        this.taskService = taskService;
        this.instanceService = instanceService;
        this.variableStrategy = variableStrategy;
        this.taskMapper = taskMapper;
        this.joinTokenService = joinTokenService;
        this.routingService = routingService;
    }

    @Override
    public FlowInstanceService getInstanceService() {
        return instanceService;
    }

    @Override
    public FlowInstanceViewDTO start(Long instanceId) {
        FlowInstanceDO instance = instanceService.getById(instanceId);
        if (instance == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.workflow.msg_67a10717", instanceId);
        }
        FlowNodeDO startNode = flowDefinitionCacheService.getStartNode(instance.getDefinitionId());
        if (startNode == null) {
            throw new BizException(BizErrorCode.INTERNAL_ERROR,
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
    public List<FlowNodeDO> advance(FlowInstanceDO currentInstance,
                                     String currentNodeCode,
                                     String skipType,
                                     String targetNodeCode,
                                     Map<String, Object> variables) {
        FlowNodeDO currentNode = flowDefinitionCacheService.getNodeByCode(
                currentInstance.getDefinitionId(), currentNodeCode);
        if (currentNode == null) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "error.workflow.msg_d84d389b", currentNodeCode);
        }

        // REJECT 退回
        if ("REJECT".equalsIgnoreCase(skipType)) {
            String rejectTarget = targetNodeCode != null
                    ? targetNodeCode
                    : resolveRejectTarget(currentInstance.getDefinitionId(), currentNodeCode);
            if (rejectTarget == null) {
                throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_241f4a79");
            }
            FlowNodeDO target = flowDefinitionCacheService.getNodeByCode(
                    currentInstance.getDefinitionId(), rejectTarget);
            if (target == null) {
                throw new BizException(BizErrorCode.NOT_FOUND, "error.workflow.msg_6e66716d", rejectTarget);
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
            // P0-5 / GAP-P2: 网关 join 聚合 — 使用 Redis join 令牌精确跟踪分支到达状态
            if (isJoinNode(next) && hasMultipleIncoming(currentInstance.getDefinitionId(), next.getNodeCode())) {
                int incomingCount = flowDefinitionCacheService.getSkipsByNextNode(
                        currentInstance.getDefinitionId(), next.getNodeCode()).size();
                Long instId = currentInstance.getId();
                String joinCode = next.getNodeCode();
                try {
                    // 懒初始化：首次到达时初始化令牌（branchCount = 入边数）
                    if (!joinTokenService.isInitialized(instId, joinCode)) {
                        joinTokenService.initTokens(instId, joinCode, incomingCount);
                    }
                    // 标记本次到达
                    boolean allArrived = joinTokenService.arriveToken(instId, joinCode);
                    if (allArrived) {
                        // 全部分支到达：清理令牌，继续推进
                        joinTokenService.clearTokens(instId, joinCode);
                        log.info("[Flow] 并行网关 join 聚合通过（令牌）: instanceId={} node={}",
                                instId, joinCode);
                    } else {
                        log.info("[Flow] 并行网关 join 等待（令牌）: instanceId={} node={} arrived<{}",
                                instId, joinCode, incomingCount);
                        continue; // 等待其他分支
                    }
                } catch (Exception e) {
                    // Redis 异常降级：回退到 countPendingByNode 逻辑
                    log.warn("[Flow] join 令牌异常，降级到 countPending: instanceId={} node={} err={}",
                            instId, joinCode, e.getMessage());
                    int pending = taskMapper.countPendingByNode(instId, joinCode);
                    if (pending > 0) {
                        log.info("[Flow] 并行网关 join 等待（降级）: instanceId={} node={} pending={}",
                                instId, joinCode, pending);
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
                throw new BizException(BizErrorCode.NOT_FOUND,
                        "error.workflow.msg_6e66716d" + nodeCode);
            }
            // 避免重复
            if (targets.stream().noneMatch(t -> t.getNodeCode().equals(nodeCode))) {
                targets.add(target);
            }
        }
        if (targets.isEmpty()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_241f4a79");
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

        // 排他/包容网关兜底：如果无匹配且有默认出边，取第一条
        if ((isExclusive || isInclusive) && matched.isEmpty()) {
            log.info("[Flow] {}无匹配条件，取默认出边: node={}",
                    isExclusive ? "排他网关" : "包容网关", currentNode.getNodeCode());
            matched.add(all.get(0));
        }

        return matched;
    }

    /**
     * 评估跳转条件表达式
     *
     * <p>优先使用 FlowRoutingService（literule Aviator 引擎），
     * 如果 routingService 不可用或评估失败，回退到 DefaultFlowVariableStrategy（SpEL）。
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
    public String resolveRejectTarget(Long definitionId, String currentNodeCode) {
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

    /** 判断节点是否有多个入边 */
    private boolean hasMultipleIncoming(Long definitionId, String nodeCode) {
        List<FlowSkipDO> incoming = flowDefinitionCacheService.getSkipsByNextNode(definitionId, nodeCode);
        return incoming != null && incoming.size() > 1;
    }

    private String lookupNodeCodeByName(Long definitionId, String skipName) {
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
            return JsonUtils.parseMap(json);
        } catch (Exception e) {
            log.warn("[Flow] 变量解析失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private List<FlowInstanceViewDTO.FlowTaskViewDTO> loadCurrentTasks(Long instanceId) {
        return taskService.listPendingByInstance(instanceId).stream()
                .map(taskService::toView)
                .toList();
    }
}
