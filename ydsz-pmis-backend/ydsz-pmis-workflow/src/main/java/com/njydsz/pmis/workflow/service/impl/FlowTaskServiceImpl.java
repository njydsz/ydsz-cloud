package com.njydsz.pmis.workflow.service.impl;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.dto.FlowAssigneeDTO;
import com.njydsz.pmis.workflow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.engine.FlowAssigneeResolver;
import com.njydsz.pmis.workflow.engine.FlowEventContext;
import com.njydsz.pmis.workflow.engine.FlowEventListener;
import com.njydsz.pmis.workflow.engine.FlowUrgeLimiter;
import com.njydsz.pmis.workflow.engine.FlowVariableStrategy;
import com.njydsz.pmis.workflow.engine.FlowWorkflowEvent;
import com.njydsz.pmis.workflow.entity.*;
import com.njydsz.pmis.workflow.enums.*;
import com.njydsz.pmis.workflow.mapper.*;
import com.njydsz.pmis.workflow.metrics.FlowMetrics;
import com.njydsz.pmis.workflow.service.FlowDelegateAuthService;
import com.njydsz.pmis.workflow.service.FlowInstanceService;
import com.njydsz.pmis.workflow.service.FlowTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    /** P2-35: Spring 事件发布器，用于异步事件机制（测试环境可能为 null） */
    private final ApplicationEventPublisher eventPublisher;
    /** P0-2: 催办限流器（Redis Lua 冷却 30 分钟） */
    private final FlowUrgeLimiter urgeLimiter;
    /** P1-4: 长期授权委派服务（任务创建时自动改写 assignee） */
    private final FlowDelegateAuthService delegateAuthService;
    /** P1-4: 委派代理日志 Mapper（用于记录代理操作审计） */
    private final com.njydsz.pmis.workflow.mapper.FlowDelegateLogMapper delegateLogMapper;
    /** P1-6: SLA 服务（任务创建时应用 SLA 配置 + 超时自动策略） */
    @org.springframework.context.annotation.Lazy
    private final com.njydsz.pmis.workflow.service.FlowSlaService slaService;
    /** P1-7: 待办数 WebSocket 推送服务（任务创建/通过/驳回时实时推送给办理人） */
    @org.springframework.context.annotation.Lazy
    private final com.njydsz.pmis.workflow.service.FlowTodoCountPushService todoCountPushService;
    /** P2-3: Prometheus 指标收集（可能为 null：测试环境） */
    private final FlowMetrics flowMetrics;

    // ============================== 创建任务 ==============================

    @Override
    public FlowTaskDO getById(Long taskId) {
        // P2-20: 任务详情查询，委托 BaseMapper 自带 selectById
        if (taskId == null) {
            return null;
        }
        return taskMapper.selectById(taskId);
    }

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
        // P2-3: Prometheus 指标 — 任务创建（在 insert 前预先计数，便于回滚感知）
        if (flowMetrics != null) {
            flowMetrics.incTaskCreated(instance.getFlowCode(), node.getNodeCode());
        }
        // P1-6: 应用 SLA 配置 — 解析 node.slaConfig 设置 dueAt
        if (slaService != null) {
            slaService.applySlaConfig(task, node);
        }

        // 设置首个办理人
        if (userIds.isEmpty()) {
            // GAP-P0: 审批人为空兜底处理 — 读取 node.ext 中的 emptyStrategy 配置
            Map<String, Object> extConfig = parseExtConfig(node.getExt());
            String emptyStrategy = (String) extConfig.getOrDefault("emptyStrategy", "FALLBACK");

            switch (emptyStrategy) {
                case "AUTO_PASS" -> {
                    // 自动通过：创建任务后立即完成并推进
                    task.setAssigneeType(FlowAssigneeType.USER.name());
                    task.setAssigneeId("0");
                    task.setAssigneeName("SYSTEM_AUTO_PASS");
                    task.setTaskStatus(FlowTaskStatus.COMPLETED.name());
                    LocalDateTime now = LocalDateTime.now();
                    task.setFinishAt(now);
                    task.setDurationMs(0L);
                    taskMapper.insert(task);
                    archiveTask(task, FlowTaskStatus.COMPLETED);
                    audit(task, "AUTO_PASS", 0L, null, "审批人为空，自动通过");
                    log.info("[Flow] 审批人为空自动通过: instanceId={} node={}",
                            instanceId, node.getNodeCode());
                    // 推进到下一节点（递归深度保护）
                    advanceAfterAutoPass(instance, node, variables);
                    return task.getId();
                }
                case "TRANSFER_ADMIN" -> {
                    Long adminUserId = parseLongConfig(extConfig, "adminUserId", 1L);
                    task.setAssigneeType(FlowAssigneeType.USER.name());
                    task.setAssigneeId(String.valueOf(adminUserId));
                    task.setAssigneeName("ADMIN_FALLBACK");
                    taskMapper.insert(task);
                    log.info("[Flow] 审批人为空转管理员: instanceId={} node={} adminId={}",
                            instanceId, node.getNodeCode(), adminUserId);
                }
                case "ASSIGN_SPECIFIED" -> {
                    Long specifiedUserId = parseLongConfig(extConfig, "specifiedUserId", 1L);
                    task.setAssigneeType(FlowAssigneeType.USER.name());
                    task.setAssigneeId(String.valueOf(specifiedUserId));
                    task.setAssigneeName("SPECIFIED_FALLBACK");
                    taskMapper.insert(task);
                    log.info("[Flow] 审批人为空指定人员: instanceId={} node={} userId={}",
                            instanceId, node.getNodeCode(), specifiedUserId);
                }
                default -> {
                    // 默认：回退到原有 resolveAssignee 逻辑
                    taskMapper.insert(task);
                    resolveAssignee(task, node, variables, null, instance);
                    taskMapper.updateById(task);
                }
            }
        } else {
            // 展开成功：设置第一个用户为 assignee，其余写入 pmis_flow_user
            task.setAssigneeType(FlowAssigneeType.USER.name());
            task.setAssigneeId(userIds.get(0));
            task.setAssigneeName("USER:" + userIds.get(0));
            // P1-5: 解析 node.ext.votePassRate / userWeights，配置加权票签
            applyVoteConfig(task, node);
            // GAP-V2-05: 审批人自动去重 — 仅 OR（单人审批）触发，会签/票签不去重
            if (performType == FlowPerformType.OR) {
                Long dedupTaskId = tryAutoDedup(task, instance, node, variables, userIds.get(0));
                if (dedupTaskId != null) {
                    return dedupTaskId;
                }
            }
            taskMapper.insert(task);
            // 写入 pmis_flow_user
            // P1-5: 从 ext 中读取 userWeights（userId -> weight）映射
            java.util.Map<String, Integer> userWeights = parseUserWeights(node.getExt());
            for (String uid : userIds) {
                FlowUserDO fu = new FlowUserDO();
                fu.setTaskId(task.getId());
                fu.setInstanceId(instanceId);
                fu.setNodeCode(node.getNodeCode());
                fu.setUserType(FlowAssigneeType.USER.name());
                fu.setUserId(uid);
                fu.setUserName("USER:" + uid);
                fu.setProcessed(0);
                fu.setWeight(userWeights == null ? 1
                        : userWeights.getOrDefault(uid, 1));
                fu.setTenantId(instance.getTenantId());
                fu.setProviderTraceId(instance.getProviderTraceId());
                userMapper.insert(fu);
            }
        }
        log.info("[Flow] 创建任务: instanceId={} node={} performType={} assigneeCount={}",
                instanceId, node.getNodeCode(), performType, userIds.size());
        // P1-4: 应用长期授权委派 — 如果首个 assignee 命中代理规则，改写为 delegate
        applyDelegateRedirect(task, instance, node);
        fireEvent(l -> l.onTaskCreated(task.getId()), task.getId());
        // P2-35: 发布 Spring 异步事件
        publishWorkflowEvent("TASK_CREATED", instanceId, task.getId());
        // P1-7: WebSocket 推送任务分配 + 待办数更新
        if (todoCountPushService != null) {
            todoCountPushService.pushTaskAssigned(task);
        }
        return task.getId();
    }

    // ============================== P1-4: 长期授权委派改写 ==============================

    /**
     * 应用长期授权委派：检查 task.assigneeId 是否为某代理规则的 ownerUserId，
     * 如果是则改写 assigneeId 为 delegateUserId，并将原 owner 写入 assignorId。
     *
     * <p>改写是静默的（写日志 + audit），不抛异常，避免拖垮主流程。
     *
     * @param task     已 setAssigneeId 的任务（未持久化 or 已持久化均可）
     * @param instance 流程实例
     * @param node     当前节点
     */
    private void applyDelegateRedirect(FlowTaskDO task,
                                       FlowInstanceDO instance,
                                       FlowNodeDO node) {
        try {
            if (delegateAuthService == null) {
                return;
            }
            String currentAssigneeId = task.getAssigneeId();
            if (!StringUtils.hasText(currentAssigneeId)) {
                return;
            }
            Long currentUserId;
            try {
                currentUserId = Long.parseLong(currentAssigneeId.trim());
            } catch (NumberFormatException nfe) {
                return; // 非纯数字 assignee（INITIATOR/SYSTEM_*）不参与代理
            }
            FlowDelegateAuthDO matched = delegateAuthService.matchAuth(
                    instance.getTenantId(), currentUserId,
                    instance.getFlowCode(), node.getNodeCode());
            if (matched == null) {
                return;
            }
            // 改写：assignorId 记原办理人，assigneeId 改为 delegate
            task.setAssignorId(currentUserId);
            task.setAssignorName(matched.getOwnerUserName());
            task.setAssigneeId(String.valueOf(matched.getDelegateUserId()));
            task.setAssigneeName(matched.getDelegateUserName());
            taskMapper.updateById(task);
            // 写审计日志
            audit(task, "DELEGATE_AUTH_APPLIED", matched.getDelegateUserId(),
                    currentUserId,
                    "长期授权委派生效: " + matched.getId() + " (" + matched.getScopeType() + ")");
            log.info("[Flow] 长期授权委派改写: taskId={} owner={} → delegate={} authId={} scope={}",
                    task.getId(), currentUserId, matched.getDelegateUserId(),
                    matched.getId(), matched.getScopeType());
        } catch (Exception e) {
            log.error("[Flow] 长期授权委派改写异常: taskId={} err={}",
                    task == null ? "null" : task.getId(), e.getMessage(), e);
        }
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
        // P1-4: 记录代理签收日志
        logDelegateOperation(task, "CLAIM", "ACT");
        log.info("[Flow] 签收任务: taskId={} userId={}", taskId, userId);
        // P2-3: Prometheus 指标
        if (flowMetrics != null) {
            flowMetrics.incTaskClaimed(task.getFlowCode(), task.getNodeCode());
        }
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
            // P1-4: 记录委派回归的代理操作日志
            logDelegateOperation(task, "DELEGATE_RETURN", "ACT");
            task.setAssigneeId(String.valueOf(task.getAssignorId()));
            task.setAssigneeName(task.getAssignorName());
            task.setAssignorId(null);
            task.setAssignorName(null);
            task.setTaskStatus(FlowTaskStatus.CLAIMED.name());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            audit(task, "DELEGATE_RETURN", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
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
            case WEIGHTED_VOTE -> doWeightedVotePass(task, instance, mergedVars, dto);
        }
        // P1-7: WebSocket 推送任务完成 + 操作人待办数更新
        if (todoCountPushService != null) {
            todoCountPushService.pushTaskCompleted(task, dto.getUserId());
        }
        // P2-3: Prometheus 指标 — 任务通过 + 耗时
        if (flowMetrics != null) {
            flowMetrics.incTaskPassed(task.getFlowCode(), task.getNodeCode());
            flowMetrics.recordTaskDuration(task, "PASSED");
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
            audit(task, "REJECT", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
            // P2-3: Prometheus 指标 — 任务驳回 + 实例 REJECTED 终止
            if (flowMetrics != null) {
                flowMetrics.incTaskRejected(task.getFlowCode(), task.getNodeCode());
                flowMetrics.recordTaskDuration(task, "REJECTED");
                flowMetrics.incInstanceFinished(instance.getFlowCode(), "REJECTED");
                flowMetrics.recordInstanceDuration(instance, "REJECTED");
            }
            return;
        }
        instanceService.generateTasksForNodes(
                instance.getId(), rejectTargets, mergedVars);
        instanceMapper.updateStatus(instance.getId(), instance.getFlowStatus(),
                rejectTargets.get(0).getNodeCode(), rejectTargets.get(0).getNodeName(),
                null, null);
        audit(task, "REJECT", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
        log.info("[Flow] 退回任务: taskId={} target={}", task.getId(),
                rejectTargets.get(0).getNodeCode());
        // P1-7: WebSocket 推送任务驳回 + 操作人待办数更新
        if (todoCountPushService != null) {
            todoCountPushService.pushTaskRejected(task, dto.getUserId(), dto.getComment());
        }
        // P2-3: Prometheus 指标 — 任务驳回 + 耗时
        if (flowMetrics != null) {
            flowMetrics.incTaskRejected(task.getFlowCode(), task.getNodeCode());
            flowMetrics.recordTaskDuration(task, "REJECTED");
        }
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
        // P2-3: Prometheus 指标
        if (flowMetrics != null) {
            flowMetrics.incTaskTransferred(task.getFlowCode(), task.getNodeCode());
        }
        // P2-34: 触发 onTaskTransferred 事件
        fireEvent(l -> l.onTaskTransferred(task.getId(), originalAssignorId, dto.getTargetUserId()),
                task.getId());
        // P2-35: 发布 Spring 异步事件
        publishWorkflowEvent("TASK_TRANSFERRED", task.getInstanceId(), task.getId());
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
        // P2-3: Prometheus 指标
        if (flowMetrics != null) {
            flowMetrics.incTaskDelegated(task.getFlowCode(), task.getNodeCode());
        }
        // P2-34: 触发 onTaskDelegated 事件
        fireEvent(l -> l.onTaskDelegated(task.getId(), originalAssigneeId, dto.getTargetUserId()),
                task.getId());
        // P2-35: 发布 Spring 异步事件
        publishWorkflowEvent("TASK_DELEGATED", task.getInstanceId(), task.getId());
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
        // P2-34: 触发 onTaskCountersigned 事件
        fireEvent(l -> l.onTaskCountersigned(task.getId(), dto.getTargetUserId(), "BEFORE"),
                task.getId());
        // P2-35: 发布 Spring 异步事件
        publishWorkflowEvent("TASK_COUNTERSIGNED", task.getInstanceId(), task.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void countersignAfter(FlowTaskOperateDTO dto) {
        FlowTaskDO task = getTaskOrThrow(dto.getTaskId());
        if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "任务已完成，不可加签");
        }
        // P2-29: 后加签真实实现 — 当前审批人通过后，新加签人需要审批，两人都通过后才推进到下一节点
        // 实现方式：
        // 1. 将当前任务切换为顺序会签（performType=SEQUENTIAL）
        // 2. approveCount +1（当前人 + 加签人）
        // 3. 新增审批人写入 pmis_flow_user（processed=0）
        // 这样当前审批人 pass 时，doSequentialPass 检测到 approveFinished < approveCount，
        // 会切换到加签人而非推进到下一节点；加签人 pass 后才真正推进
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
            // 切换为顺序会签：当前人 pass 后切换到加签人，加签人 pass 后才推进
            task.setPerformType(FlowPerformType.SEQUENTIAL.name());
            task.setApproveCount((task.getApproveCount() == null ? 0 : task.getApproveCount()) + 1);
            taskMapper.updateById(task);
        }
        audit(task, "COUNTERSIGN_AFTER", dto.getUserId(), dto.getTargetUserId(), dto.getComment());
        log.info("[Flow] 后加签: taskId={} → 新增审批人={} (切换为顺序会签)",
                task.getId(), dto.getTargetUserId());
        // P2-34: 触发 onTaskCountersigned 事件
        fireEvent(l -> l.onTaskCountersigned(task.getId(), dto.getTargetUserId(), "AFTER"),
                task.getId());
        // P2-35: 发布 Spring 异步事件
        publishWorkflowEvent("TASK_COUNTERSIGNED", task.getInstanceId(), task.getId());
    }

    // ============================== GAP-P1: 减签 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void countersignRemove(FlowTaskOperateDTO dto) {
        FlowTaskDO task = getTaskOrThrow(dto.getTaskId());
        if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "任务已完成，不可减签");
        }
        if (dto.getTargetUserId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "减签需指定 targetUserId");
        }
        // 从 pmis_flow_user 中删除指定用户
        Map<String, Object> deleteMap = new HashMap<>();
        deleteMap.put("instance_id", task.getInstanceId());
        deleteMap.put("node_code", task.getNodeCode());
        deleteMap.put("user_id", String.valueOf(dto.getTargetUserId()));
        int deleted = userMapper.deleteByMap(deleteMap);
        if (deleted == 0) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "未找到待减签用户: userId=" + dto.getTargetUserId());
        }
        // approveCount -1，但不低于 1
        int currentCount = task.getApproveCount() == null ? 1 : task.getApproveCount();
        task.setApproveCount(Math.max(1, currentCount - 1));
        taskMapper.updateById(task);
        audit(task, "COUNTERSIGN_REMOVE", dto.getUserId(), dto.getTargetUserId(), dto.getComment());
        log.info("[Flow] 减签: taskId={} → 移除审批人={} deleted={}",
                task.getId(), dto.getTargetUserId(), deleted);
        fireEvent(l -> l.onTaskCountersigned(task.getId(), dto.getTargetUserId(), "REMOVE"),
                task.getId());
        publishWorkflowEvent("TASK_COUNTERSIGNED", task.getInstanceId(), task.getId());
    }

    // ============================== GAP-P2: 已阅/沟通 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long taskId, Long userId) {
        FlowTaskDO task = getTaskOrThrow(taskId);
        audit(task, "READ", userId, null, null);
        log.info("[Flow] 已阅: taskId={} userId={}", taskId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void communicate(FlowTaskOperateDTO dto) {
        FlowTaskDO task = getTaskOrThrow(dto.getTaskId());
        audit(task, "COMMUNICATE", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
        log.info("[Flow] 沟通: taskId={} userId={} comment={}",
                dto.getTaskId(), dto.getUserId(), dto.getComment());
    }

    // ======================== P0-03: 暂存待审 / 追加处理人 ========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDraft(FlowTaskOperateDTO dto) {
        FlowTaskDO task = getTaskOrThrow(dto.getTaskId());
        if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "任务已完成，不可暂存");
        }
        // 保存审批意见草稿到 comment 字段，不改变任务状态
        task.setComment(dto.getComment());
        taskMapper.updateById(task);
        audit(task, "SAVE_DRAFT", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
        log.info("[Flow] 暂存待审: taskId={} userId={}", dto.getTaskId(), dto.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addApprover(FlowTaskOperateDTO dto) {
        FlowTaskDO task = getTaskOrThrow(dto.getTaskId());
        if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "任务已完成，不可追加处理人");
        }
        if (dto.getTargetUserId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "追加处理人需指定 targetUserId");
        }
        // 向 pmis_flow_user 插入新审批人
        FlowUserDO fu = new FlowUserDO();
        fu.setTaskId(task.getId());
        fu.setUserId(String.valueOf(dto.getTargetUserId()));
        fu.setProcessed(0);
        fu.setWeight(1); // 默认权重 1
        userMapper.insert(fu);
        // approveCount +1
        int currentCount = task.getApproveCount() == null ? 1 : task.getApproveCount();
        task.setApproveCount(currentCount + 1);
        taskMapper.updateById(task);
        audit(task, "ADD_APPROVER", dto.getUserId(), dto.getTargetUserId(), dto.getComment());
        log.info("[Flow] 追加处理人: taskId={} targetUserId={}", task.getId(), dto.getTargetUserId());
        fireEvent(l -> l.onTaskCountersigned(task.getId(), dto.getTargetUserId(), "ADD"),
                task.getId());
        publishWorkflowEvent("TASK_ADD_APPROVER", task.getInstanceId(), task.getId());
    }

    // ============================== 催办（P1-9） ==============================

    @Override
    public List<String> urge(Long instanceId, Long operatorId, String comment) {
        // P0-2: 催办限流：同一催办人对同一实例 30 分钟内只允许一次
        if (operatorId != null && instanceId != null
                && !urgeLimiter.tryAcquire(operatorId, instanceId, "INSTANCE")) {
            throw new BizException(BizErrorCode.RATE_LIMIT,
                    "催办过于频繁，请稍后再试（同一实例 30 分钟内仅可催办一次）");
        }
        List<FlowTaskDO> pendingTasks = taskMapper.selectPendingByInstance(instanceId);
        List<String> urged = new ArrayList<>();
        for (FlowTaskDO task : pendingTasks) {
            urged.add(task.getAssigneeId());
            audit(task, "URGE", operatorId, null, comment);
        }
        log.info("[Flow] 催办: instanceId={} 被催办人={}", instanceId, urged);
        // P2-3: Prometheus 指标 — 催办
        if (flowMetrics != null) {
            // 用 flowCode 维度计数（如能查到实例就用实例的 code，否则用 unknown）
            try {
                FlowInstanceDO ins = instanceMapper.selectById(instanceId);
                if (ins != null) {
                    flowMetrics.incTaskUrged(ins.getFlowCode());
                } else {
                    flowMetrics.incTaskUrged("unknown");
                }
            } catch (Exception e) {
                flowMetrics.incTaskUrged("unknown");
            }
        }
        // P2-34: 触发 onTaskUrged 事件（实例级催办，taskId 传 null）
        fireEvent(l -> l.onTaskUrged(instanceId, null), null);
        // P2-35: 发布 Spring 异步事件
        publishWorkflowEvent("TASK_URGED", instanceId, null);
        return urged;
    }

    // ============================== 自由跳转（P2-25） ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void jump(FlowTaskOperateDTO dto) {
        FlowTaskDO task = getTaskOrThrow(dto.getTaskId());
        if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "任务已完成，不可跳转: " + task.getTaskStatus());
        }
        if (!StringUtils.hasText(dto.getTargetNodeCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "跳转目标节点不能为空");
        }
        FlowInstanceDO instance = instanceMapper.selectById(task.getInstanceId());
        if (instance == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "实例不存在: " + task.getInstanceId());
        }
        // 校验目标节点存在
        FlowNodeDO targetNode = nodeMapper.selectByCode(task.getDefinitionId(), dto.getTargetNodeCode());
        if (targetNode == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "目标节点不存在: " + dto.getTargetNodeCode());
        }
        // 完成当前任务（状态 COMPLETED，审计 action=JUMP）
        completeAndArchive(task, dto.getComment());
        // 取消同实例其他 PENDING 任务
        taskMapper.cancelByInstance(instance.getId(), FlowTaskStatus.CANCELLED.name());
        // 更新实例当前节点为目标节点
        instanceMapper.updateStatus(instance.getId(), instance.getFlowStatus(),
                targetNode.getNodeCode(), targetNode.getNodeName(), null, null);
        // 在目标节点创建新任务
        Map<String, Object> vars = mergeVariables(instance, dto.getVariables());
        createTask(instance.getId(), targetNode, vars);
        // 触发任务完成事件
        fireTaskCompleted(task.getId(), "JUMP", vars);
        audit(task, "JUMP", dto.getUserId(), null, dto.getComment());
        log.info("[Flow] 自由跳转: taskId={} → targetNode={}", task.getId(), dto.getTargetNodeCode());
        // P2-34: 触发 onTaskJumped 事件
        fireEvent(l -> l.onTaskJumped(task.getId(), task.getNodeCode(), dto.getTargetNodeCode()),
                task.getId());
        // P2-35: 发布 Spring 异步事件
        publishWorkflowEvent("TASK_JUMPED", instance.getId(), task.getId());
    }

    // ============================== 批量审批（P2-26） ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchPass(List<Long> taskIds, Long userId, String comment) {
        if (taskIds == null || taskIds.isEmpty()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "taskIds 不能为空");
        }
        for (Long taskId : taskIds) {
            FlowTaskOperateDTO dto = new FlowTaskOperateDTO();
            dto.setTaskId(taskId);
            dto.setUserId(userId);
            dto.setComment(comment);
            dto.setAction("PASS");
            this.pass(dto);
        }
        log.info("[Flow] 批量审批: taskIds={} userId={} count={}", taskIds, userId, taskIds.size());
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
        // P2-16: 多租户上下文 - 入参优先，否则从 SecurityContext 获取
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return taskMapper.selectTodoByAssignee(assigneeId, tid);
    }

    @Override
    public List<FlowTaskDO> listTodoByUser(Long userId, List<String> roleCodes,
                                            List<String> deptIds, Long tenantId) {
        // P2-16: 多租户上下文 - 入参优先，否则从 SecurityContext 获取
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
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
        // P2-16: 多租户上下文 - 入参优先，否则从 SecurityContext 获取
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
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
    public PageResult<FlowTaskDO> listTodoByAssigneePage(String assigneeId, Long tenantId,
                                                          int page, int size) {
        // P2-17: 真分页（SQL LIMIT/OFFSET）
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        int safePage = Math.max(1, page);
        int safeSize = size > 0 ? size : 20;
        int offset = (safePage - 1) * safeSize;
        List<FlowTaskDO> list = taskMapper.selectTodoByAssigneePage(assigneeId, tid, offset, safeSize);
        long total = taskMapper.countTodoByAssignee(assigneeId, tid);
        return PageResult.of(list, total, safePage, safeSize);
    }

    @Override
    public PageResult<FlowTaskDO> listDoneByAssigneePage(String assigneeId, Long tenantId,
                                                          int page, int size) {
        // P2-17: 真分页（SQL LIMIT/OFFSET） — 走历史表
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        int safePage = Math.max(1, page);
        int safeSize = size > 0 ? size : 20;
        int offset = (safePage - 1) * safeSize;
        List<FlowHisTaskDO> hisTasks = hisTaskMapper.selectDoneByAssigneePage(assigneeId, tid, offset, safeSize);
        List<FlowTaskDO> list = new ArrayList<>();
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
            list.add(t);
        }
        long total = hisTaskMapper.countDoneByAssignee(assigneeId, tid);
        return PageResult.of(list, total, safePage, safeSize);
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

    // ============================== P2-31: 审批耗时统计 ==============================

    @Override
    public List<Map<String, Object>> nodeDurationStats(String flowCode, Long tenantId) {
        return hisTaskMapper.nodeDurationStats(flowCode, tenantId);
    }

    // ============================== P2-32: 超期任务统计 ==============================

    @Override
    public List<FlowTaskDO> listOverdue(String assigneeId, Long tenantId) {
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return taskMapper.selectOverdue(assigneeId, tid);
    }

    @Override
    public long countOverdue(String assigneeId, Long tenantId) {
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return taskMapper.countOverdue(assigneeId, tid);
    }

    // ============================== P2-33: 历史任务多维筛选分页 ==============================

    @Override
    public PageResult<FlowTaskDO> listDoneByAssigneePageMulti(String assigneeId, String businessType,
                                                               String flowCode, LocalDateTime startTime,
                                                               LocalDateTime endTime, Long tenantId,
                                                               int page, int size) {
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        int safePage = Math.max(1, page);
        int safeSize = size > 0 ? size : 20;
        int offset = (safePage - 1) * safeSize;
        List<FlowHisTaskDO> hisTasks = hisTaskMapper.selectDonePage(assigneeId, businessType,
                flowCode, startTime, endTime, tid, offset, safeSize);
        List<FlowTaskDO> list = new ArrayList<>();
        for (FlowHisTaskDO his : hisTasks) {
            list.add(hisToTask(his));
        }
        long total = hisTaskMapper.countDone(assigneeId, businessType, flowCode,
                startTime, endTime, tid);
        return PageResult.of(list, total, safePage, safeSize);
    }

    // ============================== P2-36: 超时标记 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void timeoutTask(Long taskId, String reason) {
        FlowTaskDO task = getTaskOrThrow(taskId);
        // 校验状态为 PENDING/CLAIMED
        String status = task.getTaskStatus();
        if (!FlowTaskStatus.PENDING.name().equals(status)
                && !FlowTaskStatus.CLAIMED.name().equals(status)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "任务状态不可标记超时: " + status);
        }
        // 更新任务状态为 TIMEOUT
        LocalDateTime now = LocalDateTime.now();
        Long durationMs = task.getCreatedAt() == null
                ? null
                : Duration.between(task.getCreatedAt(), now).toMillis();
        taskMapper.completeTask(task.getId(), FlowTaskStatus.TIMEOUT.name(),
                reason, now, durationMs);
        task.setTaskStatus(FlowTaskStatus.TIMEOUT.name());
        task.setComment(reason);
        task.setFinishAt(now);
        task.setDurationMs(durationMs);
        // 写审计日志 action=TIMEOUT
        audit(task, "TIMEOUT", null, null, reason);
        log.info("[Flow] 任务超时: taskId={} reason={}", taskId, reason);
        // P2-3: Prometheus 指标
        if (flowMetrics != null) {
            flowMetrics.incTaskAutoHandled(task.getFlowCode(), task.getNodeCode(), "TIMEOUT");
        }
        // P2-36: 触发 onTaskTimeout 事件
        fireEvent(l -> l.onTaskTimeout(task.getId(), task.getInstanceId()), task.getId());
        // P2-35: 发布 Spring 异步事件
        publishWorkflowEvent("TASK_TIMEOUT", task.getInstanceId(), task.getId());
    }

    // ============================== 会签推进逻辑 ==============================

    /** OR 或签：直接完成 + 推进 */
    private void doPassAndAdvance(FlowTaskDO task, FlowInstanceDO instance,
                                   Map<String, Object> vars, FlowTaskOperateDTO dto) {
        completeAndArchive(task, dto.getComment());
        List<FlowNodeDO> nextNodes = advancer.advance(instance, task.getNodeCode(),
                "PASS", null, vars);
        instanceService.generateTasksForNodes(
                task.getInstanceId(), nextNodes, vars);
        updateInstanceNode(instance, nextNodes);
        fireTaskCompleted(task.getId(), "PASS", vars);
        audit(task, "PASS", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
        log.info("[Flow] 通过任务: taskId={} action=PASS next={}", task.getId(), nextNodes.size());
    }

    /** P0-1: 并行会签 — 全部通过才推进（GAP-P1: 乐观锁防并发） */
    private void doParallelPass(FlowTaskDO task, FlowInstanceDO instance,
                                 Map<String, Object> vars, FlowTaskOperateDTO dto) {
        int finished = (task.getApproveFinished() == null ? 0 : task.getApproveFinished()) + 1;
        int required = task.getApproveCount() == null ? 1 : task.getApproveCount();
        // GAP-P1: 乐观锁 — updateById 携带 @Version 自动检查版本号
        task.setApproveFinished(finished);
        int updated = taskMapper.updateById(task);
        if (updated == 0) {
            throw new BizException(BizErrorCode.RESOURCE_CONFLICT,
                    "任务已被其他操作修改，请刷新后重试: taskId=" + task.getId());
        }
        if (finished < required) {
            // 未全部通过：任务保持 PENDING，不推进
            audit(task, "PARALLEL_PASS", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
            log.info("[Flow] 并行会签部分通过: taskId={} finished={}/{}", task.getId(), finished, required);
            return;
        }
        // 全部通过：完成 + 推进 + 跳过剩余
        taskMapper.skipByNode(instance.getId(), task.getNodeCode(),
                FlowTaskStatus.SKIPPED.name());
        completeAndArchive(task, dto.getComment());
        List<FlowNodeDO> nextNodes = advancer.advance(instance, task.getNodeCode(),
                "PASS", null, vars);
        instanceService.generateTasksForNodes(
                task.getInstanceId(), nextNodes, vars);
        updateInstanceNode(instance, nextNodes);
        fireTaskCompleted(task.getId(), "PASS", vars);
        audit(task, "PARALLEL_PASS_ALL", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
        log.info("[Flow] 并行会签全部通过: taskId={} finished={}/{}", task.getId(), finished, required);
    }

    /** P1-12: 顺序会签 — 按序逐一处理（GAP-P1: 乐观锁防并发） */
    private void doSequentialPass(FlowTaskDO task, FlowInstanceDO instance,
                                   Map<String, Object> vars, FlowTaskOperateDTO dto) {
        int finished = (task.getApproveFinished() == null ? 0 : task.getApproveFinished()) + 1;
        int required = task.getApproveCount() == null ? 1 : task.getApproveCount();
        // GAP-P1: 乐观锁 — updateById 携带 @Version 自动检查版本号
        task.setApproveFinished(finished);
        int updated = taskMapper.updateById(task);
        if (updated == 0) {
            throw new BizException(BizErrorCode.RESOURCE_CONFLICT,
                    "任务已被其他操作修改，请刷新后重试: taskId=" + task.getId());
        }
        if (finished < required) {
            // 还有下一个用户：切换办理人
            List<FlowUserDO> unprocessed = userMapper.selectUnprocessedByInstanceAndNode(
                    instance.getId(), task.getNodeCode());
            if (!unprocessed.isEmpty()) {
                FlowUserDO next = unprocessed.get(0);
                taskMapper.updateAssignee(task.getId(), next.getUserId(), next.getUserName(),
                        FlowAssigneeType.USER.name());
                audit(task, "SEQUENTIAL_PASS", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
                log.info("[Flow] 顺序会签切换下一人: taskId={} → {}", task.getId(), next.getUserId());
                return;
            }
        }
        // 全部完成：完成 + 推进
        completeAndArchive(task, dto.getComment());
        List<FlowNodeDO> nextNodes = advancer.advance(instance, task.getNodeCode(),
                "PASS", null, vars);
        instanceService.generateTasksForNodes(
                task.getInstanceId(), nextNodes, vars);
        updateInstanceNode(instance, nextNodes);
        fireTaskCompleted(task.getId(), "PASS", vars);
        audit(task, "SEQUENTIAL_PASS_ALL", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
        log.info("[Flow] 顺序会签全部通过: taskId={}", task.getId());
    }

    /** P1-12 + P1-5: 票签 — 可配置通过率（默认 50%+1，支持 0~1 之间任意阈值） */
    private void doVotePass(FlowTaskDO task, FlowInstanceDO instance,
                             Map<String, Object> vars, FlowTaskOperateDTO dto) {
        int finished = (task.getApproveFinished() == null ? 0 : task.getApproveFinished()) + 1;
        int required = task.getApproveCount() == null ? 1 : task.getApproveCount();
        // GAP-P1: 乐观锁 — updateById 携带 @Version 自动检查版本号
        task.setApproveFinished(finished);
        int updated = taskMapper.updateById(task);
        if (updated == 0) {
            throw new BizException(BizErrorCode.RESOURCE_CONFLICT,
                    "任务已被其他操作修改，请刷新后重试: taskId=" + task.getId());
        }
        // P1-5: 通过率可配置 — 默认 50% + 1（即过半数）
        int threshold = (required / 2) + 1;
        if (task.getVotePassRate() != null) {
            // votePassRate 是 0~1 之间的小数
            double rate = task.getVotePassRate().doubleValue();
            if (rate > 0 && rate <= 1.0) {
                // 通过率向上取整（例如 5 人 * 0.6 = 3）
                threshold = (int) Math.ceil(required * rate);
                if (threshold < 1) {
                    threshold = 1;
                }
            }
        }
        if (finished < threshold) {
            audit(task, "VOTE_PASS", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
            log.info("[Flow] 票签部分通过: taskId={} finished={}/{} (rate={})",
                    task.getId(), finished, threshold, task.getVotePassRate());
            return;
        }
        // 达到阈值：完成 + 推进 + 跳过剩余
        taskMapper.skipByNode(instance.getId(), task.getNodeCode(),
                FlowTaskStatus.SKIPPED.name());
        completeAndArchive(task, dto.getComment());
        List<FlowNodeDO> nextNodes = advancer.advance(instance, task.getNodeCode(),
                "PASS", null, vars);
        instanceService.generateTasksForNodes(
                task.getInstanceId(), nextNodes, vars);
        updateInstanceNode(instance, nextNodes);
        fireTaskCompleted(task.getId(), "PASS", vars);
        audit(task, "VOTE_PASS_THRESHOLD", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
        log.info("[Flow] 票签达到阈值: taskId={} finished={}/{} (rate={})",
                task.getId(), finished, threshold, task.getVotePassRate());
    }

    /**
     * P1-5: 加权票签 — 按办理人 weight 累加，权重达到阈值才推进。
     *
     * <p>使用场景：财务总监 3 票，普通员工 1 票；3 人共 5 票权重，
     * 设置 votePassRate=0.6 即 ≥3 票即通过。
     *
     * <p>实现：
     * <ol>
     *   <li>从 pmis_flow_user 读取所有该任务的办理人及其 weight</li>
     *   <li>标记当前用户 processed=1，记录 comment</li>
     *   <li>统计已通过的总 weight（processed=1）</li>
     *   <li>与总 weight 对比，达标即推进</li>
     * </ol>
     */
    private void doWeightedVotePass(FlowTaskDO task, FlowInstanceDO instance,
                                     Map<String, Object> vars, FlowTaskOperateDTO dto) {
        // 1. 查询所有办理人（含 weight）
        List<FlowUserDO> users = userMapper.selectByTaskId(task.getId());
        if (users == null || users.isEmpty()) {
            // 无扩展数据：回退到简单票签
            doVotePass(task, instance, vars, dto);
            return;
        }
        // 2. 计算总权重
        int totalWeight = users.stream()
                .mapToInt(u -> u.getWeight() == null ? 1 : Math.max(1, u.getWeight()))
                .sum();
        // 3. 标记当前用户已处理
        if (dto.getUserId() != null) {
            userMapper.markProcessed(task.getId(), String.valueOf(dto.getUserId()),
                    dto.getComment(), LocalDateTime.now());
        }
        // 4. 累加已通过的权重（processed=1）
        int passedWeight = users.stream()
                .filter(u -> Integer.valueOf(1).equals(u.getProcessed()))
                .mapToInt(u -> u.getWeight() == null ? 1 : Math.max(1, u.getWeight()))
                .sum();
        // 乐观锁更新 approveFinished（这里存已通过人数而不是权重，便于前端展示）
        int finished = (task.getApproveFinished() == null ? 0 : task.getApproveFinished()) + 1;
        task.setApproveFinished(finished);
        int updated = taskMapper.updateById(task);
        if (updated == 0) {
            throw new BizException(BizErrorCode.RESOURCE_CONFLICT,
                    "任务已被其他操作修改，请刷新后重试: taskId=" + task.getId());
        }
        // 5. 阈值（默认 50%）
        int threshold = (totalWeight / 2) + 1;
        if (task.getVotePassRate() != null) {
            double rate = task.getVotePassRate().doubleValue();
            if (rate > 0 && rate <= 1.0) {
                threshold = (int) Math.ceil(totalWeight * rate);
                if (threshold < 1) {
                    threshold = 1;
                }
            }
        }
        if (passedWeight < threshold) {
            audit(task, "WEIGHTED_VOTE_PASS", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
            log.info("[Flow] 加权票签部分通过: taskId={} passedWeight={}/{} totalWeight={} (rate={})",
                    task.getId(), passedWeight, threshold, totalWeight, task.getVotePassRate());
            return;
        }
        // 6. 达到阈值：完成 + 推进 + 跳过剩余
        taskMapper.skipByNode(instance.getId(), task.getNodeCode(),
                FlowTaskStatus.SKIPPED.name());
        completeAndArchive(task, dto.getComment());
        List<FlowNodeDO> nextNodes = advancer.advance(instance, task.getNodeCode(),
                "PASS", null, vars);
        instanceService.generateTasksForNodes(
                task.getInstanceId(), nextNodes, vars);
        updateInstanceNode(instance, nextNodes);
        fireTaskCompleted(task.getId(), "PASS", vars);
        audit(task, "WEIGHTED_VOTE_THRESHOLD", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
        log.info("[Flow] 加权票签达到阈值: taskId={} passedWeight={}/{} totalWeight={} (rate={})",
                task.getId(), passedWeight, threshold, totalWeight, task.getVotePassRate());
    }

    // ============================== 私有辅助 ==============================

    /** 将历史任务 DO 转换为待办任务 DO（用于已办查询结果统一） */
    private FlowTaskDO hisToTask(FlowHisTaskDO his) {
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
        return t;
    }

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

    // ============================== GAP-P0: 审批人为空兜底辅助方法 ==============================

    /** AUTO_PASS 递归深度保护（防止流程定义环路导致栈溢出） */
    private static final ThreadLocal<Integer> AUTO_PASS_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final int MAX_AUTO_PASS_DEPTH = 20;

    /** AUTO_PASS 后推进到下一节点 */
    private void advanceAfterAutoPass(FlowInstanceDO instance, FlowNodeDO node,
                                       Map<String, Object> variables) {
        int depth = AUTO_PASS_DEPTH.get();
        if (depth >= MAX_AUTO_PASS_DEPTH) {
            log.warn("[Flow] AUTO_PASS 递归深度超限: depth={} instanceId={}", depth, instance.getId());
            throw new BizException(BizErrorCode.INTERNAL_ERROR,
                    "AUTO_PASS 递归深度超限，可能存在流程定义环路");
        }
        AUTO_PASS_DEPTH.set(depth + 1);
        try {
            List<FlowNodeDO> nextNodes = advancer.advance(instance, node.getNodeCode(),
                    "PASS", null, variables);
            if (nextNodes.isEmpty()) {
                instanceService.complete(instance.getId(), node.getNodeCode());
            } else {
                instanceService.generateTasksForNodes(instance.getId(), nextNodes, variables);
                updateInstanceNode(instance, nextNodes);
            }
        } finally {
            AUTO_PASS_DEPTH.set(depth);
        }
    }

    /**
     * GAP-V2-05: 审批人自动去重检查
     *
     * <p>当 performType == OR（单人审批）时，查询当前实例上一已完成任务的 assigneeId
     * 是否与当前任务相同。若相同且上一任务非 AUTO_PASS（assigneeName=SYSTEM_AUTO_PASS），
     * 则将当前任务标记为 COMPLETED（自动跳过），写 AUTO_DEDUP 审计日志，并推进到下一节点，
     * 避免同一人连续审批不同节点。
     *
     * <p>语义同 AUTO_PASS：复用 advanceAfterAutoPass 推进（含递归深度保护，可处理连续多节点去重）。
     * 整体 try-catch 吞异常：去重检查出错时返回 null，由主流程继续正常创建任务（尽力而为）。
     *
     * @param task              已 setAssigneeId 但尚未 insert 的任务
     * @param instance          流程实例
     * @param node              当前节点
     * @param variables         流程变量
     * @param currentAssigneeId 当前任务首办理人 ID（userIds.get(0)）
     * @return 去重命中时返回已自动完成任务 ID；未命中/异常返回 null
     */
    private Long tryAutoDedup(FlowTaskDO task, FlowInstanceDO instance, FlowNodeDO node,
                              Map<String, Object> variables, String currentAssigneeId) {
        try {
            // 查询同实例下最近一条已完成任务（按主键倒序取最新一条）
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FlowTaskDO> qw =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            qw.eq(FlowTaskDO::getInstanceId, instance.getId())
                    .eq(FlowTaskDO::getTaskStatus, FlowTaskStatus.COMPLETED.name())
                    .orderByDesc(FlowTaskDO::getId)
                    .last("LIMIT 1");
            List<FlowTaskDO> prevTasks = taskMapper.selectList(qw);
            if (prevTasks.isEmpty()) {
                return null;
            }
            FlowTaskDO prevTask = prevTasks.get(0);
            String prevAssigneeId = prevTask.getAssigneeId();
            // 上一任务为 AUTO_PASS（assigneeName=SYSTEM_AUTO_PASS）不参与去重；
            // assigneeId 不同也不去重
            if (prevAssigneeId == null
                    || !prevAssigneeId.equals(currentAssigneeId)
                    || "SYSTEM_AUTO_PASS".equals(prevTask.getAssigneeName())) {
                return null;
            }
            log.info("[Flow] 审批人自动去重: instanceId={} node={} assigneeId={}",
                    instance.getId(), node.getNodeCode(), currentAssigneeId);
            // 命中：将当前任务标记为 COMPLETED（自动跳过）
            task.setTaskStatus(FlowTaskStatus.COMPLETED.name());
            LocalDateTime now = LocalDateTime.now();
            task.setFinishAt(now);
            task.setDurationMs(0L);
            taskMapper.insert(task);
            archiveTask(task, FlowTaskStatus.COMPLETED);
            audit(task, "AUTO_DEDUP", 0L, null, "审批人与上一节点相同，自动去重跳过");
            // 推进到下一节点（复用 AUTO_PASS 推进逻辑，含递归深度保护）
            advanceAfterAutoPass(instance, node, variables);
            return task.getId();
        } catch (Exception e) {
            log.warn("[Flow] 审批人自动去重检查异常: instanceId={} node={} err={}",
                    instance.getId(), node.getNodeCode(), e.getMessage());
            return null;
        }
    }

    /** 解析 node.ext JSON 为 Map */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseExtConfig(String ext) {
        if (!StringUtils.hasText(ext)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> map = JSON.parseObject(ext, Map.class);
            return map == null ? Collections.emptyMap() : map;
        } catch (Exception e) {
            log.warn("[Flow] 解析 node.ext JSON 失败: {} err={}", ext, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /** 从 extConfig 中读取 Long 值 */
    private Long parseLongConfig(Map<String, Object> config, String key, Long defaultValue) {
        Object val = config.get(key);
        if (val == null) return defaultValue;
        if (val instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(val));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 展开办理人为用户列表 */
    private List<String> expandAssignees(FlowNodeDO node, Map<String, Object> variables) {
        String perm = node.getPermissionFlag();
        if (!StringUtils.hasText(perm)) {
            return Collections.emptyList();
        }
        // P2-15: 支持逗号分隔的多人展开（如 "user:1,user:2,role:hr"）
        String resolved = variableStrategy.resolveAssignee(perm, variables);
        if (resolved == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String token : resolved.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) continue;
            // P2-38: self_select: 前缀不在展开阶段处理（需从流程变量读取发起人指定的用户列表），保留原样走 fallback
            if (t.startsWith("self_select:")) {
                continue;
            }
            // user: 前缀直接作为用户 ID 加入结果
            if (t.startsWith("user:")) {
                String uid = t.substring(5).trim();
                if (!uid.isEmpty() && seen.add(uid)) {
                    result.add(uid);
                }
                continue;
            }
            // P2-39: multi_leader: 前缀通过 SPI 调用 expandMultiLeader 展开（从发起人开始逐级向上）
            if (t.startsWith("multi_leader:")) {
                String levelStr = t.substring("multi_leader:".length()).trim();
                int levels = 1;
                try {
                    levels = Integer.parseInt(levelStr);
                } catch (NumberFormatException ignored) {
                }
                Long startUserId = resolveInitiatorId(variables);
                if (startUserId != null) {
                    List<Long> expanded = assigneeResolver.expandMultiLeader(startUserId, levels, variables);
                    if (expanded != null) {
                        for (Long uid : expanded) {
                            String s = String.valueOf(uid);
                            if (seen.add(s)) {
                                result.add(s);
                            }
                        }
                    }
                }
                continue;
            }
            // role:/dept:/leader:/position: 前缀通过 SPI 展开
            List<Long> expanded = assigneeResolver.expandUsers(t, variables);
            if (expanded != null) {
                for (Long uid : expanded) {
                    String s = String.valueOf(uid);
                    if (seen.add(s)) {
                        result.add(s);
                    }
                }
            }
        }
        return result;
    }

    /**
     * P2-39: 从流程变量中解析发起人 ID（用于 multi_leader 展开的起始用户）
     *
     * @param variables 流程变量
     * @return 发起人 ID，找不到返回 null
     */
    private Long resolveInitiatorId(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return null;
        }
        Object val = variables.get("initiatorId");
        if (val == null) {
            val = variables.get("_initiatorId");
        }
        if (val == null) {
            return null;
        }
        if (val instanceof Long l) {
            return l;
        }
        if (val instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(val));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 解析会签类型 */
    private FlowPerformType resolvePerformType(FlowNodeDO node) {
        if (node.getExt() != null) {
            try {
                Map<?, ?> ext = JSON.parseObject(node.getExt(), Map.class);
                Object ptObj = ext.get("performType");
                if (ptObj instanceof String pt) {
                    return FlowPerformType.valueOf(pt);
                }
            } catch (Exception ignored) {
            }
        }
        return FlowPerformType.OR;
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
        // P2-15: 逗号分隔的多人 permissionFlag，取第一段作为主办理人
        if (resolved.contains(",")) {
            resolved = resolved.split(",")[0].trim();
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
        } else if (resolved.startsWith("self_select:")) {
            // P2-38: 发起人自选审批人，assigneeId 为变量名（如 self_select:approvers → approvers）
            task.setAssigneeType(FlowAssigneeType.SELF_SELECT.name());
            task.setAssigneeId(resolved.substring("self_select:".length()));
        } else if (resolved.startsWith("multi_leader:")) {
            // P2-39: 多级上级，assigneeId 为级数（如 multi_leader:3 → 3）
            task.setAssigneeType(FlowAssigneeType.MULTI_LEADER.name());
            task.setAssigneeId(resolved.substring("multi_leader:".length()));
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
        // P2-37: 同时调用携带上下文的重载版本
        FlowEventContext ctx = new FlowEventContext();
        ctx.setTaskId(taskId);
        ctx.setAction(action);
        ctx.setOperatedAt(LocalDateTime.now());
        fireEvent(l -> l.onTaskCompleted(taskId, ctx), taskId);
        // P2-35: 发布 Spring 异步事件
        publishWorkflowEvent("TASK_COMPLETED", null, taskId);
    }

    /**
     * P2-35: 发布 Spring 异步事件（ApplicationEventPublisher 可能为 null，需检查）
     *
     * @param eventType  事件类型
     * @param instanceId 实例 ID（可空）
     * @param taskId     任务 ID（可空）
     */
    private void publishWorkflowEvent(String eventType, Long instanceId, Long taskId) {
        if (eventPublisher == null) return;
        try {
            eventPublisher.publishEvent(new FlowWorkflowEvent(this, eventType, instanceId, taskId, null));
        } catch (Exception e) {
            log.warn("[Flow] 发布 Spring 事件失败: type={} err={}", eventType, e.getMessage());
        }
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
        audit(task, action, operatorId, targetId, comment, null);
    }

    /**
     * P2-42: 审计日志写入（带意见分类）
     *
     * @param task        任务
     * @param action      操作类型
     * @param operatorId  操作人 ID
     * @param targetId    目标人 ID
     * @param comment     审批意见
     * @param commentType 意见分类：AGREE/DISAGREE/SUGGEST/INQUIRE
     */
    private void audit(FlowTaskDO task, String action, Long operatorId,
                       Long targetId, String comment, String commentType) {
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
            log.setCommentType(commentType);
            log.setOperatedAt(LocalDateTime.now());
            log.setTenantId(task.getTenantId());
            log.setProviderTraceId(task.getProviderTraceId());
            auditLogMapper.insert(log);
        } catch (Exception e) {
            FlowTaskServiceImpl.log.warn("[Flow] 审计日志写入失败: {}", e.getMessage());
        }
    }

    // ============================== P1-5: 加权票签辅助 ==============================

    /**
     * P1-5: 应用投票配置 — 从 node.ext 读取 votePassRate 写入 task
     */
    private void applyVoteConfig(FlowTaskDO task, FlowNodeDO node) {
        if (node == null || !StringUtils.hasText(node.getExt())) {
            return;
        }
        try {
            Map<String, Object> ext = parseExtConfig(node.getExt());
            Object rate = ext.get("votePassRate");
            if (rate != null) {
                java.math.BigDecimal rateValue = toBigDecimal(rate);
                if (rateValue != null && rateValue.doubleValue() > 0
                        && rateValue.doubleValue() <= 1.0) {
                    task.setVotePassRate(rateValue);
                }
            }
            // 如果配置了 userWeights，自动切换 performType=WEIGHTED_VOTE
            Object userWeights = ext.get("userWeights");
            if (userWeights != null && FlowPerformType.VOTE.name().equals(task.getPerformType())) {
                task.setPerformType(FlowPerformType.WEIGHTED_VOTE.name());
            }
        } catch (Exception e) {
            log.warn("[Flow] 解析投票配置失败: node={} err={}", node.getNodeCode(), e.getMessage());
        }
    }

    /**
     * P1-5: 解析 node.ext.userWeights JSON 为 Map
     *
     * <p>配置示例：
     * <pre>
     * "userWeights": {
     *   "1001": 3,  // 财务总监 3 票
     *   "1002": 1,  // 普通员工 1 票
     *   "1003": 1
     * }
     * </pre>
     */
    private java.util.Map<String, Integer> parseUserWeights(String ext) {
        if (!StringUtils.hasText(ext)) {
            return null;
        }
        try {
            Map<String, Object> extMap = parseExtConfig(ext);
            Object uw = extMap.get("userWeights");
            if (uw == null) {
                return null;
            }
            if (uw instanceof Map<?, ?> m) {
                java.util.Map<String, Integer> result = new java.util.HashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    String key = e.getKey() == null ? null : String.valueOf(e.getKey());
                    Object val = e.getValue();
                    if (key != null && val != null) {
                        try {
                            int w = (val instanceof Number n) ? n.intValue()
                                    : Integer.parseInt(String.valueOf(val));
                            if (w < 1) w = 1;
                            result.put(key, w);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                return result;
            }
            return null;
        } catch (Exception e) {
            log.warn("[Flow] 解析 userWeights 失败: err={}", e.getMessage());
            return null;
        }
    }

    /**
     * 安全转换为 BigDecimal
     */
    private java.math.BigDecimal toBigDecimal(Object val) {
        if (val == null) return null;
        if (val instanceof java.math.BigDecimal bd) return bd;
        if (val instanceof Number n) return java.math.BigDecimal.valueOf(n.doubleValue());
        try {
            return new java.math.BigDecimal(String.valueOf(val));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ============================== P1-4: 委派代理日志 ==============================

    /**
     * P1-4: 写入委派代理日志（当被委派人 PASS/REJECT/CLAIM/TRANSFER 时记录）
     *
     * <p>仅在 task.assignorId 不为空（即经过长期授权委派改写过的任务）时记录。
     *
     * @param task   当前任务
     * @param action 操作动作
     * @param opType 操作类型：ACT=办理 / VIEW=查看
     */
    private void logDelegateOperation(FlowTaskDO task, String action, String opType) {
        if (task == null || delegateLogMapper == null) {
            return;
        }
        try {
            Long ownerId = task.getAssignorId();
            Long delegateId = parseAssignorId(task.getAssigneeId());
            if (ownerId == null || delegateId == null) {
                return; // 非代理场景
            }
            FlowDelegateLogDO log = new FlowDelegateLogDO();
            log.setTenantId(task.getTenantId());
            log.setAuthId(0L); // 暂不绑定具体 authId（多匹配时无法确定）
            log.setInstanceId(task.getInstanceId());
            log.setTaskId(task.getId());
            log.setNodeCode(task.getNodeCode());
            log.setOwnerUserId(ownerId);
            log.setDelegateUserId(delegateId);
            log.setOpType(opType == null ? "ACT" : opType);
            log.setAction(action);
            log.setComment(task.getComment());
            log.setProviderTraceId(task.getProviderTraceId());
            log.setCreatedAt(LocalDateTime.now());
            log.setUpdatedAt(LocalDateTime.now());
            delegateLogMapper.insert(log);
        } catch (Exception e) {
            FlowTaskServiceImpl.log.warn("[Flow] 委派代理日志写入失败: taskId={} err={}",
                    task.getId(), e.getMessage());
        }
    }
}
