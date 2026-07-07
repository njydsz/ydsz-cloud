package com.njydsz.pmis.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.util.JsonUtils;
import com.njydsz.pmis.workflow.dto.FlowAssigneeDTO;
import com.njydsz.pmis.workflow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.engine.FlowAssigneeResolver;
import com.njydsz.pmis.workflow.engine.FlowEventContext;
import com.njydsz.pmis.workflow.engine.FlowServiceNodeExecutor;
import com.njydsz.pmis.workflow.engine.FlowUrgeLimiter;
import com.njydsz.pmis.workflow.engine.FlowVariableStrategy;
import com.njydsz.pmis.workflow.entity.FlowDelegateAuthDO;
import com.njydsz.pmis.workflow.entity.FlowDelegateLogDO;
import com.njydsz.pmis.workflow.entity.FlowHisTaskDO;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.entity.FlowUserDO;
import com.njydsz.pmis.workflow.enums.FlowAssigneeType;
import com.njydsz.pmis.workflow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.enums.FlowNodeType;
import com.njydsz.pmis.workflow.enums.FlowPerformType;
import com.njydsz.pmis.workflow.enums.FlowSignType;
import com.njydsz.pmis.workflow.enums.FlowTaskStatus;
import com.njydsz.pmis.workflow.mapper.FlowDelegateLogMapper;
import com.njydsz.pmis.workflow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.mapper.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.mapper.FlowUserMapper;
import com.njydsz.pmis.workflow.metrics.FlowMetrics;
import com.njydsz.pmis.workflow.service.FlowDelegateAuthService;
import com.njydsz.pmis.workflow.service.FlowEventSubscriptionService;
import com.njydsz.pmis.workflow.service.FlowInstanceService;
import com.njydsz.pmis.workflow.service.FlowSlaService;
import com.njydsz.pmis.workflow.service.FlowTodoCountPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 待办任务 — 完成类 Service 实现
 *
 * <p>从原 {@code FlowTaskServiceImpl} 拆分，专注任务流转/办理职责：
 * <ul>
 *   <li>创建任务：{@link #createTask}（含审批人为空兜底、自动去重、长期授权委派改写）</li>
 *   <li>签收：{@link #claim}</li>
 *   <li>通过：{@link #pass}（含 OR/并行会签/顺序会签/票签/加权票签 5 种推进模式）</li>
 *   <li>驳回：{@link #reject}（支持退回任意历史节点）</li>
 *   <li>转办：{@link #transfer}</li>
 *   <li>委派：{@link #delegate}（被委派人处理后回到原办理人）</li>
 *   <li>自由跳转：{@link #jump}</li>
 *   <li>超时标记：{@link #timeoutTask}</li>
 *   <li>取消实例任务：{@link #cancelByInstance}</li>
 *   <li>催办：{@link #urge}</li>
 * </ul>
 *
 * <p>跨子 Service 共享的任务校验/审计/事件能力委托给 {@link FlowTaskSupport}。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskCompleteServiceImpl {

    private final FlowRunTaskMapper taskMapper;
    private final FlowInstanceMapper instanceMapper;
    /** 归档任务到历史表（completeAndArchive / AUTO_PASS / 自动去重 使用） */
    private final FlowHisTaskMapper hisTaskMapper;
    private final FlowInstanceService instanceService;
    private final FlowAdvancer advancer;
    private final FlowVariableStrategy variableStrategy;
    private final FlowUserMapper userMapper;
    private final FlowNodeMapper nodeMapper;
    private final FlowAssigneeResolver assigneeResolver;
    private final FlowDelegateAuthService delegateAuthService;
    /** P1-4: 委派代理日志 Mapper（用于记录代理操作审计） */
    private final FlowDelegateLogMapper delegateLogMapper;
    /** P1-6: SLA 服务（任务创建时应用 SLA 配置 + 超时自动策略） */
    @Lazy
    private final FlowSlaService slaService;
    /** P1-7: 待办数 WebSocket 推送服务（任务创建/通过/驳回时实时推送给办理人） */
    @Lazy
    private final FlowTodoCountPushService todoCountPushService;
    /** P2-3: Prometheus 指标收集（可能为 null：测试环境） */
    private final FlowMetrics flowMetrics;
    /** P0-2: 催办限流器（Redis Lua 冷却 30 分钟） */
    private final FlowUrgeLimiter urgeLimiter;
    /** 跨子 Service 共享的任务校验/审计/事件辅助 */
    private final FlowTaskSupport support;
    /** P1-4: 服务节点执行器（HTTP/SCRIPT/AUTO_PASS 自动执行） */
    private final FlowServiceNodeExecutor serviceNodeExecutor;
    /**
     * P0-1: 事件订阅服务 — 任务完成时取消关联的边界事件订阅
     *
     * <p>使用 @Lazy 避免循环依赖：FlowEventSubscriptionServiceImpl → FlowAdvancer → FlowTaskService → FlowTaskCompleteServiceImpl
     */
    @Lazy
    private final FlowEventSubscriptionService eventSubscriptionService;
    /** P1-6: 审批附件服务（任务通过/驳回时保存附件） */
    private final FlowAttachmentService attachmentService;

    // ============================== 创建任务 ==============================

    /**
     * 创建任务（向后兼容重载）
     *
     * @param instanceId 流程实例 ID
     * @param node       节点
     * @param variables  流程变量
     * @return 任务 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public String createTask(String instanceId, FlowNodeDO node, Map<String, Object> variables) {
        return createTask(instanceId, node, variables, null);
    }

    /**
     * 创建任务（支持显式指定办理人）
     *
     * <p>GAP-P2-9 自由流扩展：{@code explicitAssignees} 非空时直接作为目标节点办理人，
     * 跳过 {@code node.permissionFlag} / {@code ext.collection} 解析逻辑。
     * 为空时回退到原有解析逻辑（向后兼容）。
     *
     * @param instanceId         流程实例 ID
     * @param node               节点
     * @param variables          流程变量
     * @param explicitAssignees  显式办理人列表（可选，自由流 JUMP 时传入）
     * @return 任务 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public String createTask(String instanceId, FlowNodeDO node, Map<String, Object> variables,
                           List<String> explicitAssignees) {
        FlowInstanceDO instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.workflow.msg_fc4b1c16", instanceId);
        }

        // P1-4: SERVICE 服务节点 — 自动执行（HTTP/SCRIPT/AUTO_PASS），不创建人工任务
        if (node.getNodeType() != null
                && node.getNodeType() == FlowNodeType.SERVICE.getCode()) {
            return executeServiceNode(instance, node, variables);
        }

        // GAP-P2-10: FOREACH 循环节点 — 对集合中每个元素创建独立 task，全部完成才推进
        if (node.getNodeType() != null
                && node.getNodeType() == FlowNodeType.FOREACH.getCode()) {
            return createForeachTasks(instance, node, variables, explicitAssignees);
        }

        // P0-4: LEVEL_APPROVAL 逐级审批节点 — 动态展开多级上级，切换为顺序会签
        if (node.getNodeType() != null
                && node.getNodeType() == FlowNodeType.LEVEL_APPROVAL.getCode()) {
            List<String> levelApprovers = expandLevelApprovers(instance, node, variables, explicitAssignees);
            if (levelApprovers.isEmpty()) {
                // 逐级审批人为空：复用 emptyStrategy 兜底（默认 AUTO_PASS）
                log.info("[Flow] 逐级审批展开为空，走兜底: instanceId={} node={}",
                        instanceId, node.getNodeCode());
                return createTaskWithEmptyAssignee(instance, node, variables);
            }
            // 逐级审批展开成功：委托给顺序会签创建逻辑
            return createLevelApprovalTask(instance, node, variables, levelApprovers);
        }

        // 解析办理人：GAP-P2-9 显式指定优先；否则尝试展开 ROLE/DEPT 为多人
        List<String> userIds = (explicitAssignees != null && !explicitAssignees.isEmpty())
                ? new ArrayList<>(explicitAssignees)
                : expandAssignees(node, variables);
        FlowPerformType performType = resolvePerformType(node);

        // P1-5: 跨节点办理人去重 — 排除同实例下已审批过的人员
        boolean autoDedup = isAutoDedupEnabled(node);
        if (autoDedup && !userIds.isEmpty()) {
            userIds = applyCrossNodeDedup(userIds, instanceId, node);
        }

        FlowRunTaskDO task = new FlowRunTaskDO();
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
        // P1-1: 从 node.ext.priority 读取优先级写入任务（默认 50）
        Map<String, Object> nodeExt = parseExtConfig(node.getExt());
        Object priorityVal = nodeExt.get("priority");
        if (priorityVal instanceof Number n) {
            task.setPriority(n.intValue());
        } else if (priorityVal instanceof String s && !s.isBlank()) {
            try {
                task.setPriority(Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignore) {
                // keep default
            }
        }
        // P1-6: 应用 SLA 配置 — 解析 node.slaConfig 设置 dueAt
        if (slaService != null) {
            slaService.applySlaConfig(task, node);
        }

        // 设置首个办理人
        if (userIds.isEmpty()) {
            // P1-5: 跨节点去重后候选人为空 — 自动跳过该节点（记录审计日志）
            if (autoDedup) {
                task.setAssigneeType(FlowAssigneeType.USER.name());
                task.setAssigneeId("0");
                task.setAssigneeName("SYSTEM_DEDUP_SKIP");
                task.setTaskStatus(FlowTaskStatus.COMPLETED.name());
                LocalDateTime now = LocalDateTime.now();
                task.setFinishAt(now);
                task.setDurationMs(0L);
                taskMapper.insert(task);
                archiveTask(task, FlowTaskStatus.COMPLETED);
                support.audit(task, "DEDUP_SKIP", null, null, "办理人去重后为空，自动跳过");
                log.info("[Flow] 办理人去重后为空，自动跳过: instanceId={} node={}",
                        instanceId, node.getNodeCode());
                // 推进到下一节点（复用 AUTO_PASS 推进逻辑，含递归深度保护）
                advanceAfterAutoPass(instance, node, variables);
                return task.getId();
            }
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
                    support.audit(task, "AUTO_PASS", null, null, "审批人为空，自动通过");
                    log.info("[Flow] 审批人为空自动通过: instanceId={} node={}",
                            instanceId, node.getNodeCode());
                    // 推进到下一节点（递归深度保护）
                    advanceAfterAutoPass(instance, node, variables);
                    return task.getId();
                }
                case "TRANSFER_ADMIN" -> {
                    String adminUserId = parseLongConfig(extConfig, "adminUserId", "1");
                    task.setAssigneeType(FlowAssigneeType.USER.name());
                    task.setAssigneeId(adminUserId);
                    task.setAssigneeName("ADMIN_FALLBACK");
                    taskMapper.insert(task);
                    log.info("[Flow] 审批人为空转管理员: instanceId={} node={} adminId={}",
                            instanceId, node.getNodeCode(), adminUserId);
                }
                case "ASSIGN_SPECIFIED" -> {
                    String specifiedUserId = parseLongConfig(extConfig, "specifiedUserId", "1");
                    task.setAssigneeType(FlowAssigneeType.USER.name());
                    task.setAssigneeId(specifiedUserId);
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
                String dedupTaskId = tryAutoDedup(task, instance, node, variables, userIds.get(0));
                if (dedupTaskId != null) {
                    return dedupTaskId;
                }
            }
            taskMapper.insert(task);
            // 写入 pmis_flow_user
            // P1-5: 从 ext 中读取 userWeights（userId -> weight）映射
            Map<String, Integer> userWeights = parseUserWeights(node.getExt());
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
        support.fireEvent(l -> l.onTaskCreated(task.getId()), task.getId());
        // P2-35: 发布 Spring 异步事件
        support.publishWorkflowEvent("TASK_CREATED", instanceId, task.getId());
        // P1-7: WebSocket 推送任务分配 + 待办数更新
        if (todoCountPushService != null) {
            todoCountPushService.pushTaskAssigned(task);
        }
        // P2-4 (GAP-14): 自动审批节点 — 满足 autoApprove 条件时无需人工，自动通过
        tryAutoApprove(instance, node, task, variables);
        return task.getId();
    }

    // ============================== P2-4 (GAP-14): 自动审批节点 ==============================

    /**
     * P2-4 (GAP-14): 自动审批节点
     *
     * <p>节点 ext 中配置 autoApprove 规则，满足任一条件时该审批任务自动通过，不阻塞人工：
     * <ul>
     *   <li>{@code whenInitiatorIsApprover=true} 且当前审批人包含发起人 → 自动通过（自己审自己）</li>
     *   <li>{@code expr} 为 Aviator 表达式，基于流程变量求值，结果为 true → 自动通过</li>
     * </ul>
     * 仅对单人 OR 模式生效（会签/并行模式不自动通过，需人工确认）。
     *
     * @param instance  流程实例
     * @param node      审批节点
     * @param task      已创建的任务
     * @param variables 流程变量
     */
    private void tryAutoApprove(FlowInstanceDO instance, FlowNodeDO node,
                                FlowRunTaskDO task, Map<String, Object> variables) {
        if (node.getExt() == null || node.getExt().isBlank()) {
            return;
        }
        Map<String, Object> extConfig;
        try {
            extConfig = JsonUtils.parseMap(node.getExt());
        } catch (Exception e) {
            return;
        }
        if (extConfig == null) {
            return;
        }
        Object autoApproveObj = extConfig.get("autoApprove");
        if (!(autoApproveObj instanceof Map<?, ?> autoApprove)) {
            return;
        }
        Map<String, Object> cfg = (Map<String, Object>) autoApprove;
        Boolean enabled = (Boolean) cfg.get("enabled");
        if (enabled == null || !enabled) {
            return;
        }
        // 仅单人 OR 模式自动通过
        if (!FlowPerformType.OR.name().equals(task.getPerformType())) {
            return;
        }
        boolean matched = false;
        // 条件1：发起人是审批人
        Object whenInitiator = cfg.get("whenInitiatorIsApprover");
        if (Boolean.TRUE.equals(whenInitiator) && instance.getInitiatorId() != null) {
            String initiator = String.valueOf(instance.getInitiatorId());
            if (initiator.equals(task.getAssigneeId())
                    || (task.getAssigneeName() != null
                    && task.getAssigneeName().contains(initiator))) {
                matched = true;
            }
        }
        // 条件2：Aviator 表达式
        if (!matched) {
            Object exprObj = cfg.get("expr");
            if (exprObj instanceof String expr && !expr.isBlank()) {
                try {
                    Map<String, Object> env = new HashMap<>(variables);
                    env.put("_initiatorId", instance.getInitiatorId());
                    env.put("_assigneeId", task.getAssigneeId());
                    Object result = serviceNodeExecutor.evalExpr(expr, env);
                    matched = Boolean.TRUE.equals(result);
                } catch (Exception e) {
                    log.warn("[Flow] 自动审批表达式求值失败 node={} expr={} err={}",
                            node.getNodeCode(), exprObj, e.getMessage());
                }
            }
        }
        if (matched) {
            FlowTaskOperateDTO autoDto = new FlowTaskOperateDTO();
            autoDto.setTaskId(task.getId());
            autoDto.setUserId("0");
            autoDto.setUserName("SYSTEM_AUTO_APPROVE");
            autoDto.setComment("自动审批节点满足条件，自动通过");
            autoDto.setVariables(variables);
            try {
                pass(autoDto);
                log.info("[Flow] 自动审批节点通过: instanceId={} node={} taskId={}",
                        instance.getId(), node.getNodeCode(), task.getId());
            } catch (Exception e) {
                log.warn("[Flow] 自动审批节点通过失败（降级为人工）: instanceId={} node={} err={}",
                        instance.getId(), node.getNodeCode(), e.getMessage());
            }
        }
    }

    // ============================== P0-4: 逐级审批节点 ==============================

    /**
     * P0-4: 展开逐级审批的上级列表
     *
     * <p>读取 ext 配置，通过 {@link FlowAssigneeResolver#expandMultiLeader} 展开多级上级。
     * 支持终止条件：maxLevel / stopAtPosition / stopAtUserId。
     *
     * @param instance  流程实例
     * @param node      逐级审批节点
     * @param variables 流程变量
     * @param explicitAssignees 显式指定（自由流跳转时传入，优先级最高）
     * @return 审批人 ID 列表（按级别升序）
     */
    private List<String> expandLevelApprovers(FlowInstanceDO instance, FlowNodeDO node,
                                                Map<String, Object> variables,
                                                List<String> explicitAssignees) {
        // 自由流跳转时显式指定优先
        if (explicitAssignees != null && !explicitAssignees.isEmpty()) {
            return new ArrayList<>(explicitAssignees);
        }
        Map<String, Object> extConfig = parseExtConfig(node.getExt());
        int maxLevel = parseIntConfig(extConfig, "maxLevel", 3);
        if (maxLevel < 1) {
            maxLevel = 1;
        }
        String startUserId = resolveInitiatorId(variables);
        if (startUserId == null && instance.getInitiatorId() != null) {
            startUserId = String.valueOf(instance.getInitiatorId());
        }
        if (startUserId == null) {
            log.warn("[Flow] 逐级审批无法解析发起人: instanceId={} node={}",
                    instance.getId(), node.getNodeCode());
            return Collections.emptyList();
        }
        try {
            List<Long> leaders = assigneeResolver.expandMultiLeader(startUserId, maxLevel, variables);
            if (leaders == null || leaders.isEmpty()) {
                log.warn("[Flow] 逐级审批展开为空: instanceId={} startUser={} maxLevel={}",
                        instance.getId(), startUserId, maxLevel);
                return Collections.emptyList();
            }
            List<String> result = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Long uid : leaders) {
                String s = String.valueOf(uid);
                // 终止条件：遇到指定用户停止
                String stopAtUserId = (String) extConfig.get("stopAtUserId");
                if (stopAtUserId != null && stopAtUserId.equals(s)) {
                    result.add(s);
                    log.info("[Flow] 逐级审批遇到 stopAtUserId 终止: instanceId={} stopAt={}",
                            instance.getId(), s);
                    break;
                }
                if (seen.add(s)) {
                    result.add(s);
                }
            }
            log.info("[Flow] 逐级审批展开: instanceId={} node={} startUser={} maxLevel={} result={}",
                    instance.getId(), node.getNodeCode(), startUserId, maxLevel, result);
            return result;
        } catch (Exception e) {
            log.error("[Flow] 逐级审批展开异常: instanceId={} err={}",
                    instance.getId(), e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * P0-4: 创建逐级审批任务（顺序会签模式）
     *
     * <p>将展开的多级上级列表作为顺序会签审批人，每人审完切换下一人，全部通过才推进。
     *
     * @param instance  流程实例
     * @param node      逐级审批节点
     * @param variables 流程变量
     * @param approvers 审批人列表（按级别升序）
     * @return 首条任务 ID
     */
    private String createLevelApprovalTask(FlowInstanceDO instance, FlowNodeDO node,
                                             Map<String, Object> variables,
                                             List<String> approvers) {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setInstanceId(instance.getId());
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
        // 顺序会签：每人审完切换下一人
        task.setPerformType(FlowPerformType.SEQUENTIAL.name());
        task.setApproveCount(approvers.size());
        task.setApproveFinished(0);
        task.setTaskStatus(FlowTaskStatus.PENDING.name());
        task.setAssigneeType(FlowAssigneeType.USER.name());
        task.setAssigneeId(approvers.get(0));
        task.setAssigneeName("USER:" + approvers.get(0));
        task.setTenantId(instance.getTenantId());
        task.setProviderTraceId(instance.getProviderTraceId());
        task.setPriority(50);
        // 应用 SLA 配置
        if (slaService != null) {
            slaService.applySlaConfig(task, node);
        }
        taskMapper.insert(task);
        // 写入 pmis_flow_user（顺序会签）
        for (int i = 0; i < approvers.size(); i++) {
            String uid = approvers.get(i);
            FlowUserDO fu = new FlowUserDO();
            fu.setTaskId(task.getId());
            fu.setInstanceId(instance.getId());
            fu.setNodeCode(node.getNodeCode());
            fu.setUserType(FlowAssigneeType.USER.name());
            fu.setUserId(uid);
            fu.setUserName("USER:" + uid);
            fu.setProcessed(0);
            fu.setWeight(1);
            fu.setSignType(FlowSignType.ORIGINAL.name());
            fu.setTenantId(instance.getTenantId());
            fu.setProviderTraceId(instance.getProviderTraceId());
            userMapper.insert(fu);
        }
        // Prometheus 指标
        if (flowMetrics != null) {
            flowMetrics.incTaskCreated(instance.getFlowCode(), node.getNodeCode());
        }
        // WebSocket 推送
        if (todoCountPushService != null) {
            todoCountPushService.pushTaskAssigned(task);
        }
        // 事件触发
        support.fireEvent(l -> l.onTaskCreated(task.getId()), task.getId());
        support.publishWorkflowEvent("TASK_CREATED", instance.getId(), task.getId());
        // 长期授权委派改写
        applyDelegateRedirect(task, instance, node);
        log.info("[Flow] 逐级审批任务创建: instanceId={} node={} approvers={}",
                instance.getId(), node.getNodeCode(), approvers);
        return task.getId();
    }

    /**
     * P0-4: 逐级审批人为空时走 emptyStrategy 兜底
     *
     * <p>复用审批人为空的兜底逻辑（AUTO_PASS / TRANSFER_ADMIN / ASSIGN_SPECIFIED / FALLBACK），
     * 避免重复代码。
     */
    private String createTaskWithEmptyAssignee(FlowInstanceDO instance, FlowNodeDO node,
                                                 Map<String, Object> variables) {
        // 复用原有 createTask 逻辑中的 emptyStrategy 处理
        // 这里通过传空的 explicitAssignees 触发原有兜底
        return createTask(instanceId -> {
            // 委托给原有逻辑：传 null explicitAssignees + 空 permissionFlag 触发兜底
            FlowRunTaskDO task = new FlowRunTaskDO();
            task.setInstanceId(instance.getId());
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

            Map<String, Object> extConfig = parseExtConfig(node.getExt());
            String emptyStrategy = (String) extConfig.getOrDefault("emptyStrategy", "AUTO_PASS");
            switch (emptyStrategy) {
                case "AUTO_PASS", "TRANSFER_ADMIN", "ASSIGN_SPECIFIED" -> {
                    String fallbackUserId = "AUTO_PASS".equals(emptyStrategy) ? "0"
                            : parseLongConfig(extConfig,
                                    "TRANSFER_ADMIN".equals(emptyStrategy) ? "adminUserId" : "specifiedUserId",
                                    "1");
                    task.setAssigneeType(FlowAssigneeType.USER.name());
                    task.setAssigneeId(fallbackUserId);
                    task.setAssigneeName("SYSTEM_" + emptyStrategy);
                    if ("AUTO_PASS".equals(emptyStrategy)) {
                        task.setTaskStatus(FlowTaskStatus.COMPLETED.name());
                        task.setFinishAt(LocalDateTime.now());
                        task.setDurationMs(0L);
                    }
                    taskMapper.insert(task);
                    if (FlowTaskStatus.COMPLETED.name().equals(task.getTaskStatus())) {
                        archiveTask(task, FlowTaskStatus.COMPLETED);
                        support.audit(task, "LEVEL_APPROVAL_" + emptyStrategy, null, null,
                                "逐级审批展开为空，" + emptyStrategy);
                        advanceAfterAutoPass(instance, node, variables);
                    }
                    log.info("[Flow] 逐级审批空兜底: instanceId={} node={} strategy={}",
                            instance.getId(), node.getNodeCode(), emptyStrategy);
                }
                default -> {
                    task.setAssigneeType(FlowAssigneeType.USER.name());
                    task.setAssigneeId("1");
                    task.setAssigneeName("FALLBACK");
                    taskMapper.insert(task);
                    log.warn("[Flow] 逐级审批空兜底 FALLBACK: instanceId={} node={}",
                            instance.getId(), node.getNodeCode());
                }
            }
            return task.getId();
        }, instance.getId());
    }

    /** 函数式辅助：忽略参数直接执行 Supplier */
    private <T> T createTask(java.util.function.Function<String, T> fn, String ignored) {
        return fn.apply(ignored);
    }

    /** P0-4: 安全解析 int 配置 */
    private int parseIntConfig(Map<String, Object> config, String key, int defaultValue) {
        Object val = config.get(key);
        if (val == null) return defaultValue;
        if (val instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(val));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // ============================== GAP-P2-10: FOREACH 循环节点 ==============================

    /**
     * GAP-P2-10: FOREACH 循环节点 — 对集合中每个元素创建独立 task
     *
     * <p>对标 BPMN 2.0 multiInstance + 钉钉/飞书动态审批人集合。与会签（1 task + N user）的区别：
     * FOREACH 为每个集合元素创建独立的 FlowRunTaskDO，每条 task 有独立的 assigneeId / iterVar。
     *
     * <h3>处理流程</h3>
     * <ol>
     *   <li>读取 ext.collection 配置，展开集合变量得到 N 个元素（复用 expandAssignees）</li>
     *   <li>集合为空时走 ext.emptyStrategy 兜底（默认 AUTO_PASS 直接推进）</li>
     *   <li>集合非空：为每个元素创建 1 条独立 task，performType=FOREACH_PARALLEL，
     *       approveCount=1（每条 task 独立计数），iterVar 存元素值</li>
     *   <li>返回首条 task ID（向后兼容 createTask 返回值语义）</li>
     * </ol>
     *
     * <p>完成聚合在 {@link #doForeachPass} 中处理：每条 task 完成后检查同 nodeCode 的 PENDING task 数，
     * 全部完成才调用 advancer.advance 推进。
     *
     * @param instance           流程实例
     * @param node               FOREACH 节点
     * @param variables          流程变量
     * @param explicitAssignees  显式办理人（P2-9 自由流跳转传入，FOREACH 场景一般不传）
     * @return 首条 task ID（集合为空且 AUTO_PASS 时返回自动完成的 task ID）
     */
    private String createForeachTasks(FlowInstanceDO instance, FlowNodeDO node,
                                      Map<String, Object> variables,
                                      List<String> explicitAssignees) {
        // 展开集合元素（复用 expandAssignees，支持 collection 变量 / permissionFlag / 显式指定）
        List<String> elements = (explicitAssignees != null && !explicitAssignees.isEmpty())
                ? new ArrayList<>(explicitAssignees)
                : expandAssignees(node, variables);

        // 集合为空兜底（复用 emptyStrategy 配置，默认 AUTO_PASS 直接推进）
        if (elements.isEmpty()) {
            Map<String, Object> extConfig = parseExtConfig(node.getExt());
            String emptyStrategy = (String) extConfig.getOrDefault("emptyStrategy", "AUTO_PASS");
            if ("AUTO_PASS".equals(emptyStrategy)) {
                FlowRunTaskDO autoTask = buildForeachTask(instance, node, "0",
                        "SYSTEM_AUTO_PASS", "0");
                autoTask.setTaskStatus(FlowTaskStatus.COMPLETED.name());
                autoTask.setFinishAt(LocalDateTime.now());
                autoTask.setDurationMs(0L);
                taskMapper.insert(autoTask);
                archiveTask(autoTask, FlowTaskStatus.COMPLETED);
                support.audit(autoTask, "FOREACH_AUTO_PASS", null, null, "FOREACH 集合为空，自动通过");
                log.info("[Flow] FOREACH 集合为空自动通过: instanceId={} node={}",
                        instance.getId(), node.getNodeCode());
                // 推进到下一节点（复用 AUTO_PASS 推进逻辑）
                advanceAfterAutoPass(instance, node, variables);
                return autoTask.getId();
            }
            // 其他策略（TRANSFER_ADMIN / ASSIGN_SPECIFIED）回退到单 task 创建
            log.warn("[Flow] FOREACH 集合为空，使用 {} 策略: node={}", emptyStrategy, node.getNodeCode());
            elements = List.of("1"); // 默认管理员 ID
        }

        // 为每个元素创建独立 task
        String firstTaskId = null;
        for (String element : elements) {
            FlowRunTaskDO task = buildForeachTask(instance, node, element,
                    "USER:" + element, element);
            taskMapper.insert(task);

            // 写入 pmis_flow_user（保持与会签一致的办理人记录）
            FlowUserDO fu = new FlowUserDO();
            fu.setTaskId(task.getId());
            fu.setInstanceId(instance.getId());
            fu.setNodeCode(node.getNodeCode());
            fu.setUserType(FlowAssigneeType.USER.name());
            fu.setUserId(element);
            fu.setUserName("USER:" + element);
            fu.setProcessed(0);
            fu.setWeight(1);
            fu.setSignType(FlowSignType.ORIGINAL.name());
            fu.setTenantId(instance.getTenantId());
            fu.setProviderTraceId(instance.getProviderTraceId());
            userMapper.insert(fu);

            // P2-3: Prometheus 指标
            if (flowMetrics != null) {
                flowMetrics.incTaskCreated(instance.getFlowCode(), node.getNodeCode());
            }
            // P1-7: WebSocket 推送
            if (todoCountPushService != null) {
                todoCountPushService.pushTaskAssigned(task);
            }
            support.fireEvent(l -> l.onTaskCreated(task.getId()), task.getId());
            support.publishWorkflowEvent("TASK_CREATED", instance.getId(), task.getId());

            if (firstTaskId == null) {
                firstTaskId = task.getId();
            }
        }
        log.info("[Flow] FOREACH 创建 {} 条独立 task: instanceId={} node={}",
                elements.size(), instance.getId(), node.getNodeCode());
        return firstTaskId;
    }

    /**
     * GAP-P2-10: 构建 FOREACH 子任务
     */
    private FlowRunTaskDO buildForeachTask(FlowInstanceDO instance, FlowNodeDO node,
                                          String assigneeId, String assigneeName, String iterVar) {
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setInstanceId(instance.getId());
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
        task.setPerformType(FlowPerformType.FOREACH_PARALLEL.name());
        task.setApproveCount(1);
        task.setApproveFinished(0);
        task.setTaskStatus(FlowTaskStatus.PENDING.name());
        task.setAssigneeType(FlowAssigneeType.USER.name());
        task.setAssigneeId(assigneeId);
        task.setAssigneeName(assigneeName);
        task.setTenantId(instance.getTenantId());
        task.setProviderTraceId(instance.getProviderTraceId());
        task.setIterVar(iterVar);
        // P1-1: 从 node.ext.priority 读取优先级（默认 50）
        Map<String, Object> nodeExt = parseExtConfig(node.getExt());
        Object priorityVal = nodeExt.get("priority");
        if (priorityVal instanceof Number n) {
            task.setPriority(n.intValue());
        } else if (priorityVal instanceof String s && !s.isBlank()) {
            try {
                task.setPriority(Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignore) {
                task.setPriority(50);
            }
        } else {
            task.setPriority(50);
        }
        // P1-6: 应用 SLA 配置
        if (slaService != null) {
            slaService.applySlaConfig(task, node);
        }
        return task;
    }

    // ============================== P1-4: 长期授权委派改写 ==============================

    /**
     * 应用长期授权委派：检查 task.assigneeId 是否为某代理规则的 ownerUserId，
     * 如果是则改写 assigneeId 为 delegateUserId，并将原 owner 写入 assignorId。
     *
     * <p>改写是静默的（写日志 + audit），不抛异常，避免拖垮主流程。
     */
    private void applyDelegateRedirect(FlowRunTaskDO task,
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
            String currentUserId = currentAssigneeId.trim();
            FlowDelegateAuthDO matched = delegateAuthService.matchAuth(
                    instance.getTenantId(), currentUserId,
                    instance.getFlowCode(), node.getNodeCode());
            if (matched == null) {
                return;
            }
            // 改写：assignorId 记原办理人，assigneeId 改为 delegate
            task.setAssignorId(currentUserId);
            task.setAssignorName(matched.getOwnerUserName());
            task.setAssigneeId(matched.getDelegateUserId());
            task.setAssigneeName(matched.getDelegateUserName());
            taskMapper.updateById(task);
            // 写审计日志
            support.audit(task, "DELEGATE_AUTH_APPLIED", matched.getDelegateUserId(),
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

    /**
     * 签收
     */
    @Transactional(rollbackFor = Exception.class)
    public void claim(String taskId, String userId) {
        FlowRunTaskDO task = support.getTaskOrThrow(taskId);
        if (!FlowTaskStatus.PENDING.name().equals(task.getTaskStatus())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_5873f2ae", task.getTaskStatus());
        }
        taskMapper.updateById(toClaimTask(task, userId));
        support.audit(task, "CLAIM", userId, null, null);
        // P1-4: 记录代理签收日志
        logDelegateOperation(task, "CLAIM", "ACT");
        log.info("[Flow] 签收任务: taskId={} userId={}", taskId, userId);
        // P2-3: Prometheus 指标
        if (flowMetrics != null) {
            flowMetrics.incTaskClaimed(task.getFlowCode(), task.getNodeCode());
        }
    }

    // ============================== 通过（P0-1: 会签修复） ==============================

    /**
     * 通过
     */
    @Transactional(rollbackFor = Exception.class)
    public void pass(FlowTaskOperateDTO dto) {
        FlowRunTaskDO task = support.getTaskOrThrow(dto.getTaskId());
        if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_7f4098fb", task.getTaskStatus());
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
            support.audit(task, "DELEGATE_RETURN", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
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

        // P1-6: 保存审批附件
        attachmentService.saveBatch(task.getInstanceId(), task.getId(), task.getNodeCode(),
                "TASK", dto.getUserId(), dto.getUserName(), dto.getAttachments(),
                task.getTenantId(), task.getProviderTraceId());

        switch (performType) {
            case OR -> doPassAndAdvance(task, instance, mergedVars, dto);
            case PARALLEL -> doParallelPass(task, instance, mergedVars, dto);
            case SEQUENTIAL -> doSequentialPass(task, instance, mergedVars, dto);
            case VOTE -> doVotePass(task, instance, mergedVars, dto);
            case WEIGHTED_VOTE -> doWeightedVotePass(task, instance, mergedVars, dto);
            // GAP-P2-10: FOREACH 并行循环 — 每条 task 独立完成，全部完成才推进
            case FOREACH_PARALLEL -> doForeachPass(task, instance, mergedVars, dto);
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

    // ============================== 驳回（P1-11: 任意历史节点；GAP-P0-2: 多节点同退） ==============================

    /**
     * 驳回
     *
     * <p>GAP-P0-2: 支持退回多节点同退（对标飞书）。
     * 当 {@code dto.targetNodeCodes} 非空且 size > 1 时，在所有指定节点同时创建待办任务；
     * 否则降级到单节点退回（{@code dto.targetNodeCode}）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(FlowTaskOperateDTO dto) {
        FlowRunTaskDO task = support.getTaskOrThrow(dto.getTaskId());
        if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_b35e6ea3");
        }
        LocalDateTime now = LocalDateTime.now();
        Long durationMs = task.getCreatedAt() == null
                ? null
                : Duration.between(task.getCreatedAt(), now).toMillis();
        taskMapper.completeTask(task.getId(), FlowTaskStatus.REJECTED.name(),
                dto.getComment(), now, durationMs);
        archiveTask(task, FlowTaskStatus.REJECTED);

        // P1-6: 保存驳回附件
        attachmentService.saveBatch(task.getInstanceId(), task.getId(), task.getNodeCode(),
                "TASK", dto.getUserId(), dto.getUserName(), dto.getAttachments(),
                task.getTenantId(), task.getProviderTraceId());

        FlowInstanceDO instance = instanceMapper.selectById(task.getInstanceId());
        Map<String, Object> mergedVars = mergeVariables(instance, dto.getVariables());

        // GAP-P0-2: 优先使用多节点同退；为空时降级到单节点（向后兼容）
        List<FlowNodeDO> rejectTargets;
        boolean multiReject = dto.getTargetNodeCodes() != null && dto.getTargetNodeCodes().size() > 1;
        if (multiReject) {
            rejectTargets = advancer.advanceMulti(instance, task.getNodeCode(),
                    "REJECT", dto.getTargetNodeCodes(), mergedVars);
        } else {
            // 单节点退回（保持原有逻辑）
            String singleTarget = dto.getTargetNodeCodes() != null && !dto.getTargetNodeCodes().isEmpty()
                    ? dto.getTargetNodeCodes().get(0)
                    : dto.getTargetNodeCode();
            rejectTargets = advancer.advance(instance, task.getNodeCode(),
                    "REJECT", singleTarget, mergedVars);
        }
        if (rejectTargets.isEmpty()) {
            instanceMapper.updateStatus(instance.getId(),
                    FlowInstanceStatus.REJECTED.name(),
                    null, null, now,
                    instance.getStartAt() == null ? null
                            : Duration.between(instance.getStartAt(), now).toMillis());
            taskMapper.cancelByInstance(instance.getId(), FlowTaskStatus.CANCELLED.name());
            fireInstanceRejected(instance.getId(), dto.getComment());
            support.audit(task, "REJECT", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
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
        support.audit(task, "REJECT", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
        log.info("[Flow] 退回任务: taskId={} targets={} multi={}", task.getId(),
                rejectTargets.stream().map(FlowNodeDO::getNodeCode).toList(), multiReject);
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

    /**
     * 转办
     */
    @Transactional(rollbackFor = Exception.class)
    public void transfer(FlowTaskOperateDTO dto) {
        if (dto.getTargetUserId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_6ddae4d1");
        }
        FlowRunTaskDO task = support.getTaskOrThrow(dto.getTaskId());
        String originalAssignorId = parseAssignorId(task.getAssigneeId());
        String originalAssignorName = task.getAssigneeName();
        task.setAssigneeId(String.valueOf(dto.getTargetUserId()));
        task.setAssigneeName(dto.getTargetUserName());
        task.setAssignorId(originalAssignorId);
        task.setAssignorName(originalAssignorName);
        task.setTaskStatus(FlowTaskStatus.CLAIMED.name());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        support.audit(task, "TRANSFER", dto.getUserId(), dto.getTargetUserId(), dto.getComment());
        log.info("[Flow] 转办任务: taskId={} → userId={}", task.getId(), dto.getTargetUserId());
        // P2-3: Prometheus 指标
        if (flowMetrics != null) {
            flowMetrics.incTaskTransferred(task.getFlowCode(), task.getNodeCode());
        }
        // P2-34: 触发 onTaskTransferred 事件
        support.fireEvent(l -> l.onTaskTransferred(task.getId(), originalAssignorId, dto.getTargetUserId()),
                task.getId());
        // P2-35: 发布 Spring 异步事件
        support.publishWorkflowEvent("TASK_TRANSFERRED", task.getInstanceId(), task.getId());
    }

    // ============================== 委派（P1-10: 修正语义） ==============================

    /**
     * 委派
     */
    @Transactional(rollbackFor = Exception.class)
    public void delegate(FlowTaskOperateDTO dto) {
        if (dto.getTargetUserId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_d4faa79e");
        }
        FlowRunTaskDO task = support.getTaskOrThrow(dto.getTaskId());
        // 保存原办理人
        String originalAssigneeId = parseAssignorId(task.getAssigneeId());
        String originalAssigneeName = task.getAssigneeName();
        task.setAssignorId(originalAssigneeId);
        task.setAssignorName(originalAssigneeName);
        task.setAssigneeId(String.valueOf(dto.getTargetUserId()));
        task.setAssigneeName(dto.getTargetUserName());
        task.setTaskStatus(FlowTaskStatus.DELEGATED.name());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        support.audit(task, "DELEGATE", dto.getUserId(), dto.getTargetUserId(), dto.getComment());
        log.info("[Flow] 委派任务: taskId={} → 被委派人={} (处理完回到 {})",
                task.getId(), dto.getTargetUserId(), originalAssigneeName);
        // P2-3: Prometheus 指标
        if (flowMetrics != null) {
            flowMetrics.incTaskDelegated(task.getFlowCode(), task.getNodeCode());
        }
        // P2-34: 触发 onTaskDelegated 事件
        support.fireEvent(l -> l.onTaskDelegated(task.getId(), originalAssigneeId, dto.getTargetUserId()),
                task.getId());
        // P2-35: 发布 Spring 异步事件
        support.publishWorkflowEvent("TASK_DELEGATED", task.getInstanceId(), task.getId());
    }

    // ============================== 催办（P1-9） ==============================

    /**
     * P1-9: 催办 — 通知当前节点所有待办处理人
     *
     * @return 被催办人 ID 列表
     */
    public List<String> urge(String instanceId, String operatorId, String comment) {
        // P0-2: 催办限流：同一催办人对同一实例 30 分钟内只允许一次
        if (operatorId != null && instanceId != null
                && !urgeLimiter.tryAcquire(operatorId, Long.parseLong(instanceId), "INSTANCE")) {
            throw new BizException(BizErrorCode.RATE_LIMIT,
                    "error.workflow.msg_75474a57");
        }
        List<FlowRunTaskDO> pendingTasks = taskMapper.selectPendingByInstance(instanceId);
        List<String> urged = new ArrayList<>();
        for (FlowRunTaskDO task : pendingTasks) {
            urged.add(task.getAssigneeId());
            support.audit(task, "URGE", operatorId, null, comment);
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
        support.fireEvent(l -> l.onTaskUrged(instanceId, null), null);
        // P2-35: 发布 Spring 异步事件
        support.publishWorkflowEvent("TASK_URGED", instanceId, null);
        return urged;
    }

    // ============================== P2-3 (GAP-13): 节点级催办 ==============================

    /**
     * P2-3 (GAP-13): 节点级催办 — 仅催办指定节点（nodeCode）的待办任务
     *
     * <p>nodeCode 为 null/空时退化为实例级催办（{@link #urge}）。
     * 限流维度：催办人 + 任务 ID（节点级），避免单节点被频繁催办刷屏。
     */
    @Override
    public List<String> urgeByNode(String instanceId, String nodeCode, String operatorId, String comment) {
        if (nodeCode == null || nodeCode.isBlank()) {
            // 退化为实例级催办
            return urge(instanceId, operatorId, comment);
        }
        // P0-2: 节点级限流：同一催办人对该节点 30 分钟内只允许一次
        if (operatorId != null && instanceId != null) {
            String nodeTarget = instanceId + ":" + nodeCode;
            if (!urgeLimiter.tryAcquire(operatorId, nodeTarget.hashCode() & Long.MAX_VALUE, "NODE")) {
                throw new BizException(BizErrorCode.RATE_LIMIT, "error.workflow.msg_75474a57");
            }
        }
        List<FlowRunTaskDO> pendingTasks = taskMapper.selectPendingByNode(instanceId, nodeCode);
        List<String> urged = new ArrayList<>();
        for (FlowRunTaskDO task : pendingTasks) {
            urged.add(task.getAssigneeId());
            support.audit(task, "URGE", operatorId, null, comment);
            // P2-3: 节点级催办事件（taskId 透传，便于前端按任务高亮）
            support.fireEvent(l -> l.onTaskUrged(instanceId, task.getId()), task.getId());
            support.publishWorkflowEvent("TASK_URGED", instanceId, task.getId());
        }
        log.info("[Flow] 节点级催办: instanceId={} nodeCode={} 被催办人={}", instanceId, nodeCode, urged);
        if (flowMetrics != null) {
            try {
                FlowInstanceDO ins = instanceMapper.selectById(instanceId);
                flowMetrics.incTaskUrged(ins != null ? ins.getFlowCode() : "unknown");
            } catch (Exception e) {
                flowMetrics.incTaskUrged("unknown");
            }
        }
        return urged;
    }

    // ============================== 自由跳转（P2-25 / GAP-P2-9 自由流） ==============================

    /**
     * P2-25 / GAP-P2-9: 自由跳转 — 运行时动态指定下一节点（自由流核心能力）
     *
     * <p>对标钉钉/飞书"自由流"：当前办理人可在审批中跳转到流程定义内任意已开启 freeJump 白名单的节点，
     * 并可显式指定目标节点的办理人。
     *
     * <h3>GAP-P2-9 增强点</h3>
     * <ul>
     *   <li>节点级白名单校验：目标节点 {@code ext.freeJump=true} 才允许跳转，
     *       未开启的节点抛 BAD_REQUEST（避免任意跳转破坏流程语义）</li>
     *   <li>显式办理人：{@code dto.targetAssignees} 非空时覆盖目标节点默认办理人</li>
     * </ul>
     *
     * <p>原有 P2-25 行为（管理员强制跳转）通过 {@link com.njydsz.pmis.common.permission.PermissionCodes#WORKFLOW_INSTANCE_CONTROL}
     * 权限放行，不强制要求目标节点 freeJump=true（向后兼容历史调用）。
     * 自由流场景由 {@link com.njydsz.pmis.common.permission.PermissionCodes#WORKFLOW_TASK_FREE_JUMP} 权限控制，
     * 走节点级白名单校验。
     *
     * @param dto 任务操作参数（需含 taskId + targetNodeCode，可选 targetAssignees）
     */
    @Transactional(rollbackFor = Exception.class)
    public void jump(FlowTaskOperateDTO dto) {
        FlowRunTaskDO task = support.getTaskOrThrow(dto.getTaskId());
        if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_1efc5644", task.getTaskStatus());
        }
        if (!StringUtils.hasText(dto.getTargetNodeCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_09c299d0");
        }
        FlowInstanceDO instance = instanceMapper.selectById(task.getInstanceId());
        if (instance == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.workflow.msg_fc4b1c16", task.getInstanceId());
        }
        // 校验目标节点存在
        FlowNodeDO targetNode = nodeMapper.selectByCode(task.getDefinitionId(), dto.getTargetNodeCode());
        if (targetNode == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.workflow.msg_a35217ba", dto.getTargetNodeCode());
        }
        // GAP-P2-9: 节点级 freeJump 白名单校验（仅自由流操作 action=JUMP 时启用）
        // 历史管理员强制跳转（无 action 字段或 action != JUMP）保持原有放行语义，向后兼容
        if ("JUMP".equals(dto.getAction()) && !isFreeJumpEnabled(targetNode)) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    String.format("目标节点未开启自由跳转白名单: nodeCode=%s", dto.getTargetNodeCode()));
        }
        // 完成当前任务（状态 COMPLETED，审计 action=JUMP）
        completeAndArchive(task, dto.getComment());
        // 取消同实例其他 PENDING 任务
        taskMapper.cancelByInstance(instance.getId(), FlowTaskStatus.CANCELLED.name());
        // 更新实例当前节点为目标节点
        instanceMapper.updateStatus(instance.getId(), instance.getFlowStatus(),
                targetNode.getNodeCode(), targetNode.getNodeName(), null, null);
        // 在目标节点创建新任务（GAP-P2-9: 显式办理人优先）
        Map<String, Object> vars = mergeVariables(instance, dto.getVariables());
        createTask(instance.getId(), targetNode, vars, dto.getTargetAssignees());
        // 触发任务完成事件
        fireTaskCompleted(task.getId(), "JUMP", vars);
        support.audit(task, "JUMP", dto.getUserId(), null, dto.getComment());
        log.info("[Flow] 自由跳转: taskId={} → targetNode={} explicitAssignees={}",
                task.getId(), dto.getTargetNodeCode(),
                dto.getTargetAssignees() != null ? dto.getTargetAssignees().size() : 0);
        // P2-34: 触发 onTaskJumped 事件
        support.fireEvent(l -> l.onTaskJumped(task.getId(), task.getNodeCode(), dto.getTargetNodeCode()),
                task.getId());
        // P2-35: 发布 Spring 异步事件
        support.publishWorkflowEvent("TASK_JUMPED", instance.getId(), task.getId());
    }

    /**
     * GAP-P2-9: 判断目标节点是否开启自由跳转白名单
     *
     * <p>读取 {@code node.ext.freeJump} 配置：
     * <ul>
     *   <li>{@code true} / {@code "true"}：允许自由跳转到该节点</li>
     *   <li>未配置 / 其他值：禁止（默认关闭，避免任意跳转破坏流程语义）</li>
     * </ul>
     *
     * @param node 目标节点
     * @return 是否允许自由跳转
     */
    private boolean isFreeJumpEnabled(FlowNodeDO node) {
        Map<String, Object> ext = parseExtConfig(node.getExt());
        Object val = ext.get("freeJump");
        if (val == null) {
            return false;
        }
        if (val instanceof Boolean b) {
            return b;
        }
        return "true".equalsIgnoreCase(String.valueOf(val).trim());
    }

    // ============================== 取消 ==============================

    /**
     * 取消某实例的全部 PENDING 任务（终止/驳回终态时使用）
     */
    public void cancelByInstance(String instanceId, String taskStatus) {
        taskMapper.cancelByInstance(instanceId, taskStatus);
    }

    // ============================== P2-36: 超时标记 ==============================

    /**
     * P2-36: 标记任务超时
     */
    @Transactional(rollbackFor = Exception.class)
    public void timeoutTask(String taskId, String reason) {
        FlowRunTaskDO task = support.getTaskOrThrow(taskId);
        // 校验状态为 PENDING/CLAIMED
        String status = task.getTaskStatus();
        if (!FlowTaskStatus.PENDING.name().equals(status)
                && !FlowTaskStatus.CLAIMED.name().equals(status)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.workflow.msg_ecc09732", status);
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
        support.audit(task, "TIMEOUT", null, null, reason);
        log.info("[Flow] 任务超时: taskId={} reason={}", taskId, reason);
        // P2-3: Prometheus 指标
        if (flowMetrics != null) {
            flowMetrics.incTaskAutoHandled(task.getFlowCode(), task.getNodeCode(), "TIMEOUT");
        }
        // P2-36: 触发 onTaskTimeout 事件
        support.fireEvent(l -> l.onTaskTimeout(task.getId(), task.getInstanceId()), task.getId());
        // P2-35: 发布 Spring 异步事件
        support.publishWorkflowEvent("TASK_TIMEOUT", task.getInstanceId(), task.getId());
    }

    // ============================== 会签推进逻辑 ==============================

    /** OR 或签：直接完成 + 推进 */
    private void doPassAndAdvance(FlowRunTaskDO task, FlowInstanceDO instance,
                                   Map<String, Object> vars, FlowTaskOperateDTO dto) {
        completeAndArchive(task, dto.getComment());
        List<FlowNodeDO> nextNodes = advancer.advance(instance, task.getNodeCode(),
                "PASS", null, vars);
        instanceService.generateTasksForNodes(
                task.getInstanceId(), nextNodes, vars);
        updateInstanceNode(instance, nextNodes);
        fireTaskCompleted(task.getId(), "PASS", vars);
        support.audit(task, "PASS", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
        log.info("[Flow] 通过任务: taskId={} action=PASS next={}", task.getId(), nextNodes.size());
    }

    /** P0-1: 并行会签 — 全部通过才推进（GAP-P1: 乐观锁防并发） */
    private void doParallelPass(FlowRunTaskDO task, FlowInstanceDO instance,
                                 Map<String, Object> vars, FlowTaskOperateDTO dto) {
        int finished = (task.getApproveFinished() == null ? 0 : task.getApproveFinished()) + 1;
        int required = task.getApproveCount() == null ? 1 : task.getApproveCount();
        // GAP-P1: 乐观锁 — updateById 携带 @Version 自动检查版本号
        task.setApproveFinished(finished);
        int updated = taskMapper.updateById(task);
        if (updated == 0) {
            throw new BizException(BizErrorCode.RESOURCE_CONFLICT,
                    "error.workflow.msg_199e8ba1", task.getId());
        }
        // P1-4: 优先求值 completionCondition 表达式（支持 BPMN 标准多实例完成条件）
        boolean shouldAdvance;
        String completionCondition = resolveCompletionCondition(task);
        if (completionCondition != null) {
            shouldAdvance = evaluateCompletionCondition(completionCondition, finished, required, vars);
        } else {
            // 回退到原有逻辑：全部通过才推进
            shouldAdvance = finished >= required;
        }
        if (!shouldAdvance) {
            // 未达到完成条件：任务保持 PENDING，不推进
            support.audit(task, "PARALLEL_PASS", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
            log.info("[Flow] 并行会签部分通过: taskId={} finished={}/{} condition={}",
                    task.getId(), finished, required, completionCondition != null);
            return;
        }
        // 完成条件满足：完成 + 推进 + 跳过剩余
        taskMapper.skipByNode(instance.getId(), task.getNodeCode(),
                FlowTaskStatus.SKIPPED.name());
        completeAndArchive(task, dto.getComment());
        List<FlowNodeDO> nextNodes = advancer.advance(instance, task.getNodeCode(),
                "PASS", null, vars);
        instanceService.generateTasksForNodes(
                task.getInstanceId(), nextNodes, vars);
        updateInstanceNode(instance, nextNodes);
        fireTaskCompleted(task.getId(), "PASS", vars);
        support.audit(task, "PARALLEL_PASS_ALL", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
        log.info("[Flow] 并行会签完成条件满足: taskId={} finished={}/{} condition={}",
                task.getId(), finished, required, completionCondition != null);
    }

    /**
     * P1-4: 解析节点的 completionCondition 表达式
     *
     * <p>优先从 {@code ext.completionCondition} 读取（推荐存储位置）；
     * 若为空，回退从 {@code node.skipAnyNode} 读取（向后兼容 BpmnXmlParser 旧解析逻辑），
     * 但仅当其看起来像表达式时（含 {@code nrOf} 或 {@code >=} / {@code <=} 等）才采用。
     *
     * @return 完成条件表达式，未配置返回 null
     */
    private String resolveCompletionCondition(FlowRunTaskDO task) {
        if (task.getDefinitionId() == null || task.getNodeCode() == null) {
            return null;
        }
        FlowNodeDO node = nodeMapper.selectByCode(task.getDefinitionId(), task.getNodeCode());
        if (node == null) {
            return null;
        }
        // 优先从 ext.completionCondition 读取
        Map<String, Object> ext = parseExtConfig(node.getExt());
        Object cc = ext.get("completionCondition");
        if (cc != null) {
            String s = String.valueOf(cc).trim();
            if (!s.isEmpty()) {
                return stripExpressionBraces(s);
            }
        }
        // 回退：从 skipAnyNode 读取（仅当看起来像表达式时）
        String skipAny = node.getSkipAnyNode();
        if (skipAny != null && !skipAny.isBlank()
                && (skipAny.contains("nrOf") || skipAny.contains(">=") || skipAny.contains("<="))) {
            return stripExpressionBraces(skipAny.trim());
        }
        return null;
    }

    /**
     * P1-4: 去除表达式外层的 ${...} 包裹
     */
    private String stripExpressionBraces(String expr) {
        if (expr.startsWith("${") && expr.endsWith("}")) {
            return expr.substring(2, expr.length() - 1).trim();
        }
        return expr;
    }

    /**
     * P1-4: 求值 completionCondition 表达式，注入 BPMN 标准多实例变量
     *
     * <p>注入变量：
     * <ul>
     *   <li>{@code nrOfInstances} = required（总会签人数）</li>
     *   <li>{@code nrOfCompletedInstances} = finished（已通过人数，含当前这次）</li>
     *   <li>{@code nrOfActiveInstances} = required - finished（剩余待处理人数）</li>
     * </ul>
     */
    private boolean evaluateCompletionCondition(String condition, int finished, int required,
                                                  Map<String, Object> vars) {
        try {
            Map<String, Object> evalVars = new HashMap<>(vars != null ? vars : Collections.emptyMap());
            evalVars.put("nrOfInstances", required);
            evalVars.put("nrOfCompletedInstances", finished);
            evalVars.put("nrOfActiveInstances", Math.max(0, required - finished));
            return variableStrategy.evaluate(condition, evalVars);
        } catch (Exception e) {
            log.warn("[Flow] completionCondition 求值失败: condition={} err={} — 回退到全部通过逻辑",
                    condition, e.getMessage());
            return finished >= required;
        }
    }

    /**
     * GAP-P2-10: FOREACH 并行循环 — 每条 task 独立完成，全部完成才推进
     *
     * <p>与 {@link #doParallelPass}（会签）的区别：
     * <ul>
     *   <li>会签：1 条 task + N 个 FlowUserDO，approveFinished 计数聚合在单 task 上</li>
     *   <li>FOREACH：N 条独立 task，每条 approveCount=1，通过 countPendingByNode 判断是否全部完成</li>
     * </ul>
     *
     * <h3>处理流程</h3>
     * <ol>
     *   <li>完成当前 task（COMPLETED + archive）</li>
     *   <li>查询同 instanceId + nodeCode 的 PENDING task 数</li>
     *   <li>PENDING 数 > 0：还有未完成 task，不推进</li>
     *   <li>PENDING 数 = 0：全部完成，调用 advancer.advance 推进到下一节点</li>
     * </ol>
     *
     * <p>支持 ext.completionCondition 表达式提前完成（如 {@code nrOfCompletedInstances >= 2}），
     * 满足条件时 skipByNode 跳过剩余 PENDING task 并推进。
     */
    private void doForeachPass(FlowRunTaskDO task, FlowInstanceDO instance,
                                 Map<String, Object> vars, FlowTaskOperateDTO dto) {
        // 完成当前 task
        completeAndArchive(task, dto.getComment());

        // 检查同 nodeCode 的 PENDING task 数
        int pendingCount = taskMapper.countPendingByNode(instance.getId(), task.getNodeCode());

        // 优先求值 completionCondition 表达式（支持提前完成）
        String completionCondition = resolveCompletionCondition(task);
        if (completionCondition != null) {
            // FOREACH 完成计数：approveCount = 集合元素总数（从 node.ext 查询 + countPendingByNode 反推）
            // 注：FOREACH 每条 task 独立，approveFinished 不聚合，这里用"已完成数"和"总数"求值
            int totalElements = countTotalForeachElements(instance.getId(), task.getNodeCode());
            int completedElements = totalElements - pendingCount;
            boolean shouldAdvance = evaluateCompletionCondition(completionCondition,
                    completedElements, totalElements, vars);
            if (shouldAdvance && pendingCount > 0) {
                // 提前完成：跳过剩余 PENDING task
                taskMapper.skipByNode(instance.getId(), task.getNodeCode(),
                        FlowTaskStatus.SKIPPED.name());
                pendingCount = 0;
            }
        }

        if (pendingCount > 0) {
            // 还有未完成 task，不推进
            support.audit(task, "FOREACH_PASS", dto.getUserId(), null,
                    dto.getComment(), dto.getCommentType());
            log.info("[Flow] FOREACH 部分完成: taskId={} node={} pending={}",
                    task.getId(), task.getNodeCode(), pendingCount);
            return;
        }

        // 全部完成：推进到下一节点
        List<FlowNodeDO> nextNodes = advancer.advance(instance, task.getNodeCode(),
                "PASS", null, vars);
        instanceService.generateTasksForNodes(task.getInstanceId(), nextNodes, vars);
        updateInstanceNode(instance, nextNodes);
        fireTaskCompleted(task.getId(), "PASS", vars);
        support.audit(task, "FOREACH_PASS_ALL", dto.getUserId(), null,
                dto.getComment(), dto.getCommentType());
        log.info("[Flow] FOREACH 全部完成: taskId={} node={}", task.getId(), task.getNodeCode());
    }

    /**
     * GAP-P2-10: 统计 FOREACH 节点的总元素数（已完成 + PENDING）
     *
     * <p>用于 completionCondition 求值时注入 nrOfInstances。
     * 查询 pmis_flow_run_task 中同 instanceId + nodeCode 的所有 task 数（不限状态）。
     */
    private int countTotalForeachElements(String instanceId, String nodeCode) {
        // 使用 countPendingByNode + 已完成数（从历史表或 task 表统计）
        // 简化实现：查 pmis_flow_run_task 同节点全部 task 数（含 COMPLETED/SKIPPED/PENDING）
        // 这里用 FlowRunTaskMapper 的 selectList + 过滤实现，避免新增 Mapper 方法
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FlowRunTaskDO> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(FlowRunTaskDO::getInstanceId, instanceId)
                .eq(FlowRunTaskDO::getNodeCode, nodeCode);
        return Math.toIntExact(taskMapper.selectCount(wrapper));
    }

    /** P1-12: 顺序会签 — 按序逐一处理（GAP-P1: 乐观锁防并发） */
    private void doSequentialPass(FlowRunTaskDO task, FlowInstanceDO instance,
                                   Map<String, Object> vars, FlowTaskOperateDTO dto) {
        int finished = (task.getApproveFinished() == null ? 0 : task.getApproveFinished()) + 1;
        int required = task.getApproveCount() == null ? 1 : task.getApproveCount();
        // GAP-P1: 乐观锁 — updateById 携带 @Version 自动检查版本号
        task.setApproveFinished(finished);
        int updated = taskMapper.updateById(task);
        if (updated == 0) {
            throw new BizException(BizErrorCode.RESOURCE_CONFLICT,
                    "error.workflow.msg_199e8ba1", task.getId());
        }
        if (finished < required) {
            // 还有下一个用户：切换办理人
            List<FlowUserDO> unprocessed = userMapper.selectUnprocessedByInstanceAndNode(
                    instance.getId(), task.getNodeCode());
            if (!unprocessed.isEmpty()) {
                FlowUserDO next = unprocessed.get(0);
                taskMapper.updateAssignee(task.getId(), next.getUserId(), next.getUserName(),
                        FlowAssigneeType.USER.name());
                support.audit(task, "SEQUENTIAL_PASS", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
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
        support.audit(task, "SEQUENTIAL_PASS_ALL", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
        log.info("[Flow] 顺序会签全部通过: taskId={}", task.getId());
    }

    /** P1-12 + P1-5: 票签 — 可配置通过率（默认 50%+1，支持 0~1 之间任意阈值） */
    private void doVotePass(FlowRunTaskDO task, FlowInstanceDO instance,
                             Map<String, Object> vars, FlowTaskOperateDTO dto) {
        int finished = (task.getApproveFinished() == null ? 0 : task.getApproveFinished()) + 1;
        int required = task.getApproveCount() == null ? 1 : task.getApproveCount();
        // GAP-P1: 乐观锁 — updateById 携带 @Version 自动检查版本号
        task.setApproveFinished(finished);
        int updated = taskMapper.updateById(task);
        if (updated == 0) {
            throw new BizException(BizErrorCode.RESOURCE_CONFLICT,
                    "error.workflow.msg_199e8ba1", task.getId());
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
            support.audit(task, "VOTE_PASS", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
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
        support.audit(task, "VOTE_PASS_THRESHOLD", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
        log.info("[Flow] 票签达到阈值: taskId={} finished={}/{} (rate={})",
                task.getId(), finished, threshold, task.getVotePassRate());
    }

    /**
     * P1-5: 加权票签 — 按办理人 weight 累加，权重达到阈值才推进。
     */
    private void doWeightedVotePass(FlowRunTaskDO task, FlowInstanceDO instance,
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
                    "error.workflow.msg_199e8ba1", task.getId());
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
            support.audit(task, "WEIGHTED_VOTE_PASS", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
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
        support.audit(task, "WEIGHTED_VOTE_THRESHOLD", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
        log.info("[Flow] 加权票签达到阈值: taskId={} passedWeight={}/{} totalWeight={} (rate={})",
                task.getId(), passedWeight, threshold, totalWeight, task.getVotePassRate());
    }

    // ============================== 私有辅助 ==============================

    private void completeAndArchive(FlowRunTaskDO task, String comment) {
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
        // P0-1: 任务完成后取消关联的边界事件订阅（userTask 正常完成，不再等待边界事件触发）
        eventSubscriptionService.cancelByTask(task.getId(), "TASK_COMPLETED");
    }

    private void updateInstanceNode(FlowInstanceDO instance, List<FlowNodeDO> nextNodes) {
        if (!nextNodes.isEmpty() && nextNodes.get(0).getNodeType()
                != FlowNodeType.END.getCode()) {
            instanceMapper.updateStatus(instance.getId(), instance.getFlowStatus(),
                    nextNodes.get(0).getNodeCode(), nextNodes.get(0).getNodeName(),
                    null, null);
        }
    }

    /** 归档任务到历史表 */
    private void archiveTask(FlowRunTaskDO src, FlowTaskStatus finalStatus) {
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
        his.setVotePassRate(src.getVotePassRate());
        his.setTaskStatus(finalStatus.name());
        his.setComment(src.getComment());
        his.setCreatedAt(src.getCreatedAt());
        his.setClaimAt(src.getClaimAt());
        his.setFinishAt(src.getFinishAt());
        his.setDurationMs(src.getDurationMs());
        his.setTenantId(src.getTenantId());
        his.setProviderTraceId(src.getProviderTraceId());
        // GAP-P2-10: 归档保留 iter_var,FOREACH 任务审批历史可追溯
        his.setIterVar(src.getIterVar());
        hisTaskMapper.insert(his);
    }

    private Map<String, Object> mergeVariables(FlowInstanceDO instance, Map<String, Object> extra) {
        if (instance == null || !StringUtils.hasText(instance.getVariable())) {
            return extra == null ? Collections.emptyMap() : extra;
        }
        try {
            Map<String, Object> base = JsonUtils.parseMap(instance.getVariable());
            if (extra != null && !extra.isEmpty()) {
                base.putAll(extra);
            }
            return base;
        } catch (Exception e) {
            return extra == null ? Collections.emptyMap() : extra;
        }
    }

    // ============================== 事件 ==============================

    private void fireTaskCompleted(String taskId, String action, Map<String, Object> vars) {
        support.fireEvent(l -> l.onTaskCompleted(taskId, action, vars), taskId);
        // P2-37: 同时调用携带上下文的重载版本
        FlowEventContext ctx = new FlowEventContext();
        ctx.setTaskId(taskId);
        ctx.setAction(action);
        ctx.setOperatedAt(LocalDateTime.now());
        support.fireEvent(l -> l.onTaskCompleted(taskId, ctx), taskId);
        // P2-35: 发布 Spring 异步事件
        support.publishWorkflowEvent("TASK_COMPLETED", null, taskId);
    }

    private void fireInstanceRejected(String instanceId, String reason) {
        support.fireEvent(l -> l.onInstanceRejected(instanceId, reason), null);
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
                    "error.workflow.msg_fcd55e62");
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

    // ============================== P1-4: 服务节点自动执行 ==============================

    /**
     * P1-4: 执行 SERVICE 服务节点 — 自动执行不创建人工任务
     *
     * <p>从节点 ext JSON 读取 serviceType（HTTP/SCRIPT/AUTO_PASS）配置并执行：
     * <ul>
     *   <li>成功：创建 COMPLETED 任务记录（审计追溯），归档，审计，推进到下一节点</li>
     *   <li>失败：创建任务记录，归档，审计，标记实例为 ERROR 异常状态</li>
     * </ul>
     *
     * @param instance  流程实例
     * @param node      服务节点
     * @param variables 流程变量
     * @return 任务 ID（COMPLETED 状态，仅用于审计追溯）
     */
    private String executeServiceNode(FlowInstanceDO instance, FlowNodeDO node,
                                     Map<String, Object> variables) {
        // 1. 执行服务节点逻辑（HTTP/SCRIPT/AUTO_PASS）
        FlowServiceNodeExecutor.ServiceExecutionResult result =
                serviceNodeExecutor.execute(node, variables);

        // 2. 创建任务记录（COMPLETED/TIMEOUT，用于审计追溯，非人工任务）
        FlowRunTaskDO task = new FlowRunTaskDO();
        task.setInstanceId(instance.getId());
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
        task.setApproveFinished(1);
        task.setAssigneeType(FlowAssigneeType.USER.name());
        task.setAssigneeId("0");
        task.setAssigneeName("SYSTEM_SERVICE");
        task.setTenantId(instance.getTenantId());
        task.setProviderTraceId(instance.getProviderTraceId());
        LocalDateTime now = LocalDateTime.now();
        task.setFinishAt(now);
        task.setDurationMs(0L);

        if (result.success()) {
            // 3a. 成功：标记 COMPLETED，归档，审计，推进
            task.setTaskStatus(FlowTaskStatus.COMPLETED.name());
            task.setComment(result.message());
            taskMapper.insert(task);
            archiveTask(task, FlowTaskStatus.COMPLETED);
            support.audit(task, "SERVICE_EXECUTE", null, null,
                    "服务节点执行成功: " + result.message());
            log.info("[Flow] 服务节点执行成功: instanceId={} node={} msg={}",
                    instance.getId(), node.getNodeCode(), result.message());
            // 推进到下一节点（复用 AUTO_PASS 推进逻辑，含递归深度保护）
            advanceAfterAutoPass(instance, node, variables);
        } else {
            // 3b. 失败：标记 TIMEOUT，归档，审计，实例标记为异常
            task.setTaskStatus(FlowTaskStatus.TIMEOUT.name());
            task.setComment("服务节点执行失败: " + result.message());
            taskMapper.insert(task);
            archiveTask(task, FlowTaskStatus.TIMEOUT);
            support.audit(task, "SERVICE_ERROR", null, null,
                    "服务节点执行失败: " + result.message());
            // 标记实例为异常状态，需人工介入处理
            instanceMapper.updateStatus(instance.getId(),
                    FlowInstanceStatus.ERROR.name(),
                    node.getNodeCode(), node.getNodeName(), null, null);
            log.error("[Flow] 服务节点执行失败，实例标记为异常: instanceId={} node={} msg={}",
                    instance.getId(), node.getNodeCode(), result.message());
        }
        return task.getId();
    }

    // ============================== P1-5: 跨节点办理人去重 ==============================

    /**
     * P1-5: 判断节点是否启用跨节点去重（ext JSON 中 autoDedup=true）
     *
     * @param node 流程节点
     * @return true=启用跨节点去重
     */
    private boolean isAutoDedupEnabled(FlowNodeDO node) {
        if (node == null || !StringUtils.hasText(node.getExt())) {
            return false;
        }
        try {
            Map<String, Object> ext = parseExtConfig(node.getExt());
            Object val = ext.get("autoDedup");
            if (val == null) {
                return false;
            }
            if (val instanceof Boolean b) {
                return b;
            }
            return Boolean.parseBoolean(String.valueOf(val));
        } catch (Exception e) {
            log.warn("[Flow] 解析 autoDedup 配置失败: node={} err={}",
                    node.getNodeCode(), e.getMessage());
            return false;
        }
    }

    /**
     * P1-5: 跨节点办理人去重 — 从候选办理人中排除同实例下已审批过的人员
     *
     * <p>查询 his_task 中 task_status=COMPLETED 的办理人列表，
     * 从当前节点的候选办理人中排除已审批过的人员。
     * 支持钉钉"一人多环节只审批一次"场景。
     *
     * @param userIds    当前节点的候选办理人列表
     * @param instanceId 流程实例 ID
     * @param node       流程节点（用于日志）
     * @return 去重后的候选办理人列表
     */
    private List<String> applyCrossNodeDedup(List<String> userIds, String instanceId,
                                              FlowNodeDO node) {
        try {
            List<String> completedAssignees = hisTaskMapper.selectCompletedAssigneeIds(instanceId);
            if (completedAssignees == null || completedAssignees.isEmpty()) {
                return userIds;
            }
            Set<String> completedSet = new HashSet<>(completedAssignees);
            int beforeSize = userIds.size();
            List<String> deduped = userIds.stream()
                    .filter(uid -> !completedSet.contains(uid))
                    .collect(Collectors.toList());
            log.info("[Flow] 跨节点办理人去重: instanceId={} node={} before={} after={} excluded={}",
                    instanceId, node.getNodeCode(), beforeSize, deduped.size(),
                    beforeSize - deduped.size());
            return deduped;
        } catch (Exception e) {
            log.warn("[Flow] 跨节点办理人去重异常，跳过去重: instanceId={} node={} err={}",
                    instanceId, node.getNodeCode(), e.getMessage());
            return userIds;
        }
    }

    /**
     * GAP-V2-05: 审批人自动去重检查
     */
    private String tryAutoDedup(FlowRunTaskDO task, FlowInstanceDO instance, FlowNodeDO node,
                              Map<String, Object> variables, String currentAssigneeId) {
        try {
            // 查询同实例下最近一条已完成任务（按主键倒序取最新一条）
            LambdaQueryWrapper<FlowRunTaskDO> qw = new LambdaQueryWrapper<>();
            qw.eq(FlowRunTaskDO::getInstanceId, instance.getId())
                    .eq(FlowRunTaskDO::getTaskStatus, FlowTaskStatus.COMPLETED.name())
                    .orderByDesc(FlowRunTaskDO::getId)
                    .last("LIMIT 1");
            List<FlowRunTaskDO> prevTasks = taskMapper.selectList(qw);
            if (prevTasks.isEmpty()) {
                return null;
            }
            FlowRunTaskDO prevTask = prevTasks.get(0);
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
            support.audit(task, "AUTO_DEDUP", null, null, "审批人与上一节点相同，自动去重跳过");
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
    private Map<String, Object> parseExtConfig(String ext) {
        if (!StringUtils.hasText(ext)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> map = JsonUtils.parseMap(ext);
            return map == null ? Collections.emptyMap() : map;
        } catch (Exception e) {
            log.warn("[Flow] 解析 node.ext JSON 失败: {} err={}", ext, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /** 从 extConfig 中读取配置值（字符串形式，兼容 ID 字符串化） */
    private String parseLongConfig(Map<String, Object> config, String key, String defaultValue) {
        Object val = config.get(key);
        if (val == null) return defaultValue;
        if (val instanceof Number n) return String.valueOf(n.longValue());
        return String.valueOf(val);
    }

    /** 展开办理人为用户列表 */
    private List<String> expandAssignees(FlowNodeDO node, Map<String, Object> variables) {
        // P1-4: 优先读取 ext.collection 配置，从流程变量动态展开会签人员集合
        Map<String, Object> nodeExt = parseExtConfig(node.getExt());
        Object collectionVar = nodeExt.get("collection");
        if (collectionVar != null && variables != null && !variables.isEmpty()) {
            String varName = String.valueOf(collectionVar).trim();
            // 支持表达式形式 ${varName}
            if (varName.startsWith("${") && varName.endsWith("}")) {
                varName = varName.substring(2, varName.length() - 1).trim();
            }
            Object collectionValue = variables.get(varName);
            if (collectionValue == null) {
                // 尝试 _selfSelect_<nodeCode> 命名约定
                collectionValue = variables.get("_selfSelect_" + node.getNodeCode());
            }
            List<String> expanded = expandCollectionValue(collectionValue);
            if (!expanded.isEmpty()) {
                log.info("[Flow] collection 变量展开: nodeCode={} var={} count={}",
                        node.getNodeCode(), varName, expanded.size());
                return expanded;
            }
            // collection 配置存在但变量为空，返回空列表触发 emptyStrategy 兜底
            log.warn("[Flow] collection 变量为空: nodeCode={} var={}", node.getNodeCode(), varName);
            return Collections.emptyList();
        }

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
        Set<String> seen = new HashSet<>();
        for (String token : resolved.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) continue;
            // P1-4: self_select: 前缀展开 _selfSelect_<nodeCode> 变量（修复原仅存变量名不展开的问题）
            if (t.startsWith("self_select:")) {
                String varName = t.substring("self_select:".length()).trim();
                Object selfSelectVal = variables != null ? variables.get("_selfSelect_" + node.getNodeCode()) : null;
                if (selfSelectVal == null && variables != null && !varName.isEmpty()) {
                    selfSelectVal = variables.get(varName);
                }
                List<String> expanded = expandCollectionValue(selfSelectVal);
                for (String uid : expanded) {
                    if (seen.add(uid)) {
                        result.add(uid);
                    }
                }
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
                    log.debug("[FlowTaskCompleteServiceImpl] multi_leader 层级解析失败，使用默认 1 levelStr={}: {}", levelStr, ignored.getMessage());
                }
                String startUserId = resolveInitiatorId(variables);
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
            // P1-5: dept_leader: 前缀通过 SPI 调用 expandDeptLeader 解析部门负责人
            if (t.startsWith("dept_leader:")) {
                String deptId = t.substring("dept_leader:".length()).trim();
                if (!deptId.isEmpty()) {
                    Long leaderId = assigneeResolver.expandDeptLeader(deptId, variables);
                    if (leaderId != null) {
                        String s = String.valueOf(leaderId);
                        if (seen.add(s)) {
                            result.add(s);
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
     * P1-4: 将 collection / self_select 变量值展开为用户 ID 字符串列表
     *
     * <p>支持以下值类型：
     * <ul>
     *   <li>List&lt;Long&gt; / List&lt;String&gt; — 逐元素转字符串</li>
     *   <li>逗号分隔 String — split 后逐段处理</li>
     *   <li>单个数字 String — 直接作为单元素</li>
     *   <li>null — 返回空列表</li>
     * </ul>
     */
    private List<String> expandCollectionValue(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item == null) continue;
                String s = String.valueOf(item).trim();
                if (!s.isEmpty()) {
                    result.add(s);
                }
            }
        } else if (value instanceof Object[] arr) {
            for (Object item : arr) {
                if (item == null) continue;
                String s = String.valueOf(item).trim();
                if (!s.isEmpty()) {
                    result.add(s);
                }
            }
        } else {
            String s = String.valueOf(value).trim();
            if (!s.isEmpty()) {
                // 逗号分隔支持
                for (String part : s.split(",")) {
                    String p = part.trim();
                    if (!p.isEmpty()) {
                        result.add(p);
                    }
                }
            }
        }
        return result;
    }

    /**
     * P2-39: 从流程变量中解析发起人 ID（用于 multi_leader 展开的起始用户）
     */
    private String resolveInitiatorId(Map<String, Object> variables) {
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
        return String.valueOf(val);
    }

    /** 解析会签类型 */
    private FlowPerformType resolvePerformType(FlowNodeDO node) {
        if (node.getExt() != null) {
            try {
                Map<?, ?> ext = JsonUtils.parseMap(node.getExt());
                Object ptObj = ext.get("performType");
                if (ptObj instanceof String pt) {
                    return FlowPerformType.valueOf(pt);
                }
            } catch (Exception ignored) {
                log.debug("[FlowTaskCompleteServiceImpl] performType 解析失败，使用默认 OR nodeCode={}: {}", node.getNodeCode(), ignored.getMessage());
            }
        }
        return FlowPerformType.OR;
    }

    private void resolveAssignee(FlowRunTaskDO task, FlowNodeDO node,
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
        } else if (resolved.startsWith("dept_leader:")) {
            // P1-5: 部门负责人，assigneeId 为部门 ID
            task.setAssigneeType(FlowAssigneeType.DEPT_LEADER.name());
            task.setAssigneeId(resolved.substring("dept_leader:".length()));
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

    private String parseAssignorId(String assigneeId) {
        if (assigneeId == null || !assigneeId.matches("\\d+")) {
            return null;
        }
        return assigneeId;
    }

    private FlowRunTaskDO toClaimTask(FlowRunTaskDO src, String userId) {
        src.setAssigneeId(String.valueOf(userId));
        src.setTaskStatus(FlowTaskStatus.CLAIMED.name());
        src.setClaimAt(LocalDateTime.now());
        return src;
    }

    // ============================== P1-5: 加权票签辅助 ==============================

    /**
     * P1-5: 应用投票配置 — 从 node.ext 读取 votePassRate 写入 task
     */
    private void applyVoteConfig(FlowRunTaskDO task, FlowNodeDO node) {
        if (node == null || !StringUtils.hasText(node.getExt())) {
            return;
        }
        try {
            Map<String, Object> ext = parseExtConfig(node.getExt());
            Object rate = ext.get("votePassRate");
            if (rate != null) {
                BigDecimal rateValue = toBigDecimal(rate);
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
     */
    private Map<String, Integer> parseUserWeights(String ext) {
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
                Map<String, Integer> result = new HashMap<>();
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
                            log.debug("[FlowTaskCompleteServiceImpl] 会签权重解析失败，跳过 key={} val={}: {}", key, val, ignored.getMessage());
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
    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return null;
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(String.valueOf(val));
        } catch (NumberFormatException e) {
            log.warn("[FlowTaskCompleteServiceImpl] BigDecimal 转换失败 val={}: {}", val, e.getMessage());
            return null;
        }
    }

    // ============================== P1-4: 委派代理日志 ==============================

    /**
     * P1-4: 写入委派代理日志（当被委派人 PASS/REJECT/CLAIM/TRANSFER 时记录）
     */
    private void logDelegateOperation(FlowRunTaskDO task, String action, String opType) {
        if (task == null || delegateLogMapper == null) {
            return;
        }
        try {
            String ownerId = task.getAssignorId();
            String delegateId = parseAssignorId(task.getAssigneeId());
            if (ownerId == null || delegateId == null) {
                return; // 非代理场景
            }
            FlowDelegateLogDO log = new FlowDelegateLogDO();
            log.setTenantId(task.getTenantId());
            // P0-3 修复：重新匹配授权规则以获取 authId（不再硬编码 0L）
            log.setAuthId(resolveDelegateAuthId(task, ownerId));
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
            FlowTaskCompleteServiceImpl.log.warn("[Flow] 委派代理日志写入失败: taskId={} err={}",
                    task.getId(), e.getMessage());
        }
    }

    /**
     * P0-3: 重新匹配授权规则以获取 authId
     *
     * <p>在 {@link #applyDelegateRedirect} 中 authId 仅打印日志未持久化，
     * 此处通过 ownerId + flowCode + nodeCode 重新匹配授权规则以恢复 authId。
     * 匹配失败（授权已过期/已撤回）时返回 0L，不影响日志写入。
     */
    private String resolveDelegateAuthId(FlowRunTaskDO task, String ownerId) {
        try {
            FlowInstanceDO instance = instanceMapper.selectById(task.getInstanceId());
            if (instance == null) {
                return null;
            }
            FlowDelegateAuthDO matched = delegateAuthService.matchAuth(
                    instance.getTenantId(), ownerId,
                    instance.getFlowCode(), task.getNodeCode());
            return matched != null ? matched.getId() : null;
        } catch (Exception e) {
            FlowTaskCompleteServiceImpl.log.debug("[Flow] 委派 authId 解析失败: taskId={} err={}",
                    task.getId(), e.getMessage());
            return null;
        }
    }
}
