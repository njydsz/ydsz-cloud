package com.njydsz.pmis.workflow.flow.service.impl;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.flow.dto.FlowAssigneeDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.flow.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.flow.engine.FlowVariableStrategy;
import com.njydsz.pmis.workflow.flow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowAssigneeType;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.enums.FlowNodeType;
import com.njydsz.pmis.workflow.flow.enums.FlowTaskStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowDefinitionMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.service.FlowDefinitionService;
import com.njydsz.pmis.workflow.flow.service.FlowInstanceService;
import com.njydsz.pmis.workflow.flow.service.FlowTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 流程实例 Service 实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowInstanceServiceImpl implements FlowInstanceService {

    private final FlowInstanceMapper instanceMapper;
    private final FlowDefinitionMapper definitionMapper;
    private final FlowDefinitionService definitionService;
    private final FlowAdvancer advancer;
    private final FlowTaskService taskService;
    private final FlowVariableStrategy variableStrategy;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long start(FlowStartProcessDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getFlowCode())
                || !StringUtils.hasText(dto.getBusinessType())
                || !StringUtils.hasText(dto.getBusinessId())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "flowCode/businessType/businessId 必填");
        }

        // 0. 幂等：同 business 已有 RUNNING 实例则直接返回
        FlowInstanceDO existing = instanceMapper.selectByBusiness(
                dto.getBusinessType(), dto.getBusinessId());
        if (existing != null && FlowInstanceStatus.RUNNING.name().equals(existing.getFlowStatus())) {
            log.info("[Flow] 实例已存在: businessType={} businessId={} id={}",
                    dto.getBusinessType(), dto.getBusinessId(), existing.getId());
            return existing.getId();
        }

        // 1. 查定义
        Long tenantId = dto.getTenantId() == null ? 1L : dto.getTenantId();
        FlowDefinitionDO def = definitionService.getPublished(
                dto.getFlowCode(),
                StringUtils.hasText(dto.getVersion()) ? dto.getVersion() : "1.0",
                tenantId);
        if (def == null) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "流程定义未发布: code=" + dto.getFlowCode());
        }

        // 2. 创建实例
        FlowInstanceDO instance = new FlowInstanceDO();
        instance.setFlowCode(def.getFlowCode());
        instance.setFlowName(def.getFlowName());
        instance.setDefinitionId(def.getId());
        instance.setFlowVersion(def.getVersion());
        instance.setBusinessType(dto.getBusinessType());
        instance.setBusinessId(dto.getBusinessId());
        instance.setBusinessNo(dto.getBusinessNo());
        instance.setTitle(dto.getTitle() == null
                ? def.getFlowName() + "-" + dto.getBusinessId()
                : dto.getTitle());
        instance.setInitiatorId(dto.getInitiatorId());
        instance.setInitiatorName(dto.getInitiatorName());
        instance.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        instance.setActivityStatus(1);
        instance.setStartAt(LocalDateTime.now());
        instance.setVariable(dto.getVariables() == null
                ? null
                : JSON.toJSONString(dto.getVariables()));
        instance.setTenantId(tenantId);
        instance.setProviderTraceId(dto.getProviderTraceId());
        instanceMapper.insert(instance);
        Long instanceId = instance.getId();

        // 3. 引擎推进：开始节点 → 下一节点（advancer 内已生成任务 + 更新当前节点）
        advancer.start(instanceId);
        log.info("[Flow] 启动流程: code={} bizId={} instanceId={}",
                dto.getFlowCode(), dto.getBusinessId(), instanceId);
        return instanceId;
    }

    @Override
    public FlowInstanceDO getById(Long id) {
        return instanceMapper.selectById(id);
    }

    @Override
    public FlowInstanceDO getByBusiness(String businessType, String businessId) {
        return instanceMapper.selectByBusiness(businessType, businessId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void terminate(Long instanceId, String reason) {
        FlowInstanceDO instance = getByIdOrThrow(instanceId);
        if (FlowInstanceStatus.valueOf(instance.getFlowStatus()).isFinished()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "流程已结束，不可终止");
        }
        LocalDateTime now = LocalDateTime.now();
        Long durationMs = instance.getStartAt() == null
                ? null
                : Duration.between(instance.getStartAt(), now).toMillis();
        instanceMapper.updateStatus(instanceId, FlowInstanceStatus.TERMINATED.name(),
                null, null, now, durationMs);
        // 取消所有 PENDING 任务
        taskService.cancelByInstance(instanceId, FlowTaskStatus.CANCELLED.name());
        log.info("[Flow] 终止流程: instanceId={} reason={}", instanceId, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void suspend(Long instanceId) {
        FlowInstanceDO instance = getByIdOrThrow(instanceId);
        if (!FlowInstanceStatus.RUNNING.name().equals(instance.getFlowStatus())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "仅运行中流程可挂起");
        }
        instanceMapper.updateStatus(instanceId, FlowInstanceStatus.SUSPENDED.name(),
                instance.getCurrentNodeCode(), instance.getCurrentNodeName(),
                null, null);
        log.info("[Flow] 挂起流程: instanceId={}", instanceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activate(Long instanceId) {
        FlowInstanceDO instance = getByIdOrThrow(instanceId);
        if (!FlowInstanceStatus.SUSPENDED.name().equals(instance.getFlowStatus())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "仅挂起流程可激活");
        }
        instanceMapper.updateStatus(instanceId, FlowInstanceStatus.RUNNING.name(),
                instance.getCurrentNodeCode(), instance.getCurrentNodeName(),
                null, null);
        log.info("[Flow] 激活流程: instanceId={}", instanceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long instanceId, String endNodeCode) {
        FlowInstanceDO instance = getByIdOrThrow(instanceId);
        if (FlowInstanceStatus.valueOf(instance.getFlowStatus()).isFinished()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Long durationMs = instance.getStartAt() == null
                ? null
                : Duration.between(instance.getStartAt(), now).toMillis();
        instanceMapper.updateStatus(instanceId, FlowInstanceStatus.COMPLETED.name(),
                endNodeCode, null, now, durationMs);
        taskService.cancelByInstance(instanceId, FlowTaskStatus.SKIPPED.name());
        log.info("[Flow] 流程完成: instanceId={} endNode={}", instanceId, endNodeCode);
    }

    @Override
    public FlowInstanceViewDTO toView(FlowInstanceDO instance,
                                       List<FlowInstanceViewDTO.FlowTaskViewDTO> currentTasks) {
        if (instance == null) {
            return null;
        }
        return FlowInstanceViewDTO.builder()
                .id(instance.getId())
                .flowCode(instance.getFlowCode())
                .flowName(instance.getFlowName())
                .version(instance.getFlowVersion())
                .businessType(instance.getBusinessType())
                .businessId(instance.getBusinessId())
                .businessNo(instance.getBusinessNo())
                .title(instance.getTitle())
                .initiatorId(instance.getInitiatorId())
                .initiatorName(instance.getInitiatorName())
                .currentNodeCode(instance.getCurrentNodeCode())
                .currentNodeName(instance.getCurrentNodeName())
                .flowStatus(instance.getFlowStatus())
                .activityStatus(instance.getActivityStatus())
                .startAt(instance.getStartAt())
                .endAt(instance.getEndAt())
                .durationMs(instance.getDurationMs())
                .variable(instance.getVariable())
                .currentTasks(currentTasks)
                .build();
    }

    @Override
    public List<FlowInstanceDO> listByInitiator(Long initiatorId, String flowStatus) {
        return instanceMapper.selectByInitiator(initiatorId, flowStatus);
    }

    private FlowInstanceDO getByIdOrThrow(Long id) {
        FlowInstanceDO instance = instanceMapper.selectById(id);
        if (instance == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "流程实例不存在: " + id);
        }
        return instance;
    }

    /** 内部方法：创建第一个待办任务（供 FlowAdvancer 调用） */
    public Long createFirstTask(Long instanceId, FlowNodeDO startNode,
                                 Map<String, Object> variables) {
        // 找到开始节点的下一节点（按 PASS 跳转推进一次）
        FlowInstanceDO instance = getByIdOrThrow(instanceId);
        List<FlowNodeDO> nextNodes = advancer.advance(instance, startNode.getNodeCode(),
                "PASS", null, variables);
        if (nextNodes.isEmpty()) {
            log.warn("[Flow] 流程无下游节点: instanceId={}", instanceId);
            complete(instanceId, startNode.getNodeCode());
            return null;
        }
        // 推进到第一个下一节点时，可能有条件分支（多个 PASS 跳转）— 多节点都创建
        for (FlowNodeDO node : nextNodes) {
            taskService.createTask(instanceId, node, variables);
        }
        // 更新当前节点
        instanceMapper.updateStatus(instanceId, instance.getFlowStatus(),
                nextNodes.get(0).getNodeCode(),
                nextNodes.get(0).getNodeName(),
                null, null);
        return instanceId;
    }

    /** 内部方法：推进后批量生成任务（供 FlowAdvancer 调用） */
    public void generateTasksForNodes(Long instanceId, List<FlowNodeDO> nextNodes,
                                       Map<String, Object> variables) {
        if (nextNodes == null || nextNodes.isEmpty()) {
            return;
        }
        for (FlowNodeDO node : nextNodes) {
            // 抄送节点：CC（nodeType=2）不创建 task，仅记录
            if (node.getNodeType().equals(FlowNodeType.CC.getCode())) {
                log.info("[Flow] 抄送节点跳过: instanceId={} node={}", instanceId, node.getNodeCode());
                continue;
            }
            // 结束节点：直接完成
            if (node.getNodeType().equals(FlowNodeType.END.getCode())) {
                complete(instanceId, node.getNodeCode());
                return;
            }
            taskService.createTask(instanceId, node, variables);
        }
    }
}
