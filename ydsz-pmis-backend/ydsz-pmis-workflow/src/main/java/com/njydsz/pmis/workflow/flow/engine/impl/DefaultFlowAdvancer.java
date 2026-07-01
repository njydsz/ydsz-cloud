package com.njydsz.pmis.workflow.flow.engine.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.flow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.flow.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.flow.engine.FlowVariableStrategy;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.flow.entity.FlowSkipDO;
import com.njydsz.pmis.workflow.flow.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowSkipMapper;
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
 * <p>核心逻辑：当前节点 → 查 PASS 跳转 → 过滤条件 → 取目标节点 → 生成任务。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultFlowAdvancer implements FlowAdvancer {

    private final FlowSkipMapper skipMapper;
    private final FlowNodeMapper nodeMapper;
    private final FlowTaskService taskService;
    private final FlowInstanceService instanceService;
    private final FlowVariableStrategy variableStrategy;

    @Override
    public FlowInstanceViewDTO start(Long instanceId) {
        FlowInstanceDO instance = instanceService.getById(instanceId);
        if (instance == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "流程实例不存在: " + instanceId);
        }
        // 找开始节点
        FlowNodeDO startNode = nodeMapper.selectStartNode(instance.getDefinitionId());
        if (startNode == null) {
            throw new BizException(BizErrorCode.INTERNAL_ERROR,
                    "流程定义缺少开始节点: definitionId=" + instance.getDefinitionId());
        }
        // 开始节点 → 推进到下一节点
        List<FlowNodeDO> nextNodes = advance(instance, startNode.getNodeCode(),
                "PASS", null, parseVariable(instance.getVariable()));
        // 实际生成的任务由 advance() 内调用 taskService 完成
        return instanceService.toView(instance, loadCurrentTasks(instanceId));
    }

    @Override
    public List<FlowNodeDO> advance(FlowInstanceDO currentInstance,
                                     String currentNodeCode,
                                     String skipType,
                                     String targetNodeCode,
                                     Map<String, Object> variables) {
        // 1. 找到当前节点
        FlowNodeDO currentNode = nodeMapper.selectByCode(
                currentInstance.getDefinitionId(), currentNodeCode);
        if (currentNode == null) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "当前节点不存在: nodeCode=" + currentNodeCode);
        }

        // 2. 查跳转：REJECT 时取目标前驱节点；PASS 时取所有满足条件的出边
        List<FlowSkipDO> skips;
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
        } else {
            skips = resolvePassSkips(currentInstance, currentNode, variables);
        }

        if (skips.isEmpty()) {
            log.info("[Flow] 流程无下一节点，结束: instanceId={} nodeCode={}",
                    currentInstance.getId(), currentNodeCode);
            return Collections.emptyList();
        }

        // 3. 找下一节点
        List<FlowNodeDO> nextNodes = new ArrayList<>();
        for (FlowSkipDO skip : skips) {
            FlowNodeDO next = nodeMapper.selectByCode(
                    currentInstance.getDefinitionId(), skip.getNextNodeCode());
            if (next == null) {
                log.warn("[Flow] 跳转目标节点不存在: skipId={} nextNode={}",
                        skip.getId(), skip.getNextNodeCode());
                continue;
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
        // 无条件 → 取第一个；有条件 → 全部满足
        List<FlowSkipDO> matched = new ArrayList<>();
        for (FlowSkipDO skip : all) {
            String cond = skip.getSkipCondition();
            if (cond == null || cond.isBlank() || variableStrategy.evaluate(cond, variables)) {
                matched.add(skip);
            }
        }
        return matched;
    }

    @Override
    public String resolveRejectTarget(Long definitionId, String currentNodeCode) {
        // 默认退回：找指向当前节点的前一个 PASS 跳转
        List<FlowSkipDO> incoming = skipMapper.selectByNextNode(definitionId, currentNodeCode);
        if (!incoming.isEmpty()) {
            return incoming.get(0).getSkipName() == null
                    ? currentNodeCode
                    : lookupNodeCodeByName(definitionId, incoming.get(0).getSkipName());
        }
        // 若无入边，返回开始节点
        FlowNodeDO start = nodeMapper.selectStartNode(definitionId);
        return start == null ? null : start.getNodeCode();
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
