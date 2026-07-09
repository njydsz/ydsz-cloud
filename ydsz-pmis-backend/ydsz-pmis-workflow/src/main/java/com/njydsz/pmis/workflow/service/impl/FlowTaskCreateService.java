package com.njydsz.pmis.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.util.JsonUtils;
import com.njydsz.pmis.workflow.dto.FlowAssigneeDTO;
import com.njydsz.pmis.workflow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.engine.FlowAssigneeResolver;
import com.njydsz.pmis.workflow.engine.FlowServiceNodeExecutor;
import com.njydsz.pmis.workflow.engine.FlowVariableStrategy;
import com.njydsz.pmis.workflow.entity.FlowDelegateAuthDO;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务创建服务
 *
 * <p>从 {@code FlowTaskCompleteServiceImpl} 拆分的"任务创建"职责。
 * 是任务生命周期中最复杂的服务，承担以下创建场景：
 * <ul>
 *   <li>普通审批节点（resolveAssignee 解析）</li>
 *   <li>SERVICE 服务节点（HTTP/SCRIPT/AUTO_PASS 自动执行）</li>
 *   <li>FOREACH 循环节点（每个集合元素独立 task）</li>
 *   <li>LEVEL_APPROVAL 逐级审批节点（动态展开多级上级）</li>
 *   <li>审批人为空兜底（AUTO_PASS/TRANSFER_ADMIN/ASSIGN_SPECIFIED/FALLBACK）</li>
 *   <li>跨节点办理人去重（P1-5）</li>
 *   <li>自动审批节点（P2-4 GAP-14）</li>
 *   <li>长期授权委派改写（P1-4）</li>
 * </ul>
 *
 * <p>被 {@code FlowTaskPassService} / {@code FlowTaskRejectService} / {@code FlowTaskOperateService} /
 * {@code FlowInstanceService} 等多个调用方复用。
 *
 * @author ydsz-pmis-team
 * @since 1.7.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskCreateService {

    /** P0-1: 审批人为空统一默认 FALLBACK（最保守：转交管理员人工处理） */
    private static final String DEFAULT_EMPTY_STRATEGY = "FALLBACK";

    /** AUTO_PASS 递归深度保护（防止流程定义环路导致栈溢出） */
    private static final ThreadLocal<Integer> AUTO_PASS_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final int MAX_AUTO_PASS_DEPTH = 20;

    private final FlowRunTaskMapper taskMapper;
    private final FlowUserMapper userMapper;
    private final FlowInstanceMapper instanceMapper;
    private final FlowNodeMapper nodeMapper;
    private final FlowAdvancer advancer;
    private final FlowVariableStrategy variableStrategy;
    private final FlowAssigneeResolver assigneeResolver;
    private final FlowDelegateAuthService delegateAuthService;
    private final FlowTaskSupport support;
    private final FlowTaskArchiveService archiveService;
    /** 使用 @Lazy 避免循环依赖：FlowTaskPassService → FlowTaskCreateService */
    @Lazy
    private final FlowTaskPassService passService;
    private final FlowInstanceService instanceService;
    /** P1-6: SLA 服务（任务创建时应用 SLA 配置） */
    @Lazy
    private final FlowSlaService slaService;
    /** P1-7: 待办数 WebSocket 推送服务 */
    @Lazy
    private final FlowTodoCountPushService todoCountPushService;
    /** P1-4: 服务节点执行器（HTTP/SCRIPT/AUTO_PASS） */
    private final FlowServiceNodeExecutor serviceNodeExecutor;
    /** P0-1: 事件订阅服务（服务节点失败时触发 error boundary） */
    @Lazy
    private final FlowEventSubscriptionService eventSubscriptionService;
    /** P2-3: Prometheus 指标（可能为 null：测试环境） */
    private final FlowMetrics flowMetrics;

    // ============================== 公共创建入口 ==============================

    /**
     * 创建任务（向后兼容重载）
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
     */
    @Transactional(rollbackFor = Exception.class)
    public String createTask(String instanceId, FlowNodeDO node, Map<String, Object> variables,
                             List<String> explicitAssignees) {
        FlowInstanceDO instance = lookupInstance(instanceId);

        // P1-4: SERVICE 服务节点 — 自动执行
        if (isNodeType(node, FlowNodeType.SERVICE)) {
            return executeServiceNode(instance, node, variables);
        }

        // GAP-P2-10: FOREACH 循环节点 — 对集合中每个元素创建独立 task
        if (isNodeType(node, FlowNodeType.FOREACH)) {
            return createForeachTasks(instance, node, variables, explicitAssignees);
        }

        // P0-4: LEVEL_APPROVAL 逐级审批节点 — 动态展开多级上级
        if (isNodeType(node, FlowNodeType.LEVEL_APPROVAL)) {
            List<String> levelApprovers = expandLevelApprovers(instance, node, variables, explicitAssignees);
            if (levelApprovers.isEmpty()) {
                return createTaskWithEmptyAssignee(instance, node, variables);
            }
            return createLevelApprovalTask(instance, node, variables, levelApprovers);
        }

        // 解析办理人：GAP-P2-9 显式指定优先；否则尝试展开 ROLE/DEPT 为多人
        List<String> userIds = (explicitAssignees != null && !explicitAssignees.isEmpty())
                ? new ArrayList<>(explicitAssignees)
                : expandAssignees(node, variables);
        FlowPerformType performType = resolvePerformType(node);

        // P1-5: 跨节点办理人去重
        boolean autoDedup = isAutoDedupEnabled(node);
        if (autoDedup && !userIds.isEmpty()) {
            userIds = applyCrossNodeDedup(userIds, instanceId, node);
        }

        FlowRunTaskDO task = buildBaseTask(instance, node, performType, userIds.size());

        if (userIds.isEmpty()) {
            // 跨节点去重后候选人为空 — 自动跳过该节点
            if (autoDedup) {
                return handleAutoDedupSkip(task, instance, node, variables);
            }
            // P0-1: 审批人为空兜底处理
            return handleEmptyAssignee(task, instance, node, variables);
        }

        // 正常路径：设置首个办理人 + 写入 pmis_flow_user
        task.setAssigneeType(FlowAssigneeType.USER.name());
        task.setAssigneeId(userIds.get(0));
        task.setAssigneeName("USER:" + userIds.get(0));
        applyVoteConfig(task, node);
        // GAP-V2-05: 审批人自动去重 — 仅 OR 触发
        if (performType == FlowPerformType.OR) {
            String dedupTaskId = tryAutoDedup(task, instance, node, variables, userIds.get(0));
            if (dedupTaskId != null) {
                return dedupTaskId;
            }
        }
        taskMapper.insert(task);
        // 写入 pmis_flow_user
        Map<String, Integer> userWeights = parseUserWeights(node.getExt());
        for (String uid : userIds) {
            insertFlowUser(task, instance, node, uid, userWeights);
        }
        log.info("[Flow] 创建任务: instanceId={} node={} performType={} assigneeCount={}",
                instanceId, node.getNodeCode(), performType, userIds.size());
        // P1-4: 应用长期授权委派
        applyDelegateRedirect(task, instance, node);
        support.fireEvent(l -> l.onTaskCreated(task.getId()), task.getId());
        support.publishWorkflowEvent("TASK_CREATED", instanceId, task.getId());
        // P1-7: WebSocket 推送
        if (todoCountPushService != null) {
            todoCountPushService.pushTaskAssigned(task);
        }
        // P2-4: 自动审批节点
        tryAutoApprove(instance, node, task, variables);
        return task.getId();
    }

    // ============================== 内部方法 ==============================

    private FlowInstanceDO lookupInstance(String instanceId) {
        FlowInstanceDO instance = instanceService.getById(instanceId);
        if (instance == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "error.workflow.msg_fc4b1c16", instanceId);
        }
        return instance;
    }

    private boolean isNodeType(FlowNodeDO node, FlowNodeType type) {
        return node != null && node.getNodeType() != null && node.getNodeType() == type.getCode();
    }

    /**
     * 构建基础任务对象（设置通用字段）。
     */
    private FlowRunTaskDO buildBaseTask(FlowInstanceDO instance, FlowNodeDO node,
                                        FlowPerformType performType, int approveCount) {
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
        task.setPerformType(performType.name());
        task.setApproveCount(approveCount == 0 ? 1 : approveCount);
        task.setApproveFinished(0);
        task.setTaskStatus(FlowTaskStatus.PENDING.name());
        task.setTenantId(instance.getTenantId());
        task.setProviderTraceId(instance.getProviderTraceId());

        // P2-3: 指标
        if (flowMetrics != null) {
            flowMetrics.incTaskCreated(instance.getFlowCode(), node.getNodeCode());
        }
        // P1-1: 优先级
        applyPriority(task, node);
        // P1-6: SLA
        if (slaService != null) {
            slaService.applySlaConfig(task, node);
        }
        return task;
    }

    /**
     * 跨节点去重后候选人为空 — 自动跳过该节点。
     */
    private String handleAutoDedupSkip(FlowRunTaskDO task, FlowInstanceDO instance, FlowNodeDO node,
                                       Map<String, Object> variables) {
        task.setAssigneeType(FlowAssigneeType.USER.name());
        task.setAssigneeId("0");
        task.setAssigneeName("SYSTEM_DEDUP_SKIP");
        task.setTaskStatus(FlowTaskStatus.COMPLETED.name());
        LocalDateTime now = LocalDateTime.now();
        task.setFinishAt(now);
        task.setDurationMs(0L);
        taskMapper.insert(task);
        archiveService.archiveToHistory(task, FlowTaskStatus.COMPLETED);
        support.audit(task, "DEDUP_SKIP", null, null, "办理人去重后为空，自动跳过");
        log.info("[Flow] 办理人去重后为空，自动跳过: instanceId={} node={}",
                instance.getId(), node.getNodeCode());
        advanceAfterAutoPass(instance, node, variables);
        return task.getId();
    }

    /**
     * P0-1: 审批人为空兜底处理
     */
    private String handleEmptyAssignee(FlowRunTaskDO task, FlowInstanceDO instance, FlowNodeDO node,
                                       Map<String, Object> variables) {
        Map<String, Object> extConfig = parseExtConfig(node.getExt());
        String emptyStrategy = (String) extConfig.getOrDefault("emptyStrategy", DEFAULT_EMPTY_STRATEGY);

        switch (emptyStrategy) {
            case "AUTO_PASS": {
                task.setAssigneeType(FlowAssigneeType.USER.name());
                task.setAssigneeId("0");
                task.setAssigneeName("SYSTEM_AUTO_PASS");
                task.setTaskStatus(FlowTaskStatus.COMPLETED.name());
                LocalDateTime now = LocalDateTime.now();
                task.setFinishAt(now);
                task.setDurationMs(0L);
                taskMapper.insert(task);
                archiveService.archiveToHistory(task, FlowTaskStatus.COMPLETED);
                support.audit(task, "AUTO_PASS", null, null, "审批人为空，自动通过");
                log.info("[Flow] 审批人为空自动通过: instanceId={} node={}",
                        instance.getId(), node.getNodeCode());
                advanceAfterAutoPass(instance, node, variables);
                return task.getId();
            }
            case "TRANSFER_ADMIN": {
                String adminUserId = parseLongConfig(extConfig, "adminUserId", "1");
                task.setAssigneeType(FlowAssigneeType.USER.name());
                task.setAssigneeId(adminUserId);
                task.setAssigneeName("ADMIN_FALLBACK");
                taskMapper.insert(task);
                log.info("[Flow] 审批人为空转管理员: instanceId={} node={} adminId={}",
                        instance.getId(), node.getNodeCode(), adminUserId);
                return task.getId();
            }
            case "ASSIGN_SPECIFIED": {
                String specifiedUserId = parseLongConfig(extConfig, "specifiedUserId", "1");
                task.setAssigneeType(FlowAssigneeType.USER.name());
                task.setAssigneeId(specifiedUserId);
                task.setAssigneeName("SPECIFIED_FALLBACK");
                taskMapper.insert(task);
                log.info("[Flow] 审批人为空指定人员: instanceId={} node={} userId={}",
                        instance.getId(), node.getNodeCode(), specifiedUserId);
                return task.getId();
            }
            default: {
                // FALLBACK: 回退到原有 resolveAssignee 逻辑
                taskMapper.insert(task);
                resolveAssignee(task, node, variables, null, instance);
                taskMapper.updateById(task);
                return task.getId();
            }
        }
    }

    /**
     * P2-4 (GAP-14): 自动审批节点
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
        @SuppressWarnings("unchecked")
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
                passService.pass(autoDto);
                log.info("[Flow] 自动审批节点通过: instanceId={} node={} taskId={}",
                        instance.getId(), node.getNodeCode(), task.getId());
            } catch (Exception e) {
                log.warn("[Flow] 自动审批节点通过失败（降级为人工）: instanceId={} node={} err={}",
                        instance.getId(), node.getNodeCode(), e.getMessage());
            }
        }
    }

    /**
     * 写入 pmis_flow_user 记录
     */
    private void insertFlowUser(FlowRunTaskDO task, FlowInstanceDO instance, FlowNodeDO node,
                                String uid, Map<String, Integer> userWeights) {
        FlowUserDO fu = new FlowUserDO();
        fu.setTaskId(task.getId());
        fu.setInstanceId(instance.getId());
        fu.setNodeCode(node.getNodeCode());
        fu.setUserType(FlowAssigneeType.USER.name());
        fu.setUserId(uid);
        fu.setUserName("USER:" + uid);
        fu.setProcessed(0);
        fu.setWeight(userWeights == null ? 1 : userWeights.getOrDefault(uid, 1));
        fu.setSignType(FlowSignType.ORIGINAL.name());
        fu.setTenantId(instance.getTenantId());
        fu.setProviderTraceId(instance.getProviderTraceId());
        userMapper.insert(fu);
    }

    /**
     * P0-4: 创建逐级审批任务
     */
    private String createLevelApprovalTask(FlowInstanceDO instance, FlowNodeDO node,
                                           Map<String, Object> variables, List<String> approvers) {
        FlowRunTaskDO task = buildBaseTask(instance, node, FlowPerformType.SEQUENTIAL, approvers.size());
        task.setAssigneeType(FlowAssigneeType.USER.name());
        task.setAssigneeId(approvers.get(0));
        task.setAssigneeName("USER:" + approvers.get(0));
        task.setPriority(50);
        taskMapper.insert(task);
        for (String uid : approvers) {
            insertFlowUser(task, instance, node, uid, null);
        }
        if (flowMetrics != null) {
            flowMetrics.incTaskCreated(instance.getFlowCode(), node.getNodeCode());
        }
        if (todoCountPushService != null) {
            todoCountPushService.pushTaskAssigned(task);
        }
        support.fireEvent(l -> l.onTaskCreated(task.getId()), task.getId());
        support.publishWorkflowEvent("TASK_CREATED", instance.getId(), task.getId());
        applyDelegateRedirect(task, instance, node);
        log.info("[Flow] 逐级审批任务创建: instanceId={} node={} approvers={}",
                instance.getId(), node.getNodeCode(), approvers);
        return task.getId();
    }

    /**
     * P0-4: 逐级审批人为空时走 emptyStrategy 兜底
     */
    private String createTaskWithEmptyAssignee(FlowInstanceDO instance, FlowNodeDO node,
                                                Map<String, Object> variables) {
        Map<String, Object> extConfig = parseExtConfig(node.getExt());
        String emptyStrategy = (String) extConfig.getOrDefault("emptyStrategy", DEFAULT_EMPTY_STRATEGY);
        FlowRunTaskDO task = buildBaseTask(instance, node, FlowPerformType.OR, 1);

        switch (emptyStrategy) {
            case "AUTO_PASS":
            case "TRANSFER_ADMIN":
            case "ASSIGN_SPECIFIED": {
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
                    archiveService.archiveToHistory(task, FlowTaskStatus.COMPLETED);
                    support.audit(task, "LEVEL_APPROVAL_" + emptyStrategy, null, null,
                            "逐级审批展开为空，" + emptyStrategy);
                    advanceAfterAutoPass(instance, node, variables);
                }
                log.info("[Flow] 逐级审批空兜底: instanceId={} node={} strategy={}",
                        instance.getId(), node.getNodeCode(), emptyStrategy);
                return task.getId();
            }
            default: {
                task.setAssigneeType(FlowAssigneeType.USER.name());
                task.setAssigneeId("1");
                task.setAssigneeName("FALLBACK");
                taskMapper.insert(task);
                log.warn("[Flow] 逐级审批空兜底 FALLBACK: instanceId={} node={}",
                        instance.getId(), node.getNodeCode());
                return task.getId();
            }
        }
    }

    /**
     * GAP-P2-10: FOREACH 循环节点 — 对集合中每个元素创建独立 task
     */
    private String createForeachTasks(FlowInstanceDO instance, FlowNodeDO node,
                                      Map<String, Object> variables, List<String> explicitAssignees) {
        List<String> elements = (explicitAssignees != null && !explicitAssignees.isEmpty())
                ? new ArrayList<>(explicitAssignees)
                : expandAssignees(node, variables);

        if (elements.isEmpty()) {
            Map<String, Object> extConfig = parseExtConfig(node.getExt());
            String emptyStrategy = (String) extConfig.getOrDefault("emptyStrategy", DEFAULT_EMPTY_STRATEGY);
            if ("AUTO_PASS".equals(emptyStrategy)) {
                FlowRunTaskDO autoTask = buildForeachTask(instance, node, "0", "SYSTEM_AUTO_PASS", "0");
                autoTask.setTaskStatus(FlowTaskStatus.COMPLETED.name());
                autoTask.setFinishAt(LocalDateTime.now());
                autoTask.setDurationMs(0L);
                taskMapper.insert(autoTask);
                archiveService.archiveToHistory(autoTask, FlowTaskStatus.COMPLETED);
                support.audit(autoTask, "FOREACH_AUTO_PASS", null, null, "FOREACH 集合为空，自动通过");
                log.info("[Flow] FOREACH 集合为空自动通过: instanceId={} node={}",
                        instance.getId(), node.getNodeCode());
                advanceAfterAutoPass(instance, node, variables);
                return autoTask.getId();
            }
            log.warn("[Flow] FOREACH 集合为空，使用 {} 策略: node={}", emptyStrategy, node.getNodeCode());
            elements = List.of("1");
        }

        String firstTaskId = null;
        for (String element : elements) {
            FlowRunTaskDO task = buildForeachTask(instance, node, element, "USER:" + element, element);
            taskMapper.insert(task);
            insertFlowUser(task, instance, node, element, null);
            if (flowMetrics != null) {
                flowMetrics.incTaskCreated(instance.getFlowCode(), node.getNodeCode());
            }
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
        applyPriority(task, node);
        if (slaService != null) {
            slaService.applySlaConfig(task, node);
        }
        return task;
    }

    /**
     * P1-4: 长期授权委派改写
     */
    private void applyDelegateRedirect(FlowRunTaskDO task, FlowInstanceDO instance, FlowNodeDO node) {
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
            task.setAssignorId(currentUserId);
            task.setAssignorName(matched.getOwnerUserName());
            task.setAssigneeId(matched.getDelegateUserId());
            task.setAssigneeName(matched.getDelegateUserName());
            taskMapper.updateById(task);
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

    /**
     * AUTO_PASS 后推进到下一节点（含递归深度保护）
     */
    private void advanceAfterAutoPass(FlowInstanceDO instance, FlowNodeDO node,
                                       Map<String, Object> variables) {
        int depth = AUTO_PASS_DEPTH.get();
        if (depth >= MAX_AUTO_PASS_DEPTH) {
            log.warn("[Flow] AUTO_PASS 递归深度超限: depth={} instanceId={}", depth, instance.getId());
            throw new BizException(BizErrorCode.INTERNAL_ERROR, "error.workflow.msg_fcd55e62");
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
     * 更新实例当前节点
     */
    private void updateInstanceNode(FlowInstanceDO instance, List<FlowNodeDO> nextNodes) {
        if (!nextNodes.isEmpty() && nextNodes.get(0).getNodeType()
                != FlowNodeType.END.getCode()) {
            instanceMapper.updateStatus(instance.getId(), instance.getFlowStatus(),
                    nextNodes.get(0).getNodeCode(), nextNodes.get(0).getNodeName(),
                    null, null);
        }
    }

    // ============================== 通用辅助方法 ==============================

    /**
     * P1-1: 从 node.ext.priority 读取优先级（默认 50）
     */
    private void applyPriority(FlowRunTaskDO task, FlowNodeDO node) {
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
    }

    /**
     * P1-5: 解析 node.ext.votePassRate / userWeights，配置加权票签
     */
    private void applyVoteConfig(FlowRunTaskDO task, FlowNodeDO node) {
        Map<String, Object> ext = parseExtConfig(node.getExt());
        Object rate = ext.get("votePassRate");
        if (rate instanceof Number n) {
            task.setVotePassRate(BigDecimal.valueOf(n.doubleValue()));
        } else if (rate instanceof String s && !s.isBlank()) {
            try {
                task.setVotePassRate(new java.math.BigDecimal(s.trim()));
            } catch (NumberFormatException ignore) {
                // keep default
            }
        }
    }

    /**
     * P1-5: 解析 node.ext.userWeights
     */
    private Map<String, Integer> parseUserWeights(String ext) {
        Map<String, Object> config = parseExtConfig(ext);
        Object weights = config.get("userWeights");
        if (weights instanceof Map<?, ?> m) {
            Map<String, Integer> result = new HashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getValue() instanceof Number n) {
                    result.put(String.valueOf(e.getKey()), n.intValue());
                }
            }
            return result;
        }
        return null;
    }

    /**
     * P0-4: 展开逐级审批的上级列表
     */
    private List<String> expandLevelApprovers(FlowInstanceDO instance, FlowNodeDO node,
                                              Map<String, Object> variables,
                                              List<String> explicitAssignees) {
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
                return Collections.emptyList();
            }
            List<String> result = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Long uid : leaders) {
                String s = String.valueOf(uid);
                String stopAtUserId = (String) extConfig.get("stopAtUserId");
                if (stopAtUserId != null && stopAtUserId.equals(s)) {
                    result.add(s);
                    break;
                }
                if (seen.add(s)) {
                    result.add(s);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("[Flow] 逐级审批展开异常: instanceId={} err={}", instance.getId(), e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * GAP-V2-05: 审批人自动去重检查
     */
    private String tryAutoDedup(FlowRunTaskDO task, FlowInstanceDO instance, FlowNodeDO node,
                              Map<String, Object> variables, String currentAssigneeId) {
        try {
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
            if (prevAssigneeId == null
                    || !prevAssigneeId.equals(currentAssigneeId)
                    || "SYSTEM_AUTO_PASS".equals(prevTask.getAssigneeName())) {
                return null;
            }
            log.info("[Flow] 审批人自动去重: instanceId={} node={} assigneeId={}",
                    instance.getId(), node.getNodeCode(), currentAssigneeId);
            task.setTaskStatus(FlowTaskStatus.COMPLETED.name());
            LocalDateTime now = LocalDateTime.now();
            task.setFinishAt(now);
            task.setDurationMs(0L);
            taskMapper.insert(task);
            archiveService.archiveToHistory(task, FlowTaskStatus.COMPLETED);
            support.audit(task, "AUTO_DEDUP", null, null, "审批人与上一节点相同，自动去重跳过");
            advanceAfterAutoPass(instance, node, variables);
            return task.getId();
        } catch (Exception e) {
            log.warn("[Flow] 审批人自动去重检查异常: instanceId={} node={} err={}",
                    instance.getId(), node.getNodeCode(), e.getMessage());
            return null;
        }
    }

    /**
     * P1-5: 跨节点办理人去重
     */
    private List<String> applyCrossNodeDedup(List<String> userIds, String instanceId, FlowNodeDO node) {
        try {
            // 查询实例下已审批过的人员（COMPLETED 状态）
            LambdaQueryWrapper<FlowRunTaskDO> qw = new LambdaQueryWrapper<>();
            qw.eq(FlowRunTaskDO::getInstanceId, instanceId)
                    .eq(FlowRunTaskDO::getTaskStatus, FlowTaskStatus.COMPLETED.name());
            List<FlowRunTaskDO> done = taskMapper.selectList(qw);
            Set<String> excluded = new HashSet<>();
            for (FlowRunTaskDO t : done) {
                if (t.getAssigneeId() != null && !"SYSTEM_AUTO_PASS".equals(t.getAssigneeName())) {
                    excluded.add(t.getAssigneeId());
                }
            }
            int beforeSize = userIds.size();
            List<String> deduped = new ArrayList<>();
            for (String uid : userIds) {
                if (!excluded.contains(uid)) {
                    deduped.add(uid);
                }
            }
            log.info("[Flow] 跨节点办理人去重: instanceId={} node={} before={} after={} excluded={}",
                    instanceId, node.getNodeCode(), beforeSize, deduped.size(), beforeSize - deduped.size());
            return deduped;
        } catch (Exception e) {
            log.warn("[Flow] 跨节点办理人去重异常，跳过去重: instanceId={} node={} err={}",
                    instanceId, node.getNodeCode(), e.getMessage());
            return userIds;
        }
    }

    /**
     * P1-5: 判断节点是否启用跨节点去重
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
            return false;
        }
    }

    /**
     * 解析会签类型
     */
    private FlowPerformType resolvePerformType(FlowNodeDO node) {
        if (node.getExt() != null) {
            try {
                Map<?, ?> ext = JsonUtils.parseMap(node.getExt());
                Object ptObj = ext.get("performType");
                if (ptObj instanceof String pt) {
                    return FlowPerformType.valueOf(pt);
                }
            } catch (Exception ignored) {
                log.debug("[FlowTaskCreateService] performType 解析失败，使用默认 OR: {}", ignored.getMessage());
            }
        }
        return FlowPerformType.OR;
    }

    /**
     * 展开办理人为用户列表
     *
     * <p>P0-2 增强：当节点 ext 配置 {@code selfSelect: true} 时，优先从流程变量中
     * 读取发起人自选审批人（{@code _selfSelect_<nodeCode>}），无需在 permissionFlag
     * 中显式配置 {@code self_select:} 前缀。自选变量为空时回退到 permissionFlag 解析。
     */
    private List<String> expandAssignees(FlowNodeDO node, Map<String, Object> variables) {
        Map<String, Object> nodeExt = parseExtConfig(node.getExt());

        // P0-2: 节点 ext 配置 selfSelect=true 时，优先读取自选审批人
        Object selfSelectFlag = nodeExt.get("selfSelect");
        if (selfSelectFlag != null && isBooleanTrue(selfSelectFlag) && variables != null) {
            Object selfSelectVal = variables.get("_selfSelect_" + node.getNodeCode());
            List<String> selfSelectExpanded = expandCollectionValue(selfSelectVal);
            if (!selfSelectExpanded.isEmpty()) {
                log.info("[Flow] P0-2 自选审批人展开: nodeCode={} count={}",
                        node.getNodeCode(), selfSelectExpanded.size());
                return selfSelectExpanded;
            }
            // 自选变量为空 → 检查是否允许回退到 permissionFlag
            Object allowFallback = nodeExt.get("selfSelectAllowFallback");
            if (!isBooleanTrue(allowFallback)) {
                log.warn("[Flow] P0-2 自选审批人为空且未配置 fallback: nodeCode={}", node.getNodeCode());
                return Collections.emptyList();
            }
            log.info("[Flow] P0-2 自选审批人为空，回退到 permissionFlag: nodeCode={}", node.getNodeCode());
        }

        Object collectionVar = nodeExt.get("collection");
        if (collectionVar != null && variables != null && !variables.isEmpty()) {
            String varName = String.valueOf(collectionVar).trim();
            if (varName.startsWith("${") && varName.endsWith("}")) {
                varName = varName.substring(2, varName.length() - 1).trim();
            }
            Object collectionValue = variables.get(varName);
            if (collectionValue == null) {
                collectionValue = variables.get("_selfSelect_" + node.getNodeCode());
            }
            List<String> expanded = expandCollectionValue(collectionValue);
            if (!expanded.isEmpty()) {
                log.info("[Flow] collection 变量展开: nodeCode={} var={} count={}",
                        node.getNodeCode(), varName, expanded.size());
                return expanded;
            }
            log.warn("[Flow] collection 变量为空: nodeCode={} var={}", node.getNodeCode(), varName);
            return Collections.emptyList();
        }

        String perm = node.getPermissionFlag();
        if (!StringUtils.hasText(perm)) {
            return Collections.emptyList();
        }
        String resolved = variableStrategy.resolveAssignee(perm, variables);
        if (resolved == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String token : resolved.split(",")) {
            String t = token.trim();
            if (t.isEmpty()) continue;
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
            if (t.startsWith("user:")) {
                String uid = t.substring(5).trim();
                if (!uid.isEmpty() && seen.add(uid)) {
                    result.add(uid);
                }
                continue;
            }
            if (t.startsWith("multi_leader:")) {
                String levelStr = t.substring("multi_leader:".length()).trim();
                int levels = 1;
                try {
                    levels = Integer.parseInt(levelStr);
                } catch (NumberFormatException ignored) {
                    // use default
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
     * 从流程变量中解析发起人 ID
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
        // 多人取首段
        String firstResolved = resolved.split(",")[0].trim();
        task.setAssigneeType(FlowAssigneeType.USER.name());
        task.setAssigneeId(firstResolved);
        task.setAssigneeName("USER:" + firstResolved);
    }

    /**
     * P1-4: 执行 SERVICE 服务节点（HTTP/SCRIPT/AUTO_PASS 自动执行）
     *
     * <p>创建 COMPLETED/TIMEOUT 任务记录（仅用于审计追溯），归档，审计。
     * 成功时推进到下一节点；失败时优先触发 error boundary 事件，否则标记实例异常。
     */
    private String executeServiceNode(FlowInstanceDO instance, FlowNodeDO node,
                                      Map<String, Object> variables) {
        // 1. 执行服务节点逻辑
        FlowServiceNodeExecutor.ServiceExecutionResult result;
        try {
            result = serviceNodeExecutor.execute(node, variables);
        } catch (Exception e) {
            log.error("[Flow] 服务节点执行异常: instanceId={} node={} err={}",
                    instance.getId(), node.getNodeCode(), e.getMessage(), e);
            result = new FlowServiceNodeExecutor.ServiceExecutionResult(false,
                    "服务节点执行异常: " + e.getMessage());
        }

        // 2. 创建任务记录（用于审计追溯）
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
            archiveService.archiveToHistory(task, FlowTaskStatus.COMPLETED);
            support.audit(task, "SERVICE_EXECUTE", null, null,
                    "服务节点执行成功: " + result.message());
            log.info("[Flow] 服务节点执行成功: instanceId={} node={} msg={}",
                    instance.getId(), node.getNodeCode(), result.message());
            advanceAfterAutoPass(instance, node, variables);
        } else {
            // 3b. 失败：优先尝试触发 error boundary 接管流程
            boolean errorBoundaryTriggered = triggerErrorBoundaryIfExists(instance, node, result.message());
            task.setTaskStatus(FlowTaskStatus.TIMEOUT.name());
            if (errorBoundaryTriggered) {
                task.setComment("服务节点失败，error boundary 已触发: " + result.message());
            } else {
                task.setComment("服务节点执行失败: " + result.message());
            }
            taskMapper.insert(task);
            archiveService.archiveToHistory(task, FlowTaskStatus.TIMEOUT);
            if (errorBoundaryTriggered) {
                support.audit(task, "SERVICE_ERROR_BOUNDARY", null, null,
                        "服务节点失败，error boundary 触发: " + result.message());
                log.info("[Flow] 服务节点失败，error boundary 已触发: instanceId={} node={}",
                        instance.getId(), node.getNodeCode());
            } else {
                support.audit(task, "SERVICE_ERROR", null, null,
                        "服务节点执行失败: " + result.message());
                instanceMapper.updateStatus(instance.getId(),
                        FlowInstanceStatus.ERROR.name(),
                        node.getNodeCode(), node.getNodeName(), null, null);
                log.error("[Flow] 服务节点执行失败，实例标记为异常: instanceId={} node={} msg={}",
                        instance.getId(), node.getNodeCode(), result.message());
            }
        }
        return task.getId();
    }

    /**
     * P0-2: 触发附着在 serviceNode 上的 error boundary 事件
     */
    private boolean triggerErrorBoundaryIfExists(FlowInstanceDO instance, FlowNodeDO serviceNode,
                                                  String errorMsg) {
        if (eventSubscriptionService == null) {
            return false;
        }
        try {
            List<FlowNodeDO> allNodes = nodeMapper.selectByDefinitionId(instance.getDefinitionId());
            if (allNodes == null || allNodes.isEmpty()) {
                return false;
            }
            List<FlowNodeDO> errorBoundaries = allNodes.stream()
                    .filter(n -> {
                        if (!eventSubscriptionService.isEventCatchNode(n)) {
                            return false;
                        }
                        Map<String, Object> ext = parseExtConfig(n.getExt());
                        String attachedTo = (String) ext.get("attachedToRef");
                        String eventType = (String) ext.get("eventType");
                        return serviceNode.getNodeCode().equals(attachedTo)
                                && "ERROR".equalsIgnoreCase(eventType);
                    })
                    .toList();
            if (errorBoundaries.isEmpty()) {
                return false;
            }
            for (FlowNodeDO boundary : errorBoundaries) {
                Map<String, Object> ext = parseExtConfig(boundary.getExt());
                String errorRef = (String) ext.getOrDefault("errorRef", "SERVICE_ERROR");
                eventSubscriptionService.throwError(instance.getTenantId(),
                        instance.getId(), errorRef, errorMsg);
                log.info("[Flow] error boundary 触发: instanceId={} serviceNode={} boundary={} errorRef={}",
                        instance.getId(), serviceNode.getNodeCode(), boundary.getNodeCode(), errorRef);
            }
            return true;
        } catch (Exception e) {
            log.warn("[Flow] 触发 error boundary 失败，降级到标记实例异常: instanceId={} node={} err={}",
                    instance.getId(), serviceNode.getNodeCode(), e.getMessage());
            return false;
        }
    }

    /**
     * 解析 node.ext JSON 为 Map
     */
    private Map<String, Object> parseExtConfig(String ext) {
        if (!StringUtils.hasText(ext)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> map = JsonUtils.parseMap(ext);
            return map == null ? Collections.emptyMap() : map;
        } catch (Exception e) {
            log.warn("[Flow] 解析 node.ext JSON 失败: err={}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * P0-2: 判断 ext 配置中的布尔值是否为 true。
     *
     * @param val 配置值（Boolean / String / Number）
     * @return true 当值为 true / "true" / 1
     */
    private boolean isBooleanTrue(Object val) {
        if (val == null) {
            return false;
        }
        if (val instanceof Boolean b) {
            return b;
        }
        if (val instanceof Number n) {
            return n.intValue() != 0;
        }
        return "true".equalsIgnoreCase(String.valueOf(val).trim());
    }

    /**
     * 从 extConfig 中读取字符串配置值
     */
    private String parseLongConfig(Map<String, Object> config, String key, String defaultValue) {
        Object val = config.get(key);
        if (val == null) return defaultValue;
        if (val instanceof Number n) return String.valueOf(n.longValue());
        return String.valueOf(val);
    }

    /**
     * 解析 int 配置
     */
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
}
