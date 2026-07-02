package com.njydsz.pmis.workflow.flow.service.impl;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.flow.dto.FlowAssigneeDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.flow.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.flow.engine.FlowAssigneeResolver;
import com.njydsz.pmis.workflow.flow.engine.FlowEventListener;
import com.njydsz.pmis.workflow.flow.engine.FlowVariableStrategy;
import com.njydsz.pmis.workflow.flow.entity.*;
import com.njydsz.pmis.workflow.flow.enums.*;
import com.njydsz.pmis.workflow.flow.mapper.*;
import com.njydsz.pmis.workflow.flow.service.FlowInstanceService;
import com.njydsz.pmis.workflow.flow.service.FlowTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 待办任务 Service 实现
 *
 * <p>P0 修复：会签全部通过才推进、监听器全事件触发、ROLE/DEPT 展开到 pmis_flow_user、
 * 委派语义修正（被委派人处理后回到原办理人）、审计日志。
 *
 * <p>P1 新增：加签（前/后）、催办、顺序会签/票签。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
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
    private final FlowUserMapper userMapper;
    private final FlowAuditLogMapper auditLogMapper;
    private final FlowNodeMapper nodeMapper;
    private final FlowAssigneeResolver assigneeResolver;
    private final List<FlowEventListener> eventListeners;

    // ============================== 创建任务 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(Long instanceId, FlowNodeDO node, Map<String, Object> variables) {
        FlowInstanceDO instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "实例不存在: " + instanceId);
        }

        // 解析办理人：尝试展开 ROLE/DEPT 为多人
        List<String> userIds = expandAssignees(node, variables);
        FlowPerformType performType = resolvePerformType(node);

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
        task.setPerformType(performType.name());
        task.setApproveCount(userIds.isEmpty() ? 1 : userIds.size());
        task.setApproveFinished(0);
        task.setTaskStatus(FlowTaskStatus.PENDING.name());
        task.setTenantId(instance.getTenantId());
        task.setProviderTraceId(instance.getProviderTraceId());

        // 设置首个办理人
        if (userIds.isEmpty()) {
            // 无法展开：回退到原有 resolveAssignee 逻辑
            taskMapper.insert(task);
            resolveAssignee(task, node, variables, null, instance);
            taskMapper.updateById(task);
        } else {
            // 展开成功：设置第一个用户为 assignee，其余写入 pmis_flow_user
            task.setAssigneeType(FlowAssigneeType.USER.name());
            task.setAssigneeId(userIds.get(0));
            task.setAssigneeName("USER:" + userIds.get(0));
            taskMapper.insert(task);
            // 写入 pmis_flow_user
            for (String uid : userIds) {
                FlowUserDO fu = new FlowUserDO();
                fu.setTaskId(task.getId());
                fu.setInstanceId(instanceId);
                fu.setNodeCode(node.getNodeCode());
                fu.setUserType(FlowAssigneeType.USER.name());
                fu.setUserId(uid);
                fu.setUserName("USER:" + uid);
                fu.setProcessed(0);
                fu.setTenantId(instance.getTenantId());
                fu.setProviderTraceId(instance.getProviderTraceId());
                userMapper.insert(fu);
            }
        }
        log.info("[Flow] 创建任务: instanceId={} node={} performType={} assigneeCount={}",
                instanceId, node.getNodeCode(), performType, userIds.size());
        fireEvent(FlowEventListener::onTaskCreated, task.getId());
        return task.getId();
    }

    // ============================== 签收 ==============================

    @Override
    public void claim(Long taskId, Long userId) {
        FlowTaskDO task = getTaskOrThrow(taskId);
        if (!FlowTaskStatus.PENDING.name().equals(task.getTaskStatus())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "任务不可签收: " + task.getTaskStatus());
        }
        taskMapper.updateById(toClaimTask(task, userId));
        audit(task, "CLAIM", userId, null, null);
        log.info("[Flow] 签收任务: taskId={} userId={}", taskId, userId);
    }

    // ============================== 通过（P0-1: 会签修复） ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pass(FlowTaskOperateDTO dto) {
        FlowTaskDO task = getTaskOrThrow(dto.getTaskId());
        if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "任务已完成: " + task.getTaskStatus());
        }
        Map<String, Object> variables = dto.getVariables() == null
                ? Collections.emptyMap() : dto.getVariables();
        FlowInstanceDO instance = instanceMapper.selectById(task.getInstanceId());
        Map<String, Object> mergedVars = mergeVariables(instance, variables);

        // P1-10: 委派回归 — 被委派人通过后任务回到原办理人
        if (FlowTaskStatus.DELEGATED.name().equals(task.getTaskStatus()) && task.getAssignorId() != null) {
            task.setAssigneeId(String.valueOf(task.getAssignorId()));
            task.setAssigneeName(task.getAssignorName());
            task.setAssignorId(null);
            task.setAssignorName(null);
            task.setTaskStatus(FlowTaskStatus.CLAIMED.name());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            audit(task, "DELEGATE_RETURN", dto.getUserId(), null, dto.getComment());
            log.info("[Flow] 委派回归: taskId={} → 原办理人={}", task.getId(), task.getAssigneeId());
            return;
        }

        FlowPerformType performType = FlowPerformType.valueOf(
                task.getPerformType() == null ? FlowPerformType.OR.name() : task.getPerformType());

        // 标记当前用户已处理（pmis_flow_user）
        if (dto.getUserId() != null) {
            userMapper.markProcessed(task.getId(), String.valueOf(dto.getUserId()),
                    dto.getComment(), LocalDateTime.now());
        }

        switch (performType) {
            case OR -> doPassAndAdvance(task, instance, mergedVars, dto);
            case PARALLEL -> doParallelPass(task, instance, mergedVars, dto);
            case SEQUENTIAL -> doSequentialPass(task, instance, mergedVars, dto);
            case VOTE -> doVotePass(task, instance, mergedVars, dto);
        }
    }

    // ============================== 驳回（P1-11: 任意历史节点） ==============================

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

        FlowInstanceDO instance = instanceMapper.selectById(task.getInstanceId());
        Map<String, Object> mergedVars = mergeVariables(instance, dto.getVariables());

        // 推进：退回模式（dto.targetNodeCode 支持任意历史节点）
        List<FlowNodeDO> rejectTargets = advancer.advance(instance, task.getNodeCode(),
                "REJECT", dto.getTargetNodeCode(), mergedVars);
        if (rejectTargets.isEmpty()) {
            instanceMapper.updateStatus(instance.getId(),
                    FlowInstanceStatus.REJECTED.name(),
                    null, null, now,
                    instance.getStartAt() == null ? null
                            : Duration.between(instance.getStartAt(), now).toMillis());
            taskMapper.cancelByInstance(instance.getId(), FlowTaskStatus.CANCELLED.name());
            fireInstanceRejected(instance.getId(), dto.getComment());
            audit(task, "REJECT", dto.getUserId(), null, dto.getComment());
            return;
        }
        ((FlowInstanceServiceImpl) instanceService).generateTasksForNodes(
                instance.getId(), rejectTargets, mergedVars);
        instanceMapper.updateStatus(instance.getId(), instance.getFlowStatus(),
                rejectTargets.get(0).getNodeCode(), rejectTargets.get(0).getNodeName(),
                null, null);
        audit(task, "REJECT", dto.getUserId(), null, dto.getComment());
        log.info("[Flow] 退回任务: taskId={} target={}", task.getId(),
                rejectTargets.get(0).getNodeCode());
    }

    // ============================== 转办 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transfer(FlowTaskOperateDTO dto) {
        if (dto.getTargetUserId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "转办目标人不能为空");
        }
        FlowTaskDO task = getTaskOrThrow(dto.getTaskId());
        Long originalAssignorId = parseAssignorId(task.getAssigneeId());
        String originalAssignorName = task.getAssigneeName();
        task.setAssigneeId(String.valueOf(dto.getTargetUserId()));
        task.setAssigneeName(dto.getTargetUserName());
        task.setAssignorId(originalAssignorId);
        task.setAssignorName(originalAssignorName);
        task.setTaskStatus(FlowTaskStatus.CLAIMED.name());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        audit(task, "TRANSFER", dto.getUserId(), dto.getTargetUserId(), dto.getComment());
        log.info("[Flow] 转办任务: taskId={} → userId={}", task.getId(), dto.getTargetUserId());
    }

    // ============================== 委派（P1-10: 修正语义） ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delegate(FlowTaskOperateDTO dto) {
        if (dto.getTargetUserId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "委派目标人不能为空");
        }
        FlowTaskDO task = getTaskOrThrow(dto.getTaskId());
        // 保存原办理人
        Long originalAssigneeId = parseAssignorId(task.getAssigneeId());
        String originalAssigneeName = task.getAssigneeName();
        task.setAssignorId(originalAssigneeId);
        task.setAssignorName(originalAssigneeName);
        task.setAssigneeId(String.valueOf(dto.getTargetUserId()));
        task.setAssigneeName(dto.getTargetUserName());
        task.setTaskStatus(FlowTaskStatus.DELEGATED.name());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        audit(task, "DELEGATE", dto.getUserId(), dto.getTargetUserId(), dto.getComment());
        log.info("[Flow] 委派任务: taskId={} → 被委派人={} (处理完回到 {})",
                task.getId(), dto.getTargetUserId(), originalAssigneeName);
    }

    // ============================== 加签（P1-7） ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void countersignBefore(FlowTaskOperateDTO dto) {
        FlowTaskDO task = getTaskOrThrow(dto.getTaskId());
        if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "任务已完成，不可加签");
        }
        // 前加签：在当前节点前插入临时审批人
        // 实现：为当前任务新增一个审批人记录到 pmis_flow_user，approveCount+1
        if (dto.getTargetUserId() != null) {
            FlowUserDO fu = new FlowUserDO();
            fu.setTaskId(task.getId());
            fu.setInstanceId(task.getInstanceId());
            fu.setNodeCode(task.getNodeCode());
            fu.setUserType(FlowAssigneeType.USER.name());
            fu.setUserId(String.valueOf(dto.getTargetUserId()));
            fu.setUserName(dto.getTargetUserName());
            fu.setProcessed(0);
            fu.setTenantId(task.getTenantId());
            fu.setProviderTraceId(task.getProviderTraceId());
            userMapper.insert(fu);
            taskMapper.updateApproveFinished(task.getId(), task.getApproveFinished());
            // approveCount +1
            task.setApproveCount((task.getApproveCount() == null ? 0 : task.getApproveCount()) + 1);
            taskMapper.updateById(task);
        }
        audit(task, "COUNTERSIGN_BEFORE", dto.getUserId(), dto.getTargetUserId(), dto.getComment());
        log.info("[Flow] 前加签: taskId={} → 新增审批人={}", task.getId(), dto.getTargetUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void countersignAfter(FlowTaskOperateDTO dto) {
        FlowTaskDO task = getTaskOrThrow(dto.getTaskId());
        if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "任务已完成，不可加签");
        }
        // 后加签：在当前节点通过后、下一节点前插入临时审批人
        // 实现：在 ext 字段记录后加签人，pass 完成后由 advancer 处理
        // 简化实现：直接在当前节点增加一个审批人（同前加签）
        countersignBefore(dto);
        audit(task, "COUNTERSIGN_AFTER", dto.getUserId(), dto.getTargetUserId(), dto.getComment());
    }

    // ============================== 催办（P1-9） ==============================

    @Override
    public List<String> urge(Long instanceId, Long operatorId, String comment) {
        List<FlowTaskDO> pendingTasks = taskMapper.selectPendingByInstance(instanceId);
        List<String> urged = new ArrayList<>();
        for (FlowTaskDO task : pendingTasks) {
            urged.add(task.getAssigneeId());
            audit(task, "URGE", operatorId, null, comment);
        }
        log.info("[Flow] 催办: instanceId={} 被催办人={}", instanceId, urged);
        return urged;
    }

    // ============================== 取消 / 查询 ==============================

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
    public List<FlowTaskDO> listTodoByUser(Long userId, List<String> roleCodes,
                                            List<String> deptIds, Long tenantId) {
        Long tid = tenantId == null ? 1L : tenantId;
        Set<FlowTaskDO> result = new LinkedHashSet<>();
        // 1. 直接分配给该用户的任务
        result.addAll(taskMapper.selectTodoByAssignee(String.valueOf(userId), tid));
        // 2. 通过 pmis_flow_user 关联的任务
        List<Long> taskIds = userMapper.selectTaskIdsByUser(String.valueOf(userId), tid);
        if (taskIds != null && !taskIds.isEmpty()) {
            for (Long tid2 : taskIds) {
                FlowTaskDO t = taskMapper.selectById(tid2);
                if (t != null && !FlowTaskStatus.valueOf(t.getTaskStatus()).isFinished()) {
                    result.add(t);
                }
            }
        }
        // 3. ROLE/DEPT 匹配
        if (roleCodes != null) {
            for (String rc : roleCodes) {
                result.addAll(taskMapper.selectTodoByAssignee(rc, tid));
            }
        }
        if (deptIds != null) {
            for (String did : deptIds) {
                result.addAll(taskMapper.selectTodoByAssignee(did, tid));
            }
        }
        return new ArrayList<>(result);
    }

    @Override
    public List<FlowTaskDO> listDoneByAssignee(String assigneeId, Long tenantId) {
        // P0-3: 改查历史表
        Long tid = tenantId == null ? 1L : tenantId;
        List<FlowHisTaskDO> hisTasks = hisTaskMapper.selectDoneByAssignee(assigneeId, tid);
        List<FlowTaskDO> result = new ArrayList<>();
        for (FlowHisTaskDO his : hisTasks) {
            FlowTaskDO t = new FlowTaskDO();
            t.setId(his.getTaskId());
            t.setInstanceId(his.getInstanceId());
            t.setFlowCode(his.getFlowCode());
            t.setDefinitionId(his.getDefinitionId());
            t.setNodeCode(his.getNodeCode());
            t.setNodeName(his.getNodeName());
            t.setNodeType(his.getNodeType());
            t.setBusinessType(his.getBusinessType());
            t.setBusinessId(his.getBusinessId());
            t.setBusinessNo(his.getBusinessNo());
            t.setFlowName(his.getFlowName());
            t.setTitle(his.getTitle());
            t.setAssigneeType(his.getAssigneeType());
            t.setAssigneeId(his.getAssigneeId());
            t.setAssigneeName(his.getAssigneeName());
            t.setPerformType(his.getPerformType());
            t.setTaskStatus(his.getTaskStatus());
            t.setComment(his.getComment());
            t.setCreatedAt(his.getCreatedAt());
            t.setClaimAt(his.getClaimAt());
            t.setFinishAt(his.getFinishAt());
            t.setDurationMs(his.getDurationMs());
            result.add(t);
        }
        return result;
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

    // ============================== 会签推进逻辑 ==============================

    /** OR 或签：直接完成 + 推进 */
    private void doPassAndAdvance(FlowTaskDO task, FlowInstanceDO instance,
                                   Map<String, Object> vars, FlowTaskOperateDTO dto) {
        completeAndArchive(task, dto.getComment());
        List<FlowNodeDO> nextNodes = advancer.advance(instance, task.getNodeCode(),
                "PASS", null, vars);
        ((FlowInstanceServiceImpl) instanceService).generateTasksForNodes(
                task.getInstanceId(), nextNodes, vars);
        updateInstanceNode(instance, nextNodes);
        fireTaskCompleted(task.getId(), "PASS", vars);
        audit(task, "PASS", dto.getUserId(), null, dto.getComment());
        log.info("[Flow] 通过任务: taskId={} action=PASS next={}", task.getId(), nextNodes.size());
    }

    /** P0-1: 并行会签 — 全部通过才推进 */
    private void doParallelPass(FlowTaskDO task, FlowInstanceDO instance,
                                 Map<String, Object> vars, FlowTaskOperateDTO dto) {
        int finished = (task.getApproveFinished() == null ? 0 : task.getApproveFinished()) + 1;
        int required = task.getApproveCount() == null ? 1 : task.getApproveCount();
        taskMapper.updateApproveFinished(task.getId(), finished);
        task.setApproveFinished(finished);
        if (finished < required) {
            // 未全部通过：任务保持 PENDING，不推进
            audit(task, "PARALLEL_PASS", dto.getUserId(), null, dto.getComment());
            log.info("[Flow] 并行会签部分通过: taskId={} finished={}/{}", task.getId(), finished, required);
            return;
        }
        // 全部通过：完成 + 推进 + 跳过剩余
        taskMapper.skipByNode(instance.getId(), task.getNodeCode(),
                FlowTaskStatus.SKIPPED.name());
        completeAndArchive(task, dto.getComment());
        List<FlowNodeDO> nextNodes = advancer.advance(instance, task.getNodeCode(),
                "PASS", null, vars);
        ((FlowInstanceServiceImpl) instanceService).generateTasksForNodes(
                task.getInstanceId(), nextNodes, vars);
        updateInstanceNode(instance, nextNodes);
        fireTaskCompleted(task.getId(), "PASS", vars);
        audit(task, "PARALLEL_PASS_ALL", dto.getUserId(), null, dto.getComment());
        log.info("[Flow] 并行会签全部通过: taskId={} finished={}/{}", task.getId(), finished, required);
    }

    /** P1-12: 顺序会签 — 按序逐一处理 */
    private void doSequentialPass(FlowTaskDO task, FlowInstanceDO instance,
                                   Map<String, Object> vars, FlowTaskOperateDTO dto) {
        int finished = (task.getApproveFinished() == null ? 0 : task.getApproveFinished()) + 1;
        int required = task.getApproveCount() == null ? 1 : task.getApproveCount();
        taskMapper.updateApproveFinished(task.getId(), finished);
        task.setApproveFinished(finished);
        if (finished < required) {
            // 还有下一个用户：切换办理人
            List<FlowUserDO> unprocessed = userMapper.selectUnprocessedByInstanceAndNode(
                    instance.getId(), task.getNodeCode());
            if (!unprocessed.isEmpty()) {
                FlowUserDO next = unprocessed.get(0);
                taskMapper.updateAssignee(task.getId(), next.getUserId(), next.getUserName(),
                        FlowAssigneeType.USER.name());
                audit(task, "SEQUENTIAL_PASS", dto.getUserId(), null, dto.getComment());
                log.info("[Flow] 顺序会签切换下一人: taskId={} → {}", task.getId(), next.getUserId());
                return;
            }
        }
        // 全部完成：完成 + 推进
        completeAndArchive(task, dto.getComment());
        List<FlowNodeDO> nextNodes = advancer.advance(instance, task.getNodeCode(),
                "PASS", null, vars);
        ((FlowInstanceServiceImpl) instanceService).generateTasksForNodes(
                task.getInstanceId(), nextNodes, vars);
        updateInstanceNode(instance, nextNodes);
        fireTaskCompleted(task.getId(), "PASS", vars);
        audit(task, "SEQUENTIAL_PASS_ALL", dto.getUserId(), null, dto.getComment());
        log.info("[Flow] 顺序会签全部通过: taskId={}", task.getId());
    }

    /** P1-12: 票签 — 达到阈值即推进 */
    private void doVotePass(FlowTaskDO task, FlowInstanceDO instance,
                             Map<String, Object> vars, FlowTaskOperateDTO dto) {
        int finished = (task.getApproveFinished() == null ? 0 : task.getApproveFinished()) + 1;
        int required = task.getApproveCount() == null ? 1 : task.getApproveCount();
        taskMapper.updateApproveFinished(task.getId(), finished);
        task.setApproveFinished(finished);
        // 阈值：默认 50% + 1（即过半数通过）
        int threshold = (required / 2) + 1;
        if (finished < threshold) {
            audit(task, "VOTE_PASS", dto.getUserId(), null, dto.getComment());
            log.info("[Flow] 票签部分通过: taskId={} finished={}/{}", task.getId(), finished, threshold);
            return;
        }
        // 达到阈值：完成 + 推进 + 跳过剩余
        taskMapper.skipByNode(instance.getId(), task.getNodeCode(),
                FlowTaskStatus.SKIPPED.name());
        completeAndArchive(task, dto.getComment());
        List<FlowNodeDO> nextNodes = advancer.advance(instance, task.getNodeCode(),
                "PASS", null, vars);
        ((FlowInstanceServiceImpl) instanceService).generateTasksForNodes(
                task.getInstanceId(), nextNodes, vars);
        updateInstanceNode(instance, nextNodes);
        fireTaskCompleted(task.getId(), "PASS", vars);
        audit(task, "VOTE_PASS_THRESHOLD", dto.getUserId(), null, dto.getComment());
        log.info("[Flow] 票签达到阈值: taskId={} finished={}/{}", task.getId(), finished, threshold);
    }

    // ============================== 私有辅助 ==============================

    private FlowTaskDO getTaskOrThrow(Long id) {
        FlowTaskDO task = taskMapper.selectById(id);
        if (task == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "任务不存在: " + id);
        }
        return task;
    }

    private void completeAndArchive(FlowTaskDO task, String comment) {
        LocalDateTime now = LocalDateTime.now();
        Long durationMs = task.getCreatedAt() == null
                ? null
                : Duration.between(task.getCreatedAt(), now).toMillis();
        taskMapper.completeTask(task.getId(), FlowTaskStatus.COMPLETED.name(),
                comment, now, durationMs);
        task.setTaskStatus(FlowTaskStatus.COMPLETED.name());
        task.setComment(comment);
        task.setFinishAt(now);
        task.setDurationMs(durationMs);
        archiveTask(task, FlowTaskStatus.COMPLETED);
    }

    private void updateInstanceNode(FlowInstanceDO instance, List<FlowNodeDO> nextNodes) {
        if (!nextNodes.isEmpty() && nextNodes.get(0).getNodeType()
                != FlowNodeType.END.getCode()) {
            instanceMapper.updateStatus(instance.getId(), instance.getFlowStatus(),
                    nextNodes.get(0).getNodeCode(), nextNodes.get(0).getNodeName(),
                    null, null);
        }
    }

    /** 展开办理人为用户列表 */
    private List<String> expandAssignees(FlowNodeDO node, Map<String, Object> variables) {
        String perm = node.getPermissionFlag();
        if (!StringUtils.hasText(perm)) {
            return Collections.emptyList();
        }
        String resolved = variableStrategy.resolveAssignee(perm, variables);
        if (resolved == null) {
            return Collections.emptyList();
        }
        // 尝试通过 SPI 展开
        List<Long> userIds = assigneeResolver.expandUsers(resolved, variables);
        if (userIds != null && !userIds.isEmpty()) {
            return userIds.stream().map(String::valueOf).toList();
        }
        return Collections.emptyList();
    }

    /** 解析会签类型 */
    private FlowPerformType resolvePerformType(FlowNodeDO node) {
        if (node.getExt() != null) {
            try {
                Map<String, Object> ext = JSON.parseObject(node.getExt(), Map.class);
                String pt = (String) ext.get("performType");
                if (pt != null) {
                    return FlowPerformType.valueOf(pt);
                }
            } catch (Exception ignored) {
            }
        }
        return FlowPerformType.OR;
    }

    @SuppressWarnings("unchecked")
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
            task.setAssigneeType(FlowAssigneeType.INITIATOR.name());
            task.setAssigneeId(instance != null && instance.getInitiatorId() != null
                    ? String.valueOf(instance.getInitiatorId())
                    : String.valueOf(task.getId()));
            task.setAssigneeName("INITIATOR");
            return;
        }
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
        } else if (resolved.startsWith("leader:")) {
            task.setAssigneeType(FlowAssigneeType.LEADER.name());
            task.setAssigneeId(resolved.substring(7));
        } else if (resolved.startsWith("position:")) {
            task.setAssigneeType(FlowAssigneeType.POSITION.name());
            task.setAssigneeId(resolved.substring(9));
        } else if (resolved.startsWith("${")) {
            task.setAssigneeType(FlowAssigneeType.SPEL.name());
            task.setAssigneeId(resolved);
        } else {
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

    // ============================== 事件 + 审计 ==============================

    private void fireEvent(java.util.function.Consumer<FlowEventListener> action, Long taskId) {
        if (eventListeners == null) return;
        for (FlowEventListener listener : eventListeners) {
            try {
                action.accept(listener);
            } catch (Exception e) {
                log.warn("[Flow] 事件监听器异常: listener={} err={}",
                        listener.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    private void fireTaskCompleted(Long taskId, String action, Map<String, Object> vars) {
        fireEvent(l -> l.onTaskCompleted(taskId, action, vars), taskId);
    }

    private void fireInstanceRejected(Long instanceId, String reason) {
        if (eventListeners == null) return;
        for (FlowEventListener listener : eventListeners) {
            try {
                listener.onInstanceRejected(instanceId, reason);
            } catch (Exception e) {
                log.warn("[Flow] onInstanceRejected 异常: {}", e.getMessage());
            }
        }
    }

    private void audit(FlowTaskDO task, String action, Long operatorId,
                       Long targetId, String comment) {
        try {
            FlowAuditLogDO log = new FlowAuditLogDO();
            log.setInstanceId(task.getInstanceId());
            log.setTaskId(task.getId());
            log.setFlowCode(task.getFlowCode());
            log.setBusinessType(task.getBusinessType());
            log.setBusinessId(task.getBusinessId());
            log.setNodeCode(task.getNodeCode());
            log.setNodeName(task.getNodeName());
            log.setAction(action);
            log.setOperatorId(operatorId);
            log.setTargetId(targetId);
            log.setComment(comment);
            log.setOperatedAt(LocalDateTime.now());
            log.setTenantId(task.getTenantId());
            log.setProviderTraceId(task.getProviderTraceId());
            auditLogMapper.insert(log);
        } catch (Exception e) {
            FlowTaskServiceImpl.log.warn("[Flow] 审计日志写入失败: {}", e.getMessage());
        }
    }
}
