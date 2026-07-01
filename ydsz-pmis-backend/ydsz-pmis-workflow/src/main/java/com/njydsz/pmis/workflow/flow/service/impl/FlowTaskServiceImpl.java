package com.njydsz.pmis.workflow.flow.service.impl;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.flow.dto.FlowAssigneeDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.flow.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.flow.engine.FlowVariableStrategy;
import com.njydsz.pmis.workflow.flow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.flow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.flow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.flow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.flow.enums.FlowAssigneeType;
import com.njydsz.pmis.workflow.flow.enums.FlowPerformType;
import com.njydsz.pmis.workflow.flow.enums.FlowTaskStatus;
import com.njydsz.pmis.workflow.flow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowTaskMapper;
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
 * 待办任务 Service 实现
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskServiceImpl implements FlowTaskService {

    private final FlowTaskMapper taskMapper;
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowInstanceMapper instanceMapper;
    private final FlowInstanceService instanceService;
    private final FlowAdvancer advancer;
    private final FlowVariableStrategy variableStrategy;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(Long instanceId, FlowNodeDO node, Map<String, Object> variables) {
        FlowInstanceDO instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "实例不存在: " + instanceId);
        }

        FlowTaskDO task = new FlowTaskDO();
        task.setInstanceId(instanceId);
        task.setFlowCode(instance.getFlowCode());
        task.setDefinitionId(instance.getDefinitionId());
        task.setNodeCode(node.getNodeCode());
        task.setNodeName(node.getNodeName());
        task.setNodeType(node.getNodeType());
        task.setBusinessType(instance.getBusinessType());
        task.setBusinessId(instance.getBusinessId());
        task.setBusinessNo(instance.getBusinessNo());
        task.setFlowName(instance.getFlowName());
        task.setTitle(instance.getTitle());
        task.setPermissionFlag(node.getPermissionFlag());
        task.setPerformType(FlowPerformType.OR.name());
        task.setApproveCount(1);
        task.setApproveFinished(0);
        task.setTaskStatus(FlowTaskStatus.PENDING.name());
        task.setTenantId(instance.getTenantId());
        task.setProviderTraceId(instance.getProviderTraceId());

        taskMapper.insert(task);
        // 解析办理人（必须在 insert 之后，确保 task.getId() 有值）
        resolveAssignee(task, node, variables, null, instance);
        taskMapper.updateById(task);
        log.info("[Flow] 创建任务: instanceId={} node={} assignee={}",
                instanceId, node.getNodeCode(), task.getAssigneeId());
        return task.getId();
    }

    @Override
    public void claim(Long taskId, Long userId) {
        FlowTaskDO task = getTaskOrThrow(taskId);
        if (!FlowTaskStatus.PENDING.name().equals(task.getTaskStatus())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "任务不可签收: " + task.getTaskStatus());
        }
        // 单点：assignee 改成 userId，状态改为 CLAIMED
        taskMapper.updateById(toClaimTask(task, userId));
        log.info("[Flow] 签收任务: taskId={} userId={}", taskId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pass(FlowTaskOperateDTO dto) {
        FlowTaskDO task = getTaskOrThrow(dto.getTaskId());
        if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "任务已完成: " + task.getTaskStatus());
        }
        Map<String, Object> variables = dto.getVariables() == null
                ? Collections.emptyMap() : dto.getVariables();

        // 标记当前任务完成
        LocalDateTime now = LocalDateTime.now();
        Long durationMs = task.getCreatedAt() == null
                ? null
                : Duration.between(task.getCreatedAt(), now).toMillis();
        taskMapper.completeTask(task.getId(), FlowTaskStatus.COMPLETED.name(),
                dto.getComment(), now, durationMs);

        // 归档到历史表
        archiveTask(task, FlowTaskStatus.COMPLETED);

        // 会签：计数器 +1
        if (FlowPerformType.PARALLEL.name().equals(task.getPerformType())) {
            taskMapper.incrementFinished(task.getId());
            // 实际业务：会签全通过才推进（这里简化：任一通过即推进）
        }

        // 推进实例
        FlowInstanceDO instance = instanceMapper.selectById(task.getInstanceId());
        List<FlowNodeDO> nextNodes = advancer.advance(instance, task.getNodeCode(),
                "PASS", null, mergeVariables(instance, variables));
        // 委托 FlowInstanceServiceImpl 生成下一批任务并更新当前节点
        ((FlowInstanceServiceImpl) instanceService).generateTasksForNodes(
                task.getInstanceId(), nextNodes, mergeVariables(instance, variables));
        if (!nextNodes.isEmpty() && nextNodes.get(0).getNodeType()
                != com.njydsz.pmis.workflow.flow.enums.FlowNodeType.END.getCode()) {
            instanceMapper.updateStatus(instance.getId(), instance.getFlowStatus(),
                    nextNodes.get(0).getNodeCode(), nextNodes.get(0).getNodeName(),
                    null, null);
        }
        log.info("[Flow] 通过任务: taskId={} action=PASS next={}",
                task.getId(), nextNodes.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(FlowTaskOperateDTO dto) {
        FlowTaskDO task = getTaskOrThrow(dto.getTaskId());
        if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "任务已完成");
        }
        LocalDateTime now = LocalDateTime.now();
        Long durationMs = task.getCreatedAt() == null
                ? null
                : Duration.between(task.getCreatedAt(), now).toMillis();
        taskMapper.completeTask(task.getId(), FlowTaskStatus.REJECTED.name(),
                dto.getComment(), now, durationMs);
        archiveTask(task, FlowTaskStatus.REJECTED);

        // 推进：退回模式
        FlowInstanceDO instance = instanceMapper.selectById(task.getInstanceId());
        List<FlowNodeDO> rejectTargets = advancer.advance(instance, task.getNodeCode(),
                "REJECT", dto.getTargetNodeCode(), mergeVariables(instance, dto.getVariables()));
        if (rejectTargets.isEmpty()) {
            // 找不到退回目标 → 流程驳回终止
            instanceMapper.updateStatus(instance.getId(),
                    com.njydsz.pmis.workflow.flow.enums.FlowInstanceStatus.REJECTED.name(),
                    null, null, now,
                    instance.getStartAt() == null ? null
                            : Duration.between(instance.getStartAt(), now).toMillis());
            taskMapper.cancelByInstance(instance.getId(), FlowTaskStatus.CANCELLED.name());
            return;
        }
        // 生成退回目标的任务
        ((FlowInstanceServiceImpl) instanceService).generateTasksForNodes(
                instance.getId(), rejectTargets, mergeVariables(instance, dto.getVariables()));
        instanceMapper.updateStatus(instance.getId(), instance.getFlowStatus(),
                rejectTargets.get(0).getNodeCode(), rejectTargets.get(0).getNodeName(),
                null, null);
        log.info("[Flow] 退回任务: taskId={} target={}", task.getId(),
                rejectTargets.get(0).getNodeCode());
    }

    @Override
    public void transfer(FlowTaskOperateDTO dto) {
        if (dto.getTargetUserId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "转办目标人不能为空");
        }
        FlowTaskDO task = getTaskOrThrow(dto.getTaskId());
        // 先保留原办理人信息（assignor = 委托人）
        Long originalAssignorId = parseAssignorId(task.getAssigneeId());
        String originalAssignorName = task.getAssigneeName();
        task.setAssigneeId(String.valueOf(dto.getTargetUserId()));
        task.setAssigneeName(dto.getTargetUserName());
        task.setAssignorId(originalAssignorId);
        task.setAssignorName(originalAssignorName);
        task.setTaskStatus(FlowTaskStatus.CLAIMED.name());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        log.info("[Flow] 转办任务: taskId={} → userId={}", task.getId(), dto.getTargetUserId());
    }

    @Override
    public void delegate(FlowTaskOperateDTO dto) {
        // 委派：暂时把任务指给被委派人；处理完后回到原 assignee（实现简化：同 transfer）
        transfer(dto);
    }

    @Override
    public void cancelByInstance(Long instanceId, String taskStatus) {
        taskMapper.cancelByInstance(instanceId, taskStatus);
    }

    @Override
    public List<FlowTaskDO> listPendingByInstance(Long instanceId) {
        return taskMapper.selectPendingByInstance(instanceId);
    }

    @Override
    public List<FlowTaskDO> listTodoByAssignee(String assigneeId, Long tenantId) {
        return taskMapper.selectTodoByAssignee(assigneeId, tenantId == null ? 1L : tenantId);
    }

    @Override
    public List<FlowTaskDO> listDoneByAssignee(String assigneeId, Long tenantId) {
        return taskMapper.selectDoneByAssignee(assigneeId, tenantId == null ? 1L : tenantId);
    }

    @Override
    public FlowInstanceViewDTO.FlowTaskViewDTO toView(FlowTaskDO task) {
        if (task == null) {
            return null;
        }
        return FlowInstanceViewDTO.FlowTaskViewDTO.builder()
                .id(task.getId())
                .nodeCode(task.getNodeCode())
                .nodeName(task.getNodeName())
                .nodeType(task.getNodeType())
                .assigneeType(task.getAssigneeType())
                .assigneeId(task.getAssigneeId())
                .assigneeName(task.getAssigneeName())
                .performType(task.getPerformType())
                .taskStatus(task.getTaskStatus())
                .comment(task.getComment())
                .createAt(task.getCreatedAt())
                .claimAt(task.getClaimAt())
                .finishAt(task.getFinishAt())
                .durationMs(task.getDurationMs())
                .dueAt(task.getDueAt())
                .build();
    }

    // ============== 私有 ==============

    private FlowTaskDO getTaskOrThrow(Long id) {
        FlowTaskDO task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "任务不存在: " + id);
        }
        return task;
    }

    private void resolveAssignee(FlowTaskDO task, FlowNodeDO node,
                                  Map<String, Object> variables,
                                  FlowAssigneeDTO explicit,
                                  FlowInstanceDO instance) {
        String perm = node.getPermissionFlag();
        if (explicit != null) {
            task.setAssigneeType(explicit.getUserType());
            task.setAssigneeId(explicit.getUserId());
            task.setAssigneeName(explicit.getUserName());
            return;
        }
        if (!StringUtils.hasText(perm)) {
            // 默认指派给发起人
            task.setAssigneeType(FlowAssigneeType.INITIATOR.name());
            task.setAssigneeId(instance != null && instance.getInitiatorId() != null
                    ? String.valueOf(instance.getInitiatorId())
                    : String.valueOf(task.getId()));
            task.setAssigneeName("INITIATOR");
            return;
        }
        // 解析 permissionFlag：role:hr / dept:10 / user:1001 / ${expression}
        String resolved = variableStrategy.resolveAssignee(perm, variables);
        if (resolved == null) {
            task.setAssigneeType(FlowAssigneeType.USER.name());
            task.setAssigneeId(perm);
            return;
        }
        if (resolved.startsWith("role:")) {
            task.setAssigneeType(FlowAssigneeType.ROLE.name());
            task.setAssigneeId(resolved.substring(5));
        } else if (resolved.startsWith("dept:")) {
            task.setAssigneeType(FlowAssigneeType.DEPT.name());
            task.setAssigneeId(resolved.substring(5));
        } else if (resolved.startsWith("user:")) {
            task.setAssigneeType(FlowAssigneeType.USER.name());
            task.setAssigneeId(resolved.substring(5));
        } else if (resolved.startsWith("${")) {
            task.setAssigneeType(FlowAssigneeType.SPEL.name());
            task.setAssigneeId(resolved);
        } else {
            // 默认按 USER 处理
            task.setAssigneeType(FlowAssigneeType.USER.name());
            task.setAssigneeId(resolved);
        }
    }

    private Long parseAssignorId(String assigneeId) {
        if (assigneeId == null || !assigneeId.matches("\\d+")) {
            return null;
        }
        try {
            return Long.parseLong(assigneeId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private FlowTaskDO toClaimTask(FlowTaskDO src, Long userId) {
        src.setAssigneeId(String.valueOf(userId));
        src.setTaskStatus(FlowTaskStatus.CLAIMED.name());
        src.setClaimAt(LocalDateTime.now());
        return src;
    }

    private void archiveTask(FlowTaskDO src, FlowTaskStatus finalStatus) {
        FlowHisTaskDO his = new FlowHisTaskDO();
        his.setInstanceId(src.getInstanceId());
        his.setTaskId(src.getId());
        his.setFlowCode(src.getFlowCode());
        his.setDefinitionId(src.getDefinitionId());
        his.setNodeCode(src.getNodeCode());
        his.setNodeName(src.getNodeName());
        his.setNodeType(src.getNodeType());
        his.setBusinessType(src.getBusinessType());
        his.setBusinessId(src.getBusinessId());
        his.setBusinessNo(src.getBusinessNo());
        his.setFlowName(src.getFlowName());
        his.setTitle(src.getTitle());
        his.setAssigneeType(src.getAssigneeType());
        his.setAssigneeId(src.getAssigneeId());
        his.setAssigneeName(src.getAssigneeName());
        his.setPerformType(src.getPerformType());
        his.setApproveCount(src.getApproveCount());
        his.setApproveFinished(src.getApproveFinished());
        his.setTaskStatus(finalStatus.name());
        his.setComment(src.getComment());
        // 关键：created_at 业务时间（关闭自动填充，由业务显式 setCreatedAt）
        his.setCreatedAt(src.getCreatedAt());
        his.setClaimAt(src.getClaimAt());
        his.setFinishAt(src.getFinishAt());
        his.setDurationMs(src.getDurationMs());
        his.setTenantId(src.getTenantId());
        his.setProviderTraceId(src.getProviderTraceId());
        hisTaskMapper.insert(his);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeVariables(FlowInstanceDO instance, Map<String, Object> extra) {
        if (instance == null || !StringUtils.hasText(instance.getVariable())) {
            return extra == null ? Collections.emptyMap() : extra;
        }
        try {
            Map<String, Object> base = JSON.parseObject(instance.getVariable(), Map.class);
            if (extra != null && !extra.isEmpty()) {
                base.putAll(extra);
            }
            return base;
        } catch (Exception e) {
            return extra == null ? Collections.emptyMap() : extra;
        }
    }
}
