package com.njydsz.pmis.workflow.flow.engine.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.flow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.flow.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.flow.engine.FlowVariableStrategy;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.flow.entity.FlowSkipDO;
import com.njydsz.pmis.workflow.flow.enums.FlowNodeType;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowSkipMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
import com.njydsz.pmis.workflow.flow.service.FlowInstanceService;
import com.njydsz.pmis.workflow.flow.service.FlowTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
public class DefaultFlowAdvancer implements FlowAdvancer {

    private final FlowSkipMapper skipMapper;
    private final FlowNodeMapper nodeMapper;
    private final FlowInstanceMapper instanceMapper;
    private final FlowTaskService taskService;
    private final FlowInstanceService instanceService;
    private final FlowVariableStrategy variableStrategy;
    private final FlowTaskMapper taskMapper;

    @Override
    public com.njydsz.pmis.workflow.flow.service.FlowInstanceService getInstanceService() {
        return instanceService;
    }

    @Override
    public FlowInstanceViewDTO start(Long instanceId) {
        FlowInstanceDO instance = instanceService.getById(instanceId);
        if (instance == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "流程实例不存在: " + instanceId);
        }
        FlowNodeDO startNode = nodeMapper.selectStartNode(instance.getDefinitionId());
        if (startNode == null) {
            throw new BizException(BizErrorCode.INTERNAL_ERROR,
                    "流程定义缺少开始节点: definitionId=" + instance.getDefinitionId());
        }
        List<FlowNodeDO> nextNodes = advance(instance, startNode.getNodeCode(),
                "PASS", null, parseVariable(instance.getVariable()));
        if (nextNodes.isEmpty()) {
            log.info("[Flow] 流程无下游节点，自动完成: instanceId={}", instanceId);
            instanceService.complete(instanceId, startNode.getNodeCode());
            return instanceService.toView(instanceService.getById(instanceId),
                    loadCurrentTasks(instanceId));
        }
        com.njydsz.pmis.workflow.flow.service.impl.FlowInstanceServiceImpl impl =
                (com.njydsz.pmis.workflow.flow.service.impl.FlowInstanceServiceImpl) instanceService;
        impl.generateTasksForNodes(instanceId, nextNodes, parseVariable(instance.getVariable()));
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
        FlowNodeDO currentNode = nodeMapper.selectByCode(
                currentInstance.getDefinitionId(), currentNodeCode);
        if (currentNode == null) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "当前节点不存在: nodeCode=" + currentNodeCode);
        }

        // REJECT 退回
        if ("REJECT".equalsIgnoreCase(skipType)) {
            String rejectTarget = targetNodeCode != null
                    ? targetNodeCode
                    : resolveRejectTarget(currentInstance.getDefinitionId(), currentNodeCode);
            if (rejectTarget == null) {
                throw new BizException(BizErrorCode.BAD_REQUEST, "无法找到退回目标节点");
            }
            FlowNodeDO target = nodeMapper.selectByCode(currentInstance.getDefinitionId(), rejectTarget);
            if (target == null) {
                throw new BizException(BizErrorCode.NOT_FOUND, "退回目标节点不存在: " + rejectTarget);
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
            FlowNodeDO next = nodeMapper.selectByCode(
                    currentInstance.getDefinitionId(), skip.getNextNodeCode());
            if (next == null) {
                log.warn("[Flow] 跳转目标节点不存在: skipId={} nextNode={}",
                        skip.getId(), skip.getNextNodeCode());
                continue;
            }
            // P0-5: 网关 join 聚合 — 如果下一节点是并行/包容网关且有多个入边，
            // 检查是否还有其他分支的未完成任务
            if (isJoinNode(next) && hasMultipleIncoming(currentInstance.getDefinitionId(), next.getNodeCode())) {
                int pending = taskMapper.countPendingByNode(
                        currentInstance.getId(), next.getNodeCode());
                if (pending > 0) {
                    log.info("[Flow] 并行网关 join 等待: instanceId={} node={} pending={}",
                            currentInstance.getId(), next.getNodeCode(), pending);
                    continue; // 不推进到 join 节点，等待其他分支完成
                }
            }
            nextNodes.add(next);
        }
        return nextNodes;
    }

    @Override
    public List<FlowSkipDO> resolvePassSkips(FlowInstanceDO instance,
                                              FlowNodeDO currentNode,
                                              Map<String, Object> variables) {
        List<FlowSkipDO> all = skipMapper.selectByNodeCode(
                instance.getDefinitionId(), currentNode.getNodeCode(), "PASS");
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
            if (cond == null || cond.isBlank() || variableStrategy.evaluate(cond, variables)) {
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

    @Override
    public String resolveRejectTarget(Long definitionId, String currentNodeCode) {
        List<FlowSkipDO> incoming = skipMapper.selectByNextNode(definitionId, currentNodeCode);
        if (!incoming.isEmpty()) {
            return incoming.get(0).getSkipName() == null
                    ? currentNodeCode
                    : lookupNodeCodeByName(definitionId, incoming.get(0).getSkipName());
        }
        FlowNodeDO start = nodeMapper.selectStartNode(definitionId);
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
        List<FlowSkipDO> incoming = skipMapper.selectByNextNode(definitionId, nodeCode);
        return incoming != null && incoming.size() > 1;
    }

    private String lookupNodeCodeByName(Long definitionId, String skipName) {
        List<FlowNodeDO> all = nodeMapper.selectByDefinitionId(definitionId);
        return all.stream()
                .filter(n -> skipName.equals(n.getNodeName()))
                .map(FlowNodeDO::getNodeCode)
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseVariable(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return com.alibaba.fastjson2.JSON.parseObject(json, Map.class);
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
