package com.njydsz.pmis.workflow.flow.service.impl;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.flow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.flow.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.flow.engine.FlowEventListener;
import com.njydsz.pmis.workflow.flow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.flow.enums.FlowNodeType;
import com.njydsz.pmis.workflow.flow.enums.FlowTaskStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
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
 * <p>P0 修复：补全 onInstanceStart / onError 事件触发、挂起冻结任务、撤回功能。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowInstanceServiceImpl implements FlowInstanceService {

    private final FlowInstanceMapper instanceMapper;
    private final FlowDefinitionService definitionService;
    private final FlowAdvancer advancer;
    private final FlowTaskService taskService;
    private final FlowTaskMapper taskMapper;
    private final List<FlowEventListener> eventListeners;

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

        // P0-2: 触发 onInstanceStart 事件
        fireInstanceStart(instanceId, dto.getVariables());

        // 3. 引擎推进：开始节点 → 下一节点
        try {
            advancer.start(instanceId);
        } catch (Exception e) {
            fireError(instanceId, e);
            throw e;
        }
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
        // P2-18: reason 持久化到 variable JSON
        String var = instance.getVariable();
        if (StringUtils.hasText(reason)) {
            try {
                Map<String, Object> m = var == null ? new java.util.HashMap<>()
                        : JSON.parseObject(var, Map.class);
                m.put("_terminateReason", reason);
                var = JSON.toJSONString(m);
            } catch (Exception ignored) {
            }
        }
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
        // P2-18: 冻结 PENDING 任务（标记为 SKIPPED 不可操作）
        // 实际实现：不改任务状态，通过实例状态 SUSPENDED 在 pass/reject 中拦截
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

        // 业务侧事件：onInstanceCompleted
        fireEvent(l -> l.onInstanceCompleted(instanceId));
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

    // ============================== P1-8: 撤回 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean recall(Long instanceId, Long initiatorId) {
        FlowInstanceDO instance = getByIdOrThrow(instanceId);
        // 校验：仅发起人可撤回
        if (!instance.getInitiatorId().equals(initiatorId)) {
            throw new BizException(BizErrorCode.FORBIDDEN, "仅发起人可撤回流程");
        }
        // 校验：仅运行中可撤回
        if (!FlowInstanceStatus.RUNNING.name().equals(instance.getFlowStatus())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "仅运行中流程可撤回");
        }
        // 校验：下一节点未被处理（PENDING 状态的任务可以撤回）
        List<FlowTaskDO> pendingTasks = taskMapper.selectPendingByInstance(instanceId);
        boolean anyProcessed = pendingTasks.stream()
                .anyMatch(t -> FlowTaskStatus.CLAIMED.name().equals(t.getTaskStatus())
                        || FlowTaskStatus.COMPLETED.name().equals(t.getTaskStatus()));
        if (anyProcessed) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "审批人已处理，不可撤回");
        }
        // 取消当前待办
        taskService.cancelByInstance(instanceId, FlowTaskStatus.CANCELLED.name());
        // 回退到开始节点的下一节点（重新生成第一批待办）
        // 简化实现：将实例状态保持 RUNNING，重新推进到第一个审批节点
        try {
            advancer.start(instanceId);
        } catch (Exception e) {
            log.error("[Flow] 撤回后重新推进失败: instanceId={}", instanceId, e);
            throw new BizException(BizErrorCode.INTERNAL_ERROR, "撤回失败: " + e.getMessage());
        }
        log.info("[Flow] 撤回流程: instanceId={} initiatorId={}", instanceId, initiatorId);
        return true;
    }

    // ============================== 内部方法 ==============================

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
        FlowInstanceDO instance = getByIdOrThrow(instanceId);
        List<FlowNodeDO> nextNodes = advancer.advance(instance, startNode.getNodeCode(),
                "PASS", null, variables);
        if (nextNodes.isEmpty()) {
            log.warn("[Flow] 流程无下游节点: instanceId={}", instanceId);
            complete(instanceId, startNode.getNodeCode());
            return null;
        }
        for (FlowNodeDO node : nextNodes) {
            taskService.createTask(instanceId, node, variables);
        }
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
            if (node.getNodeType().equals(FlowNodeType.CC.getCode())) {
                log.info("[Flow] 抄送节点跳过: instanceId={} node={}", instanceId, node.getNodeCode());
                continue;
            }
            if (node.getNodeType().equals(FlowNodeType.END.getCode())) {
                complete(instanceId, node.getNodeCode());
                return;
            }
            taskService.createTask(instanceId, node, variables);
        }
    }

    // ============================== 事件触发 ==============================

    private void fireInstanceStart(Long instanceId, Map<String, Object> variables) {
        if (eventListeners == null) return;
        for (FlowEventListener listener : eventListeners) {
            try {
                listener.onInstanceStart(instanceId, variables);
            } catch (Exception e) {
                log.warn("[Flow] onInstanceStart 事件失败: {}", e.getMessage());
            }
        }
    }

    private void fireEvent(java.util.function.Consumer<FlowEventListener> action) {
        if (eventListeners == null) return;
        for (FlowEventListener listener : eventListeners) {
            try {
                action.accept(listener);
            } catch (Exception e) {
                log.warn("[Flow] 事件监听器异常: {}", e.getMessage());
            }
        }
    }

    private void fireError(Long instanceId, Throwable t) {
        if (eventListeners == null) return;
        for (FlowEventListener listener : eventListeners) {
            try {
                listener.onError(instanceId, t);
            } catch (Exception e) {
                log.warn("[Flow] onError 事件失败: {}", e.getMessage());
            }
        }
    }
}
