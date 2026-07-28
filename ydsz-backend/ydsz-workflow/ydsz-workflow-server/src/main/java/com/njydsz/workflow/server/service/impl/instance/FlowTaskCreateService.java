package com.njydsz.workflow.server.service.impl.instance;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.collection.MapUtils;
import com.njydsz.workflow.domain.dto.FlowAssigneeDTO;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.entity.FlowDelegateAuth;
import com.njydsz.workflow.domain.entity.FlowInstance;
import com.njydsz.workflow.domain.entity.FlowNode;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.domain.entity.FlowUser;
import com.njydsz.workflow.domain.enums.FlowAssigneeType;
import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.domain.enums.FlowNodeType;
import com.njydsz.workflow.domain.enums.FlowPerformType;
import com.njydsz.workflow.domain.enums.FlowSignType;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.infra.mapper.FlowInstanceMapper;
import com.njydsz.workflow.infra.mapper.FlowNodeMapper;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.workflow.infra.mapper.FlowUserMapper;
import com.njydsz.workflow.server.engine.FlowAdvancer;

import com.njydsz.workflow.server.engine.FlowAssigneeResolver;
import com.njydsz.workflow.server.engine.FlowServiceNodeExecutor;
import com.njydsz.workflow.server.engine.FlowVariableStrategy;
import com.njydsz.workflow.server.metrics.FlowMetrics;
import com.njydsz.workflow.server.service.FlowDelegateAuthService;
import com.njydsz.workflow.server.service.FlowEventSubscriptionService;
import com.njydsz.workflow.server.service.FlowInstanceService;
import com.njydsz.workflow.server.service.FlowSlaService;
import com.njydsz.workflow.server.service.FlowTodoCountPushService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 任务创建服务（拆分自 FlowTaskCompleteServiceImpl）
 *
 * <p>工作流引擎中<b>任务创建场景最复杂</b>的服务，承担 BPMN 2.0 中几乎所有节点类型的「创建运行时任务」
 * 职责。是从原 {@code FlowTaskCompleteServiceImpl}（单体实现）拆分的产物，
 * 是大厂 B 端工作流「灵活节点类型 + 智能审批人解析」的关键实现层。
 *
 * <p><b>支持的任务创建场景：</b>
 * <ul>
 *   <li><b>普通审批节点（{@code APPROVAL}）</b>：{@link #createTask} 走标准审批人解析路径</li>
 *   <li><b>SERVICE 服务节点（{@code SERVICE}）</b>：{@link #executeServiceNode} —
 *       HTTP / SCRIPT / AUTO_PASS 自动执行，无需人工介入</li>
 *   <li><b>FOREACH 循环节点（{@code FOREACH}）</b>：{@link #createForeachTasks} —
 *       对集合中每个元素创建独立 task（每个元素独立审批）</li>
 *   <li><b>LEVEL_APPROVAL 逐级审批节点（{@code LEVEL_APPROVAL}）</b>：{@link #createLevelApprovalTask} —
 *       动态展开多级上级（直属 → 二级 → 三级），依次审批</li>
 *   <li><b>审批人为空兜底</b>：{@link #handleEmptyAssignee} —
 *       AUTO_PASS / TRANSFER_ADMIN / ASSIGN_SPECIFIED / FALLBACK 四种策略</li>
 *   <li><b>跨节点办理人去重（P1-5）</b>：{@link #applyCrossNodeDedup} —
 *       过滤已在当前实例审批过的用户，对标钉钉「同人不重复审批」</li>
 *   <li><b>自动审批节点（P2-4 / GAP-14）</b>：{@link #tryAutoApprove} —
 *       配置化规则引擎（{@code INITIATOR_IS_APPROVER / AMOUNT_BELOW / EXPR / ALWAYS}）</li>
 *   <li><b>长期授权委派改写（P1-4）</b>：{@link #applyDelegateRedirect} —
 *       链式解析 A→B→C 委派链路，最终将任务分配给链路末端的代理人</li>
 * </ul>
 *
 * <p><b>被调用方（依赖注入关系）：</b>
 * <ul>
 *   <li>{@link FlowTaskPassService} / {@link FlowTaskRejectService} —
 *       自动审批执行后调用 pass / reject 子服务</li>
 *   <li>{@link FlowTaskOperateService} — 创建任务后应用转办 / 委派 / 加签等操作</li>
 *   <li>{@link FlowInstanceService} — 创建子任务时调用本服务</li>
 *   <li>{@link FlowAdvancer} — 流程推进引擎，AUTO_PASS 递归推进到下一节点</li>
 *   <li>{@link FlowSlaService} — 任务创建时应用 SLA 配置</li>
 *   <li>{@link FlowDelegateAuthService} — 长期授权委派查询</li>
 *   <li>{@link FlowTodoCountPushService} — WebSocket 待办数推送</li>
 * </ul>
 *
 * <p><b>事务边界：</b>所有公共方法开启 {@code @Transactional(rollbackFor = Exception.class)}，
 * 「参数解析 + 任务构建 + 业务字段设置 + 事件发布」原子性。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>递归保护</b>：AUTO_PASS 通过 {@link ThreadLocal} 维护递归深度，超过
 *       {@link #MAX_AUTO_PASS_DEPTH}（20）立即抛异常，防止流程定义环路导致栈溢出</li>
 *   <li><b>循环依赖处理</b>：与 {@link FlowTaskPassService} / {@link FlowTaskRejectService} /
 *       {@link FlowSlaService} / {@link FlowTodoCountPushService} 等服务存在循环依赖，
 *       通过 {@code @Lazy} 注解打破</li>
 *   <li><b>空安全</b>：所有集合 / 字符串参数均做空检查，避免 NPE</li>
 *   <li><b>指标埋点</b>：通过 {@link FlowMetrics} 暴露任务创建数等 Prometheus 指标</li>
 *   <li><b>事件发布</b>：任务创建后通过 {@link FlowTaskSupport#fireEvent} 触发监听器，
 *       通过 {@link FlowTaskSupport#publishWorkflowEvent} 发布 Spring 事件</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 场景：流程推进到「财务审批」节点
 * String taskId = flowTaskCreateService.createTask(
 *     instanceId,                  // 流程实例 ID
 *     financeApprovalNode,         // 节点配置
 *     flowVariables                // 流程变量
 * );
 * // 返回新创建的任务 ID
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowTaskServiceImpl 任务门面（拆分入口）
 * @see FlowRunTask 运行时任务实体
 * @see FlowNode 流程节点实体
 * @see FlowAdvancer 流程推进引擎
 * @see FlowSlaService SLA 服务
 * @see FlowDelegateAuthService 委派代理服务
 * @see FlowAssigneeResolver 审批人解析器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskCreateService {

    /** P0-1: 审批人为空统一默认 FALLBACK（最保守：转交管理员人工处理） */
    private static final String DEFAULT_EMPTY_STRATEGY = "FALLBACK";

    /** AUTO_PASS 递归深度保护（防止流程定义环路导致栈溢出） */
    private static final ThreadLocal<Integer> AUTO_PASS_DEPTH = ThreadLocal.withInitial(() -> 0);
    /** AUTO_PASS 最大递归深度，超过则抛异常 */
    private static final int MAX_AUTO_PASS_DEPTH = 20;

    /** 运行时任务 Mapper，创建/更新待办任务 */
    private final FlowRunTaskMapper taskMapper;
    /** 用户 Mapper，查询审批人/候选人用户信息 */
    private final FlowUserMapper userMapper;
    /** 流程实例 Mapper，查询/更新实例状态和变量 */
    private final FlowInstanceMapper instanceMapper;
    /** 流程节点 Mapper，查询节点配置（审批人/权限/SLA 等） */
    private final FlowNodeMapper nodeMapper;
    /** 流程推进引擎，AUTO_PASS 递归推进到下一节点 */
    private final FlowAdvancer advancer;
    /** 变量策略，解析节点 ext JSON 中的条件表达式 */
    private final FlowVariableStrategy variableStrategy;
    /** 审批人解析器，从节点配置解析实际审批人/候选人列表 */
    private final FlowAssigneeResolver assigneeResolver;
    /** 委派授权服务，查询长期授权委派改写审批人 */
    private final FlowDelegateAuthService delegateAuthService;
    /** 跨子 Service 共享的任务校验/审计/事件辅助 */
    private final FlowTaskSupport support;
    /** 任务归档服务，完成任务后写入历史任务表 */
    private final FlowTaskArchiveService archiveService;
    /** 使用 @Lazy 避免循环依赖：FlowTaskPassService → FlowTaskCreateService */
    @Lazy
    private final FlowTaskPassService passService;
    /** P0-4: 自动审批 REJECT 动作使用 */
    @Lazy
    private final FlowTaskRejectService rejectService;
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
    public String createTask(String instanceId, FlowNode node, Map<String, Object> variables) {
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
    public String createTask(String instanceId, FlowNode node, Map<String, Object> variables,
                             List<String> explicitAssignees) {
        FlowInstance instance = lookupInstance(instanceId);

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

        FlowRunTask task = buildBaseTask(instance, node, performType, userIds.size());

        if (userIds.isEmpty()) {
            // 跨节点去重后候选人为空 — 自动跳过该节点
            if (autoDedup) {
                return handleAutoDedupSkip(task, instance, node, variables);
            }
            // P0-1: 审批人为空兜底处理
            return handleEmptyAssignee(task, instance, node, variables);
        }

        // 正常路径：设置首个办理人 + 写入 ydsz_flow_user
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
        // 写入 ydsz_flow_user
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

    private FlowInstance lookupInstance(String instanceId) {
        FlowInstance instance = instanceService.getById(instanceId);
        if (instance == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "error.workflow.msg_fc4b1c16", instanceId);
        }
        return instance;
    }

    private boolean isNodeType(FlowNode node, FlowNodeType type) {
        return node != null && node.getNodeType() != null && node.getNodeType() == type.getCode();
    }

    /**
     * 构建基础任务对象（设置通用字段）。
     */
    private FlowRunTask buildBaseTask(FlowInstance instance, FlowNode node,
                                        FlowPerformType performType, int approveCount) {
        FlowRunTask task = new FlowRunTask();
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
    private String handleAutoDedupSkip(FlowRunTask task, FlowInstance instance, FlowNode node,
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
    private String handleEmptyAssignee(FlowRunTask task, FlowInstance instance, FlowNode node,
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
     * P2-4 (GAP-14) / P0-4: 自动审批节点（配置化规则引擎）
     *
     * <p>P0-4 增强：支持多规则配置（rules 数组），每条规则可指定 type + action。
     *
     * <p>ext JSON 配置示例：
     * <pre>
     * {
     *   "autoApprove": {
     *     "enabled": true,
     *     "rules": [
     *       {"type": "INITIATOR_IS_APPROVER", "action": "PASS"},
     *       {"type": "AMOUNT_BELOW", "threshold": 1000, "variable": "amount", "action": "PASS"},
     *       {"type": "EXPR", "expr": "deptType == 'engineering' && urgency == 'low'", "action": "PASS"},
     *       {"type": "AMOUNT_ABOVE", "threshold": 100000, "variable": "amount", "action": "REJECT"}
     *     ]
     *   }
     * }
     * </pre>
     *
     * <p>兼容旧配置：enabled + whenInitiatorIsApprover + expr 单条规则格式。
     */
    private void tryAutoApprove(FlowInstance instance, FlowNode node,
                                FlowRunTask task, Map<String, Object> variables) {
        if (node.getExt() == null || node.getExt().isBlank()) {
            return;
        }
        Map<String, Object> extConfig;
        try {
            extConfig = YdszJson.parseMap(node.getExt());
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
        Map<String, Object> cfg = MapUtils.toStringObjectMap(autoApprove);
        Boolean enabled = (Boolean) cfg.get("enabled");
        if (enabled == null || !enabled) {
            return;
        }
        // 仅单人 OR 模式自动通过
        if (!FlowPerformType.OR.name().equals(task.getPerformType())) {
            return;
        }

        // P0-4: 构建评估环境
        Map<String, Object> env = new HashMap<>();
        if (variables != null) {
            env.putAll(variables);
        }
        env.put("_initiatorId", instance.getInitiatorId());
        env.put("_assigneeId", task.getAssigneeId());
        env.put("_nodeCode", node.getNodeCode());

        // P0-4: 优先使用 rules 数组（新配置）
        Object rulesObj = cfg.get("rules");
        if (rulesObj instanceof List<?> rulesList && !rulesList.isEmpty()) {
            for (Object ruleObj : rulesList) {
                if (!(ruleObj instanceof Map<?, ?> rule)) {
                    continue;
                }
                Map<String, Object> ruleCfg = MapUtils.toStringObjectMap(rule);
                String action = evaluateAutoApproveRule(ruleCfg, instance, task, env);
                if (action != null) {
                    executeAutoAction(action, instance, node, task, variables, ruleCfg);
                    return; // 命中第一条规则即执行
                }
            }
            return; // 规则数组无命中
        }

        // 兼容旧配置：单条规则
        boolean matched = false;
        String action = "PASS";

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
                    Object result = serviceNodeExecutor.evalExpr(expr, env);
                    matched = Boolean.TRUE.equals(result);
                } catch (Exception e) {
                    log.warn("[Flow] 自动审批表达式求值失败 node={} expr={} err={}",
                            node.getNodeCode(), exprObj, e.getMessage());
                }
            }
        }
        if (matched) {
            executeAutoAction(action, instance, node, task, variables, null);
        }
    }

    /**
     * P0-4: 评估单条自动审批规则
     *
     * @return "PASS" / "REJECT" / null（未命中）
     */
    private String evaluateAutoApproveRule(Map<String, Object> rule, FlowInstance instance,
                                            FlowRunTask task, Map<String, Object> env) {
        String type = String.valueOf(rule.getOrDefault("type", "")).toUpperCase();
        String action = String.valueOf(rule.getOrDefault("action", "PASS")).toUpperCase();
        boolean matched = false;

        switch (type) {
            case "INITIATOR_IS_APPROVER" -> {
                if (instance.getInitiatorId() != null) {
                    String initiator = String.valueOf(instance.getInitiatorId());
                    matched = initiator.equals(task.getAssigneeId())
                            || (task.getAssigneeName() != null
                            && task.getAssigneeName().contains(initiator));
                }
            }
            case "EXPR" -> {
                Object exprObj = rule.get("expr");
                if (exprObj instanceof String expr && !expr.isBlank()) {
                    try {
                        Object result = serviceNodeExecutor.evalExpr(expr, env);
                        matched = Boolean.TRUE.equals(result);
                    } catch (Exception e) {
                        log.warn("[Flow] P0-4 自动审批规则表达式求值失败: type={} expr={} err={}",
                                type, exprObj, e.getMessage());
                    }
                }
            }
            case "AMOUNT_BELOW" -> {
                String varName = String.valueOf(rule.getOrDefault("variable", "amount"));
                Object thresholdObj = rule.get("threshold");
                Object val = env.get(varName);
                if (thresholdObj != null && val instanceof Number n) {
                    double threshold = ((Number) thresholdObj).doubleValue();
                    matched = n.doubleValue() < threshold;
                }
            }
            case "AMOUNT_ABOVE" -> {
                String varName = String.valueOf(rule.getOrDefault("variable", "amount"));
                Object thresholdObj = rule.get("threshold");
                Object val = env.get(varName);
                if (thresholdObj != null && val instanceof Number n) {
                    double threshold = ((Number) thresholdObj).doubleValue();
                    matched = n.doubleValue() > threshold;
                }
            }
            case "ALWAYS" -> matched = true;
            default -> {
                log.debug("[Flow] P0-4 未知自动审批规则类型: type={}", type);
            }
        }

        return matched ? action : null;
    }

    /**
     * P0-4: 执行自动审批动作（PASS / REJECT）
     */
    private void executeAutoAction(String action, FlowInstance instance, FlowNode node,
                                    FlowRunTask task, Map<String, Object> variables,
                                    Map<String, Object> ruleCfg) {
        FlowTaskOperateDTO autoDto = new FlowTaskOperateDTO();
        autoDto.setTaskId(task.getId());
        autoDto.setUserId("0");
        autoDto.setUserName("SYSTEM_AUTO_APPROVE");
        String ruleDesc = ruleCfg != null
                ? String.valueOf(ruleCfg.getOrDefault("type", "UNKNOWN")) : "LEGACY";
        if ("REJECT".equals(action)) {
            autoDto.setComment("P0-4 自动审批规则[" + ruleDesc + "]命中，自动驳回");
            try {
                // 调用 rejectService 驳回
                rejectService.reject(autoDto);
                log.info("[Flow] P0-4 自动审批规则驳回: instanceId={} node={} taskId={} rule={}",
                        instance.getId(), node.getNodeCode(), task.getId(), ruleDesc);
            } catch (Exception e) {
                log.warn("[Flow] P0-4 自动审批驳回失败（降级为人工）: instanceId={} node={} err={}",
                        instance.getId(), node.getNodeCode(), e.getMessage());
            }
        } else {
            autoDto.setComment("P0-4 自动审批规则[" + ruleDesc + "]命中，自动通过");
            autoDto.setVariables(variables);
            try {
                passService.pass(autoDto);
                log.info("[Flow] P0-4 自动审批规则通过: instanceId={} node={} taskId={} rule={}",
                        instance.getId(), node.getNodeCode(), task.getId(), ruleDesc);
            } catch (Exception e) {
                log.warn("[Flow] P0-4 自动审批通过失败（降级为人工）: instanceId={} node={} err={}",
                        instance.getId(), node.getNodeCode(), e.getMessage());
            }
        }
    }

    /**
     * 写入 ydsz_flow_user 记录
     */
    private void insertFlowUser(FlowRunTask task, FlowInstance instance, FlowNode node,
                                String uid, Map<String, Integer> userWeights) {
        FlowUser fu = new FlowUser();
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
    private String createLevelApprovalTask(FlowInstance instance, FlowNode node,
                                           Map<String, Object> variables, List<String> approvers) {
        FlowRunTask task = buildBaseTask(instance, node, FlowPerformType.SEQUENTIAL, approvers.size());
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
    private String createTaskWithEmptyAssignee(FlowInstance instance, FlowNode node,
                                                Map<String, Object> variables) {
        Map<String, Object> extConfig = parseExtConfig(node.getExt());
        String emptyStrategy = (String) extConfig.getOrDefault("emptyStrategy", DEFAULT_EMPTY_STRATEGY);
        FlowRunTask task = buildBaseTask(instance, node, FlowPerformType.OR, 1);

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
    private String createForeachTasks(FlowInstance instance, FlowNode node,
                                      Map<String, Object> variables, List<String> explicitAssignees) {
        List<String> elements = (explicitAssignees != null && !explicitAssignees.isEmpty())
                ? new ArrayList<>(explicitAssignees)
                : expandAssignees(node, variables);

        if (elements.isEmpty()) {
            Map<String, Object> extConfig = parseExtConfig(node.getExt());
            String emptyStrategy = (String) extConfig.getOrDefault("emptyStrategy", DEFAULT_EMPTY_STRATEGY);
            if ("AUTO_PASS".equals(emptyStrategy)) {
                FlowRunTask autoTask = buildForeachTask(instance, node, "0", "SYSTEM_AUTO_PASS", "0");
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
            FlowRunTask task = buildForeachTask(instance, node, element, "USER:" + element, element);
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
    private FlowRunTask buildForeachTask(FlowInstance instance, FlowNode node,
                                          String assigneeId, String assigneeName, String iterVar) {
        FlowRunTask task = new FlowRunTask();
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
     * P1-4/P1-7: 长期授权委派改写（支持链式解析）
     *
     * <p>P1-7 增强：使用 {@code resolveDelegateChain} 递归解析 A→B→C 链式委派，
     * 最终将任务分配给链路末端的代理人。
     */
    private void applyDelegateRedirect(FlowRunTask task, FlowInstance instance, FlowNode node) {
        try {
            if (delegateAuthService == null) {
                return;
            }
            String currentAssigneeId = task.getAssigneeId();
            if (!StringUtils.hasText(currentAssigneeId)) {
                return;
            }
            String currentUserId = currentAssigneeId.trim();
            // P1-7: 链式解析最终代理人
            String finalDelegateId = delegateAuthService.resolveDelegateChain(
                    instance.getTenantId(), currentUserId,
                    instance.getFlowCode(), node.getNodeCode());
            if (finalDelegateId == null || finalDelegateId.equals(currentUserId)) {
                // 无委派规则，或最终代理人就是原办理人
                return;
            }
            // 仍需匹配首条授权规则用于审计记录
            FlowDelegateAuth matched = delegateAuthService.matchAuth(
                    instance.getTenantId(), currentUserId,
                    instance.getFlowCode(), node.getNodeCode());
            task.setAssignorId(currentUserId);
            task.setAssignorName(matched != null ? matched.getOwnerUserName() : null);
            task.setAssigneeId(finalDelegateId);
            // 最终代理人姓名：优先从链路末端匹配记录获取
            task.setAssigneeName(matched != null ? matched.getDelegateUserName() : finalDelegateId);
            taskMapper.updateById(task);
            String authId = matched != null ? matched.getId() : "CHAIN_RESOLVED";
            String scopeType = matched != null ? matched.getScopeType() : "CHAIN";
            support.audit(task, "DELEGATE_AUTH_APPLIED", finalDelegateId,
                    currentUserId,
                    "长期授权委派生效(链式): " + authId + " (" + scopeType + ") → " + finalDelegateId);
            log.info("[Flow] 长期授权委派改写(链式): taskId={} owner={} → finalDelegate={} authId={} scope={}",
                    task.getId(), currentUserId, finalDelegateId, authId, scopeType);
        } catch (Exception e) {
            log.error("[Flow] 长期授权委派改写异常: taskId={} err={}",
                    task == null ? "null" : task.getId(), e.getMessage(), e);
        }
    }

    /**
     * AUTO_PASS 后推进到下一节点（含递归深度保护）
     */
    private void advanceAfterAutoPass(FlowInstance instance, FlowNode node,
                                       Map<String, Object> variables) {
        int depth = AUTO_PASS_DEPTH.get();
        if (depth >= MAX_AUTO_PASS_DEPTH) {
            log.warn("[Flow] AUTO_PASS 递归深度超限: depth={} instanceId={}", depth, instance.getId());
            throw new SysException(BaseResultCode.INTERNAL_ERROR, "error.workflow.msg_fcd55e62");
        }
        AUTO_PASS_DEPTH.set(depth + 1);
        try {
            List<FlowNode> nextNodes = advancer.advance(instance, node.getNodeCode(),
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
    private void updateInstanceNode(FlowInstance instance, List<FlowNode> nextNodes) {
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
    private void applyPriority(FlowRunTask task, FlowNode node) {
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
    private void applyVoteConfig(FlowRunTask task, FlowNode node) {
        Map<String, Object> ext = parseExtConfig(node.getExt());
        Object rate = ext.get("votePassRate");
        if (rate instanceof Number n) {
            task.setVotePassRate(BigDecimal.valueOf(n.doubleValue()));
        } else if (rate instanceof String s && !s.isBlank()) {
            try {
                task.setVotePassRate(new BigDecimal(s.trim()));
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
    private List<String> expandLevelApprovers(FlowInstance instance, FlowNode node,
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
    private String tryAutoDedup(FlowRunTask task, FlowInstance instance, FlowNode node,
                              Map<String, Object> variables, String currentAssigneeId) {
        try {
            LambdaQueryWrapper<FlowRunTask> qw = new LambdaQueryWrapper<>();
            qw.eq(FlowRunTask::getInstanceId, instance.getId())
                    .eq(FlowRunTask::getTaskStatus, FlowTaskStatus.COMPLETED.name())
                    .orderByDesc(FlowRunTask::getId)
                    .last("LIMIT 1");
            List<FlowRunTask> prevTasks = taskMapper.selectList(qw);
            if (prevTasks.isEmpty()) {
                return null;
            }
            FlowRunTask prevTask = prevTasks.get(0);
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
    private List<String> applyCrossNodeDedup(List<String> userIds, String instanceId, FlowNode node) {
        try {
            // 查询实例下已审批过的人员（COMPLETED 状态）
            LambdaQueryWrapper<FlowRunTask> qw = new LambdaQueryWrapper<>();
            qw.eq(FlowRunTask::getInstanceId, instanceId)
                    .eq(FlowRunTask::getTaskStatus, FlowTaskStatus.COMPLETED.name());
            List<FlowRunTask> done = taskMapper.selectList(qw);
            Set<String> excluded = new HashSet<>();
            for (FlowRunTask t : done) {
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
    private boolean isAutoDedupEnabled(FlowNode node) {
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
    private FlowPerformType resolvePerformType(FlowNode node) {
        if (node.getExt() != null) {
            try {
                Map<?, ?> ext = YdszJson.parseMap(node.getExt());
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
    private List<String> expandAssignees(FlowNode node, Map<String, Object> variables) {
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

    private void resolveAssignee(FlowRunTask task, FlowNode node,
                                  Map<String, Object> variables,
                                  FlowAssigneeDTO explicit,
                                  FlowInstance instance) {
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
    private String executeServiceNode(FlowInstance instance, FlowNode node,
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
        FlowRunTask task = new FlowRunTask();
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
    private boolean triggerErrorBoundaryIfExists(FlowInstance instance, FlowNode serviceNode,
                                                  String errorMsg) {
        if (eventSubscriptionService == null) {
            return false;
        }
        try {
            List<FlowNode> allNodes = nodeMapper.selectByDefinitionId(instance.getDefinitionId());
            if (allNodes == null || allNodes.isEmpty()) {
                return false;
            }
            List<FlowNode> errorBoundaries = allNodes.stream()
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
            for (FlowNode boundary : errorBoundaries) {
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
            Map<String, Object> map = YdszJson.parseMap(ext);
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
