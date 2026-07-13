package com.njydsz.pmis.workflow.server.service.impl.instance;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.lock.annotation.YdszDistributedLock;
import com.njydsz.pmis.common.auth.annotation.DataScope;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.core.response.PageResponse;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.security.DataScopeHelper;
import com.njydsz.pmis.common.security.LoginUser;
import com.njydsz.pmis.common.auth.context.AuthContext;
import com.njydsz.pmis.common.util.json.JsonUtils;
import com.njydsz.pmis.workflow.domain.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.domain.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.server.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.server.engine.FlowEventContext;
import com.njydsz.pmis.workflow.server.engine.FlowEventListener;
import com.njydsz.pmis.workflow.server.engine.FlowVariableStrategy;
import com.njydsz.pmis.workflow.server.engine.FlowWorkflowEvent;
import com.njydsz.pmis.workflow.domain.entity.FlowAuditLogDO;
import com.njydsz.pmis.workflow.domain.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.domain.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.domain.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.domain.entity.FlowSkipDO;
import com.njydsz.pmis.workflow.domain.entity.FlowRunTaskDO;
import com.njydsz.pmis.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.domain.enums.FlowNodeType;
import com.njydsz.pmis.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.pmis.workflow.infra.mapper.FlowAuditLogMapper;
import com.njydsz.pmis.workflow.infra.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.infra.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.infra.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.infra.mapper.FlowSkipMapper;
import com.njydsz.pmis.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.pmis.workflow.server.metrics.FlowMetrics;
import com.njydsz.pmis.workflow.server.service.FlowAutoTriggerService;
import com.njydsz.pmis.workflow.server.service.FlowCanaryService;
import com.njydsz.pmis.workflow.server.service.FlowCcService;
import com.njydsz.pmis.workflow.server.service.FlowDefinitionService;
import com.njydsz.pmis.workflow.server.service.FlowEventSubscriptionService;
import com.njydsz.pmis.workflow.server.service.FlowInstanceService;
import com.njydsz.pmis.workflow.server.service.FlowSubProcessService;
import com.njydsz.pmis.workflow.server.service.FlowTaskService;
import com.njydsz.pmis.workflow.server.service.FlowThirdPartySyncService;
import com.njydsz.pmis.workflow.server.service.FlowTimerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

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

    /** 流程实例 Mapper，负责 pmis_flow_instance 表的增删改查 */
    private final FlowInstanceMapper instanceMapper;
    /** 流程定义服务，启动实例时解析流程定义节点和跳转 */
    private final FlowDefinitionService definitionService;
    /** P3-1: 灰度发布服务（启动流程时按 canary 配置切流） */
    private final FlowCanaryService canaryService;
    /** 流程推进引擎，负责节点推进/跳转/网关条件求值 */
    private final FlowAdvancer advancer;
    /** 流程任务服务，创建/推进/终止任务 */
    private final FlowTaskService taskService;
    /** 运行时任务 Mapper，查询/更新当前待办任务 */
    private final FlowRunTaskMapper taskMapper;
    /** GAP-V2-08: 流程节点 Mapper（模拟运行时查询节点） */
    private final FlowNodeMapper nodeMapper;
    /** GAP-V2-08: 流程跳转 Mapper（模拟运行时查询跳转） */
    private final FlowSkipMapper skipMapper;
    /** GAP-V2-08: 条件求值策略（模拟运行时复用 SpEL 条件解析） */
    private final FlowVariableStrategy variableStrategy;
    /** 事件监听器列表（Spring 自动注入所有实现），处理流程生命周期事件 */
    private final List<FlowEventListener> eventListeners;
    /** P2-3: Prometheus 指标收集（可能为 null：测试环境） */
    private final FlowMetrics flowMetrics;
    /** P2-35: Spring 事件发布器，用于异步事件机制（测试环境可能为 null） */
    private final ApplicationEventPublisher eventPublisher;
    /** P1-3: 子流程服务（处理 callActivity 子流程启动） */
    private final FlowSubProcessService subProcessService;
    /** GAP-P1: 抄送服务（CC 节点处理） */
    private final FlowCcService ccService;
    /** 流程自动触发服务（实例完成时检查是否需要自动发起下一流程） */
    private final FlowAutoTriggerService autoTriggerService;
    /**
     * P0-1: BPMN 事件订阅服务 — 流程推进到事件捕获节点时创建订阅
     *
     * <p>使用 @Lazy 避免循环依赖：FlowEventSubscriptionServiceImpl → FlowAdvancer → FlowInstanceService → FlowEventSubscriptionService
     */
    @Lazy
    private final FlowEventSubscriptionService eventSubscriptionService;
    /** P2-2: 审计日志 Mapper（重审时写入 RESUBMIT 轨迹） */
    private final FlowAuditLogMapper auditLogMapper;
    /**
     * P1-1: 历史任务 Mapper（查询可撤回的历史节点列表）
     */
    private final FlowHisTaskMapper hisTaskMapper;
    /** P2-6: 三方审批双向同步服务（终止/撤回时主动同步回三方） */
    private final FlowThirdPartySyncService thirdPartySyncService;
    /**
     * P0-2: 定时器服务 — boundaryEvent 含 timer 配置时注册边界定时器自动触发
     *
     * <p>使用 @Lazy 避免循环依赖：FlowTimerServiceImpl → FlowAdvancer → FlowInstanceService → FlowTimerService
     */
    @Lazy
    private final FlowTimerService timerService;

    /**
     * P2-6: 自注入代理引用，使 {@link #batchStartInstances} 内部调用 {@link #start}
     * 时能正确触发 Spring 事务代理（避免 self-invocation 导致事务失效）。
     * 使用 {@code @Lazy} 打破启动期循环依赖。
     */
    @Lazy
    private final FlowInstanceServiceImpl self;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String start(FlowStartProcessDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getFlowCode())
                || !StringUtils.hasText(dto.getBusinessType())
                || !StringUtils.hasText(dto.getBusinessId())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_208e3c66");
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
        // P2-16: 多租户上下文 - DTO 显式传入优先，否则从 SecurityContext 获取
        String tenantId = dto.getTenantId() != null
                ? dto.getTenantId()
                : AuthContext.getTenantIdOrDefault("1");
        // P3-1: 灰度发布 - 启动时按 canary 配置切流到稳定版或灰度版
        FlowDefinitionDO def = canaryService.resolveEffectiveDefinition(
                dto.getFlowCode(),
                StringUtils.hasText(dto.getVersion()) ? dto.getVersion() : "1.0",
                tenantId,
                dto.getInitiatorId());
        if (def == null) {
            throw new SysException(StandardResultCode.NOT_FOUND,
                    "error.workflow.msg_add8d012", dto.getFlowCode());
        }

        // 2. 创建实例
        FlowInstanceDO instance = new FlowInstanceDO();
        instance.setFlowCode(def.getFlowCode());
        instance.setFlowName(def.getFlowName());
        instance.setDefinitionId(def.getId());
        instance.setFlowVersion(def.getFlowVersion());
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
        // GAP-P2: 发起人自选审批人 — 将 nodeAssignees 合并到 variables 中
        Map<String, Object> mergedVars = dto.getVariables() == null
                ? new HashMap<>() : new HashMap<>(dto.getVariables());
        if (dto.getNodeAssignees() != null && !dto.getNodeAssignees().isEmpty()) {
            for (Map.Entry<String, List<Long>> entry : dto.getNodeAssignees().entrySet()) {
                mergedVars.put("_selfSelect_" + entry.getKey(), entry.getValue());
            }
        }
        instance.setVariable(mergedVars.isEmpty() ? null : JSON.toJSONString(mergedVars));
        instance.setTenantId(tenantId);
        instance.setProviderTraceId(dto.getProviderTraceId());
        // P1-3: 子流程场景：填充父实例信息
        instance.setParentInstanceId(dto.getParentInstanceId());
        instance.setParentNodeCode(dto.getParentNodeCode());
        instanceMapper.insert(instance);
        String instanceId = instance.getId();

        // P2-38: 发起人自选审批人 — _selfSelect_<nodeCode> 变量已合并到 mergedVars
        for (String key : mergedVars.keySet()) {
            if (key != null && key.startsWith("_selfSelect_")) {
                log.info("[Flow] 发起人自选审批人变量: instanceId={} key={} value={}",
                        instanceId, key, mergedVars.get(key));
            }
        }

        // P0-2: 触发 onInstanceStart 事件
        fireInstanceStart(instanceId, mergedVars);

        // P2-3: Prometheus 指标 — 实例创建
        if (flowMetrics != null) {
            flowMetrics.incInstanceCreated(def.getFlowCode());
        }

        // 3. 引擎推进：开始节点 → 下一节点
        try {
            advancer.start(instanceId);
        } catch (Exception e) {
            fireError(instanceId, e);
            if (flowMetrics != null) {
                flowMetrics.incStartError(def.getFlowCode(), e.getClass().getSimpleName());
            }
            throw e;
        }
        log.info("[Flow] 启动流程: code={} bizId={} instanceId={}",
                dto.getFlowCode(), dto.getBusinessId(), instanceId);
        return instanceId;
    }

    @Override
    @Transactional(readOnly = true)
    public FlowInstanceDO getById(String id) {
        return instanceMapper.selectById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public FlowInstanceDO getByBusiness(String businessType, String businessId) {
        return instanceMapper.selectByBusiness(businessType, businessId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
    public void terminate(String instanceId, String reason) {
        FlowInstanceDO instance = getByIdOrThrow(instanceId);
        if (FlowInstanceStatus.valueOf(instance.getFlowStatus()).isFinished()) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_2246960b");
        }
        LocalDateTime now = LocalDateTime.now();
        Long durationMs = instance.getStartAt() == null
                ? null
                : Duration.between(instance.getStartAt(), now).toMillis();
        // P2-18: reason 持久化到 variable JSON
        String var = instance.getVariable();
        if (StringUtils.hasText(reason)) {
            try {
                Map<String, Object> m = parseVariables(var);
                m.put("_terminateReason", reason);
                var = JSON.toJSONString(m);
                // 修复 P2-18: 写回 DB（之前仅改局部变量未持久化）
                instanceMapper.updateVariable(instanceId, var);
            } catch (Exception e) {
                log.warn("[Flow] terminate reason 持久化失败: instanceId={} reason={}",
                        instanceId, e.getMessage());
            }
        }
        instanceMapper.updateStatus(instanceId, FlowInstanceStatus.TERMINATED.name(),
                null, null, now, durationMs);
        // 取消所有 PENDING 任务
        taskService.cancelByInstance(instanceId, FlowTaskStatus.CANCELLED.name());
        // P0-1: 取消所有 WAITING 事件订阅
        eventSubscriptionService.cancelByInstance(instanceId, "INSTANCE_TERMINATED: " + reason);
        log.info("[Flow] 终止流程: instanceId={} reason={}", instanceId, reason);
        // P2-3: Prometheus 指标 — 实例终止 + 耗时
        if (flowMetrics != null) {
            flowMetrics.incInstanceFinished(instance.getFlowCode(), "TERMINATED");
            flowMetrics.recordInstanceDuration(instance, "TERMINATED");
        }
        // P2-34: 触发 onInstanceTerminated 事件
        fireEvent(l -> l.onInstanceTerminated(instanceId, reason));
        // P2-37: 同时调用携带上下文的重载版本
        FlowEventContext ctx = buildContext(instanceId, null, null, "TERMINATE", instance);
        fireEvent(l -> l.onInstanceTerminated(instanceId, reason, ctx));
        // P2-35: 发布 Spring 异步事件
        publishWorkflowEvent("INSTANCE_TERMINATED", instanceId, null);
        // P2-6: 双向同步 — 本地→三方取消审批单
        try {
            thirdPartySyncService.syncBackOnTerminate(instanceId, reason);
        } catch (Exception e) {
            log.warn("[Flow] 三方审批同步回退失败（不影响本地终止）: instanceId={} err={}",
                    instanceId, e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
    public void suspend(String instanceId) {
        FlowInstanceDO instance = getByIdOrThrow(instanceId);
        if (!FlowInstanceStatus.RUNNING.name().equals(instance.getFlowStatus())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_543fc92f");
        }
        instanceMapper.updateStatus(instanceId, FlowInstanceStatus.SUSPENDED.name(),
                instance.getCurrentNodeCode(), instance.getCurrentNodeName(),
                null, null);
        // P2-18: 冻结 PENDING/CLAIMED 任务为 FROZEN，禁止办理
        taskMapper.freezeByInstance(instanceId);
        log.info("[Flow] 挂起流程: instanceId={}", instanceId);
        // P2-3: Prometheus 指标
        if (flowMetrics != null) {
            flowMetrics.incInstanceSuspended(instance.getFlowCode());
        }
        // P2-34: 触发 onInstanceSuspended 事件
        fireEvent(l -> l.onInstanceSuspended(instanceId));
        // P2-35: 发布 Spring 异步事件
        publishWorkflowEvent("INSTANCE_SUSPENDED", instanceId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
    public void activate(String instanceId) {
        FlowInstanceDO instance = getByIdOrThrow(instanceId);
        if (!FlowInstanceStatus.SUSPENDED.name().equals(instance.getFlowStatus())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_ab594c75");
        }
        instanceMapper.updateStatus(instanceId, FlowInstanceStatus.RUNNING.name(),
                instance.getCurrentNodeCode(), instance.getCurrentNodeName(),
                null, null);
        // P2-18: 解冻 FROZEN 任务，回到 PENDING 可办理
        taskMapper.unfreezeByInstance(instanceId);
        log.info("[Flow] 激活流程: instanceId={}", instanceId);
        // P2-3: Prometheus 指标
        if (flowMetrics != null) {
            flowMetrics.incInstanceActivated(instance.getFlowCode());
        }
        // P2-34: 触发 onInstanceActivated 事件
        fireEvent(l -> l.onInstanceActivated(instanceId));
        // P2-35: 发布 Spring 异步事件
        publishWorkflowEvent("INSTANCE_ACTIVATED", instanceId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
    public void complete(String instanceId, String endNodeCode) {
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
        // P2-3: Prometheus 指标 — 实例完成 + 耗时
        if (flowMetrics != null) {
            flowMetrics.incInstanceFinished(instance.getFlowCode(), "COMPLETED");
            flowMetrics.recordInstanceDuration(instance, "COMPLETED");
        }

        // 业务侧事件：onInstanceCompleted
        fireEvent(l -> l.onInstanceCompleted(instanceId));
        // P2-35: 发布 Spring 异步事件
        publishWorkflowEvent("INSTANCE_COMPLETED", instanceId, null);
        // 自动触发：检查是否需要自动发起下一流程
        try {
            autoTriggerService.onInstanceCompleted(instanceId);
        } catch (Exception e) {
            log.warn("[Flow] 自动触发检查失败: instanceId={} err={}", instanceId, e.getMessage());
        }
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
    @Transactional(readOnly = true)
    public List<FlowInstanceDO> listByInitiator(String initiatorId, String flowStatus) {
        return instanceMapper.selectByInitiator(initiatorId, flowStatus);
    }

    // ============================== P1-8: 撤回 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
    public boolean recall(String instanceId, String initiatorId) {
        FlowInstanceDO instance = getByIdOrThrow(instanceId);
        // 校验：仅发起人可撤回
        if (!instance.getInitiatorId().equals(initiatorId)) {
            throw new SysException(StandardResultCode.FORBIDDEN, "error.workflow.msg_cc712a3a");
        }
        // 校验：仅运行中可撤回
        if (!FlowInstanceStatus.RUNNING.name().equals(instance.getFlowStatus())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_3095a676");
        }
        // 校验：下一节点未被处理（PENDING 状态的任务可以撤回）
        List<FlowRunTaskDO> pendingTasks = taskMapper.selectPendingByInstance(instanceId);
        boolean anyProcessed = pendingTasks.stream()
                .anyMatch(t -> FlowTaskStatus.CLAIMED.name().equals(t.getTaskStatus())
                        || FlowTaskStatus.COMPLETED.name().equals(t.getTaskStatus()));
        if (anyProcessed) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_c55fe642");
        }
        // 取消当前待办
        taskService.cancelByInstance(instanceId, FlowTaskStatus.CANCELLED.name());
        // 回退到开始节点的下一节点（重新生成第一批待办）
        // 简化实现：将实例状态保持 RUNNING，重新推进到第一个审批节点
        try {
            advancer.start(instanceId);
        } catch (Exception e) {
            log.error("[Flow] 撤回后重新推进失败: instanceId={}", instanceId, e);
            throw new SysException(StandardResultCode.INTERNAL_ERROR, "error.workflow.msg_3d726320", e.getMessage());
        }
        log.info("[Flow] 撤回流程: instanceId={} initiatorId={}", instanceId, initiatorId);
        // P2-3: Prometheus 指标 — 撤回
        if (flowMetrics != null) {
            flowMetrics.incRecall(instance.getFlowCode());
        }
        // P2-34: 触发 onInstanceRecalled 事件
        fireEvent(l -> l.onInstanceRecalled(instanceId, initiatorId));
        // P2-35: 发布 Spring 异步事件
        publishWorkflowEvent("INSTANCE_RECALLED", instanceId, null);
        // P2-6: 双向同步 — 撤回对应三方 canceled（发起人撤回），主动取消三方审批单
        try {
            thirdPartySyncService.syncBackOnRecall(instanceId, initiatorId);
        } catch (Exception e) {
            log.warn("[Flow] 三方审批同步撤回失败（不影响本地撤回）: instanceId={} err={}",
                    instanceId, e.getMessage());
        }
        return true;
    }

    // ============================== P1-1: 撤回到指定历史节点 ==============================

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRecallableNodes(String instanceId, String initiatorId) {
        FlowInstanceDO instance = getByIdOrThrow(instanceId);
        // 校验：仅发起人可查询
        if (!instance.getInitiatorId().equals(initiatorId)) {
            throw new SysException(StandardResultCode.FORBIDDEN, "error.workflow.msg_cc712a3a");
        }
        // 校验：仅运行中可查询
        if (!FlowInstanceStatus.RUNNING.name().equals(instance.getFlowStatus())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_3095a676");
        }
        // 查历史已办节点
        List<Map<String, Object>> passedNodes = hisTaskMapper.listPassedNodes(instanceId);
        if (passedNodes == null || passedNodes.isEmpty()) {
            return Collections.emptyList();
        }
        // 排除当前待办节点（撤回到当前节点无意义）
        String currentNodeCode = instance.getCurrentNodeCode();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> n : passedNodes) {
            Object code = n.get("nodeCode");
            if (code != null && !code.toString().equals(currentNodeCode)) {
                result.add(n);
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
    public boolean recall(String instanceId, String initiatorId, String targetNodeCode) {
        // 向后兼容：targetNodeCode 为空时降级到原有 recall
        if (!StringUtils.hasText(targetNodeCode)) {
            return recall(instanceId, initiatorId);
        }

        FlowInstanceDO instance = getByIdOrThrow(instanceId);
        // 校验：仅发起人可撤回
        if (!instance.getInitiatorId().equals(initiatorId)) {
            throw new SysException(StandardResultCode.FORBIDDEN, "error.workflow.msg_cc712a3a");
        }
        // 校验：仅运行中可撤回
        if (!FlowInstanceStatus.RUNNING.name().equals(instance.getFlowStatus())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_3095a676");
        }
        // 校验：下一节点未被处理（PENDING 状态的任务可以撤回）
        List<FlowRunTaskDO> pendingTasks = taskMapper.selectPendingByInstance(instanceId);
        boolean anyProcessed = pendingTasks.stream()
                .anyMatch(t -> FlowTaskStatus.CLAIMED.name().equals(t.getTaskStatus())
                        || FlowTaskStatus.COMPLETED.name().equals(t.getTaskStatus()));
        if (anyProcessed) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_c55fe642");
        }
        // 校验：targetNodeCode 必须在可撤回节点列表中
        List<Map<String, Object>> recallable = hisTaskMapper.listPassedNodes(instanceId);
        Set<String> recallableCodes = new HashSet<>();
        if (recallable != null) {
            for (Map<String, Object> n : recallable) {
                Object code = n.get("nodeCode");
                if (code != null) {
                    recallableCodes.add(code.toString());
                }
            }
        }
        if (!recallableCodes.contains(targetNodeCode)) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "error.workflow.msg_e5f6a7b8", targetNodeCode);
        }

        // 取消当前待办（审计：CANCELLED，原因 RECALL）
        String currentNodeCode = pendingTasks.isEmpty()
                ? instance.getCurrentNodeCode() : pendingTasks.get(0).getNodeCode();
        taskService.cancelByInstance(instanceId, FlowTaskStatus.CANCELLED.name());

        // 退回到目标节点（复用 advancer.advance 的 REJECT 通道，保持审计轨迹一致）
        Map<String, Object> variables = parseVariables(instance.getVariable());
        try {
            advancer.advance(instance, currentNodeCode, "REJECT", targetNodeCode, variables);
        } catch (Exception e) {
            log.error("[Flow] 撤回到指定节点失败: instanceId={} targetNodeCode={}",
                    instanceId, targetNodeCode, e);
            throw new SysException(StandardResultCode.INTERNAL_ERROR,
                    "error.workflow.msg_3d726320", e.getMessage());
        }

        log.info("[Flow] 撤回流程到指定节点: instanceId={} initiatorId={} targetNodeCode={}",
                instanceId, initiatorId, targetNodeCode);
        // P2-3: Prometheus 指标 — 撤回
        if (flowMetrics != null) {
            flowMetrics.incRecall(instance.getFlowCode());
        }
        // P2-34: 触发 onInstanceRecalled 事件
        fireEvent(l -> l.onInstanceRecalled(instanceId, initiatorId));
        // P2-35: 发布 Spring 异步事件
        publishWorkflowEvent("INSTANCE_RECALLED", instanceId, null);
        // P2-6: 双向同步 — 撤回对应三方 canceled
        try {
            thirdPartySyncService.syncBackOnRecall(instanceId, initiatorId);
        } catch (Exception e) {
            log.warn("[Flow] 三方审批同步撤回失败（不影响本地撤回）: instanceId={} err={}",
                    instanceId, e.getMessage());
        }
        return true;
    }

    // ============================== P2-3: 流程回滚（已完成实例撤销） ==============================

    /** P2-3: 默认允许回滚的最大天数 */
    private static final int DEFAULT_ROLLBACK_DAYS = 7;

    /** P2-3: 管理员回滚权限编码 */
    private static final String PERM_INSTANCE_ROLLBACK = "workflow:instance:rollback";

    @Override
    @Transactional(rollbackFor = Exception.class)
    @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
    public boolean rollback(String instanceId, String operatorId, String reason, int maxRollbackDays) {
        FlowInstanceDO instance = getByIdOrThrow(instanceId);

        // 1. 校验：仅 COMPLETED 状态可回滚
        if (!FlowInstanceStatus.COMPLETED.name().equals(instance.getFlowStatus())) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "error.workflow.msg_a1b2c3d4", instance.getFlowStatus());
        }

        // 2. 校验：仅发起人或管理员可回滚
        boolean isInitiator = instance.getInitiatorId() != null
                && instance.getInitiatorId().equals(operatorId);
        boolean isAdmin = false;
        LoginUser user =
                AuthContext.getCurrentOrNull();
        if (user != null) {
            isAdmin = user.isSuperAdmin() || user.hasPermission(PERM_INSTANCE_ROLLBACK);
        }
        if (!isInitiator && !isAdmin) {
            throw new SysException(StandardResultCode.FORBIDDEN, "error.workflow.msg_b2c3d4e5");
        }

        // 3. 校验：回滚原因不能为空
        if (!StringUtils.hasText(reason)) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_d4e5f6a7");
        }

        // 4. 校验：时间窗口
        int days = maxRollbackDays > 0 ? maxRollbackDays : DEFAULT_ROLLBACK_DAYS;
        if (instance.getEndAt() != null) {
            long elapsedDays = Duration.between(instance.getEndAt(), LocalDateTime.now()).toDays();
            if (elapsedDays > days) {
                throw new SysException(StandardResultCode.BAD_REQUEST,
                        "error.workflow.msg_c3d4e5f6", days);
            }
        }

        // 5. 更新实例状态为 ROLLED_BACK（保留 currentNodeCode/currentNodeName 不变，便于追溯）
        LocalDateTime now = LocalDateTime.now();
        Long durationMs = instance.getStartAt() == null
                ? null
                : Duration.between(instance.getStartAt(), now).toMillis();
        instanceMapper.updateStatus(instanceId, FlowInstanceStatus.ROLLED_BACK.name(),
                instance.getCurrentNodeCode(), instance.getCurrentNodeName(),
                now, durationMs);

        // 6. 记录回滚元信息到 variable JSON（保留原有变量，仅追加 _rollback 字段）
        try {
            Map<String, Object> vars = parseVariables(instance.getVariable());
            Map<String, Object> rollbackInfo = new LinkedHashMap<>();
            rollbackInfo.put("operatorId", operatorId);
            rollbackInfo.put("reason", reason);
            rollbackInfo.put("rolledBackAt", now.toString());
            rollbackInfo.put("byAdmin", isAdmin && !isInitiator);
            vars.put("_rollback", rollbackInfo);
            instanceMapper.updateVariable(instanceId, JSON.toJSONString(vars));
        } catch (Exception e) {
            log.warn("[Flow] 回滚元信息持久化失败: instanceId={} err={}", instanceId, e.getMessage());
        }

        log.info("[Flow] 回滚流程: instanceId={} operatorId={} reason={} isAdmin={}",
                instanceId, operatorId, reason, isAdmin && !isInitiator);

        // 7. Prometheus 指标 — 复用 incRecall 计数器
        if (flowMetrics != null) {
            flowMetrics.incRecall(instance.getFlowCode());
        }

        // 8. 触发 onInstanceRolledBack 事件（业务侧可执行补偿）
        fireEvent(l -> l.onInstanceRolledBack(instanceId, operatorId, reason));

        // 9. 发布 Spring 异步事件
        publishWorkflowEvent("INSTANCE_ROLLED_BACK", instanceId, null);

        return true;
    }

    // ============================== P2-23: 实例多维分页查询 ==============================

    @Override
    @Transactional(readOnly = true)
    @DataScope(deptAlias = "", userAlias = "", userColumn = "initiator_id")
    public PageResponse<FlowInstanceDO> page(String businessType, String initiatorId, String flowStatus,
                                           LocalDateTime startTime, LocalDateTime endTime,
                                           String tenantId, int pageNo, int pageSize) {
        // P2-23: 真分页（SQL LIMIT/OFFSET），支持多维度过滤
        int safePage = Math.max(1, pageNo);
        int safeSize = pageSize > 0 ? pageSize : 20;
        int offset = (safePage - 1) * safeSize;
        // P1-3: 数据权限 SQL 片段（由 DataScopeAspect ThreadLocal 传递，DataScopeHelper 构造）
        String dataScopeFilter = "";
        try {
            dataScopeFilter = DataScopeHelper
                    .buildSqlFragment("", "", "dept_id", "initiator_id");
        } catch (Exception e) {
            log.debug("[Flow] 数据权限片段构建失败（无登录用户上下文）: {}", e.getMessage());
        }
        List<FlowInstanceDO> list = instanceMapper.selectPage(
                businessType, initiatorId, flowStatus, startTime, endTime, tenantId,
                dataScopeFilter, offset, safeSize);
        long total = instanceMapper.countPage(
                businessType, initiatorId, flowStatus, startTime, endTime, tenantId, dataScopeFilter);
        return (PageResponse) PageResponse.success(total, (long) safePage, (long) safeSize, list);
    }

    // ============================== P2-24: 流程变量读写 ==============================

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getVariables(String instanceId) {
        // P2-24: 读取实例 variable JSON 并解析为 Map
        FlowInstanceDO instance = instanceMapper.selectById(instanceId);
        if (instance == null || !StringUtils.hasText(instance.getVariable())) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> map = JsonUtils.parseMap(instance.getVariable());
            return map == null ? Collections.emptyMap() : map;
        } catch (Exception e) {
            log.warn("[Flow] 解析 variable JSON 失败: instanceId={} err={}",
                    instanceId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setVariable(String instanceId, String key, Object value) {
        // P2-24: 合并写入单个变量并持久化
        if (!StringUtils.hasText(key)) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_fae06125");
        }
        FlowInstanceDO instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "error.workflow.msg_67a10717", instanceId);
        }
        Map<String, Object> map = parseVariables(instance.getVariable());
        map.put(key, value);
        instanceMapper.updateVariable(instanceId, JSON.toJSONString(map));
        log.info("[Flow] 设置变量: instanceId={} key={}", instanceId, key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setVariables(String instanceId, Map<String, Object> variables) {
        // P2-24: 批量合并写入变量并持久化
        if (variables == null || variables.isEmpty()) {
            return;
        }
        FlowInstanceDO instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "error.workflow.msg_67a10717", instanceId);
        }
        Map<String, Object> map = parseVariables(instance.getVariable());
        map.putAll(variables);
        instanceMapper.updateVariable(instanceId, JSON.toJSONString(map));
        log.info("[Flow] 批量设置变量: instanceId={} keys={}", instanceId, variables.keySet());
    }

    /** 解析 variable JSON 为 Map，空值返回空 Map */
    private Map<String, Object> parseVariables(String variable) {
        if (!StringUtils.hasText(variable)) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> map = JsonUtils.parseMap(variable);
            return map == null ? new HashMap<>() : map;
        } catch (Exception e) {
            log.warn("[Flow] 解析 variable JSON 失败，返回空 Map: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 将 {@code Map<?,?>} 强转为 {@code Map<String, Object>}。
     *
     * <p>ext JSON 由业务方配置（节点扩展字段），运行时信任其结构为 Map&lt;String,Object&gt;，
     * 因此这里的强转是安全的。该方法仅用于抑制 unchecked cast 编译警告。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> castToStringObjectMap(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    // ============================== 内部方法 ==============================

    private FlowInstanceDO getByIdOrThrow(String id) {
        FlowInstanceDO instance = instanceMapper.selectById(id);
        if (instance == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "error.workflow.msg_67a10717", id);
        }
        return instance;
    }

    /** 内部方法：创建第一个待办任务（供 FlowAdvancer 调用） */
    public String createFirstTask(String instanceId, FlowNodeDO startNode,
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
    public void generateTasksForNodes(String instanceId, List<FlowNodeDO> nextNodes,
                                       Map<String, Object> variables) {
        if (nextNodes == null || nextNodes.isEmpty()) {
            return;
        }
        for (FlowNodeDO node : nextNodes) {
            // P0-2: 优先判断事件捕获节点（boundaryEvent / intermediateCatchEvent）
            // 历史问题：boundaryEvent 在 mapNodeType 中被映射为 CC 类型，会被下方 CC 分支误处理为抄送
            // 修复：先判断 isEventCatchNode（基于 ext.eventCatch=true），命中则走事件订阅逻辑
            if (eventSubscriptionService.isEventCatchNode(node)) {
                String boundaryTaskId = resolveBoundaryTaskId(node, instanceId);
                eventSubscriptionService.createSubscription(instanceId, node, variables, boundaryTaskId);
                // P0-2: 如果 ext.timer 存在，注册边界定时器自动触发（timer boundary 语义）
                scheduleBoundaryTimerIfPresent(node, instanceId, boundaryTaskId);
                // 更新实例当前节点为事件捕获节点（流程在此等待事件触发）
                instanceMapper.updateStatus(instanceId, null,
                        node.getNodeCode(), node.getNodeName(), null, null);
                log.info("[Flow] 事件捕获节点等待触发: instanceId={} node={} type={}",
                        instanceId, node.getNodeCode(), node.getNodeType());
                continue;
            }
            if (node.getNodeType().equals(FlowNodeType.CC.getCode())) {
                // GAP-P1: 抄送节点 — 展开接收人并写入 pmis_flow_cc，然后自动推进到下一节点
                try {
                    ccService.handleCcNode(instanceId, node, variables);
                    log.info("[Flow] 抄送节点处理完成: instanceId={} node={}", instanceId, node.getNodeCode());
                } catch (Exception e) {
                    log.warn("[Flow] 抄送节点处理失败，跳过继续: instanceId={} node={} err={}",
                            instanceId, node.getNodeCode(), e.getMessage());
                }
                // 抄送节点是穿透节点：自动推进到下游
                FlowInstanceDO ccInstance = instanceMapper.selectById(instanceId);
                if (ccInstance != null) {
                    List<FlowNodeDO> ccNext = advancer.advance(ccInstance, node.getNodeCode(),
                            "PASS", null, variables);
                    if (!ccNext.isEmpty()) {
                        generateTasksForNodes(instanceId, ccNext, variables);
                    }
                }
                continue;
            }
            if (node.getNodeType().equals(FlowNodeType.END.getCode())) {
                complete(instanceId, node.getNodeCode());
                return;
            }
            // P1-3 / fix-1: SUBPROCESS 节点或 ext 中含 callActivityFlowCode 的节点触发子流程
            if (node.getNodeType().equals(FlowNodeType.SUBPROCESS.getCode()) || isCallActivity(node)) {
                try {
                    FlowInstanceDO instance = instanceMapper.selectById(instanceId);
                    subProcessService.startSubProcess(instance, node, variables);
                    // 子流程启动后，父流程"停在" callActivity 节点，更新 currentNodeCode
                    instanceMapper.updateStatus(instanceId, instance.getFlowStatus(),
                            node.getNodeCode(), node.getNodeName(), null, null);
                    log.info("[Flow] callActivity 触发子流程: instanceId={} node={}",
                            instanceId, node.getNodeCode());
                } catch (Exception e) {
                    log.error("[Flow] callActivity 启动子流程失败: instanceId={} node={} err={}",
                            instanceId, node.getNodeCode(), e.getMessage(), e);
                    throw new SysException(StandardResultCode.INTERNAL_ERROR,
                            "error.workflow.msg_f2bd498c", e.getMessage());
                }
                continue;
            }
            taskService.createTask(instanceId, node, variables);
        }
    }

    /**
     * P0-2: 解析 boundaryEvent 的 timer 配置并注册边界定时器
     *
     * <p>BPMN timer event definition 支持三种形式：
     * <ul>
     *   <li>{@code timeDuration} — ISO 8601 持续时间（如 "PT1H30M"），到点触发一次</li>
     *   <li>{@code timeDate} — ISO 8601 绝对时间（如 "2026-07-07T10:00:00"），到点触发一次</li>
     *   <li>{@code timeCycle} — ISO 8601 循环（如 "R3/PT10M"），目前仅支持首次触发，循环触发待后续实现</li>
     * </ul>
     *
     * <p>解析失败时不抛异常，仅记录 warn 日志，避免阻塞流程实例创建。
     */
    private void scheduleBoundaryTimerIfPresent(FlowNodeDO node, String instanceId, String boundaryTaskId) {
        if (timerService == null || boundaryTaskId == null) {
            return;
        }
        Map<String, Object> ext = parseExtMap(node);
        if (ext == null) return;
        Object timerObj = ext.get("timer");
        if (!(timerObj instanceof Map<?, ?> timerRaw)) {
            return;
        }
        Duration delay = parseTimerDelay(timerRaw);
        if (delay == null || delay.isNegative() || delay.isZero()) {
            log.warn("[Flow] 边界定时器配置无法解析或已过期，跳过: node={} timer={}",
                    node.getNodeCode(), timerRaw);
            return;
        }
        try {
            timerService.scheduleBoundary(boundaryTaskId, instanceId, node.getNodeCode(), delay);
            log.info("[Flow] 边界定时器已注册: instanceId={} node={} delay={} taskId={}",
                    instanceId, node.getNodeCode(), delay, boundaryTaskId);
        } catch (Exception e) {
            log.warn("[Flow] 边界定时器注册失败: instanceId={} node={} err={}",
                    instanceId, node.getNodeCode(), e.getMessage());
        }
    }

    /**
     * P0-2: 解析 BPMN timer 配置为 Duration
     *
     * <p>优先级：duration > date > cycle（cycle 仅取首次）
     */
    private Duration parseTimerDelay(Map<?, ?> timer) {
        Object duration = timer.get("duration");
        if (duration != null) {
            try {
                return Duration.parse(duration.toString());  // ISO 8601, e.g. "PT1H30M"
            } catch (Exception e) {
                log.warn("[Flow] timer.duration 解析失败: {} err={}", duration, e.getMessage());
            }
        }
        Object date = timer.get("date");
        if (date != null) {
            try {
                LocalDateTime target = LocalDateTime.parse(date.toString(),
                        DateTimeFormatter.ISO_DATE_TIME);
                Duration d = Duration.between(LocalDateTime.now(), target);
                return d.isNegative() ? null : d;
            } catch (Exception e) {
                log.warn("[Flow] timer.date 解析失败: {} err={}", date, e.getMessage());
            }
        }
        // cycle（如 "R3/PT10M"）暂仅支持首次触发：提取 PT 部分
        Object cycle = timer.get("cycle");
        if (cycle != null) {
            String cycleStr = cycle.toString();
            // 简单提取 PT 片段（"R3/PT10M" → "PT10M"）
            int ptIdx = cycleStr.indexOf("PT");
            if (ptIdx >= 0) {
                try {
                    return Duration.parse(cycleStr.substring(ptIdx));
                } catch (Exception e) {
                    log.warn("[Flow] timer.cycle 解析失败: {} err={}", cycle, e.getMessage());
                }
            }
        }
        return null;
    }

    /**
     * P0-2: 解析节点 ext JSON 为 Map（容错）
     */
    private Map<String, Object> parseExtMap(FlowNodeDO node) {
        if (node == null || !StringUtils.hasText(node.getExt())) {
            return null;
        }
        try {
            return JsonUtils.parseMap(node.getExt());
        } catch (Exception e) {
            log.warn("[Flow] 节点 ext 解析失败: nodeCode={} err={}",
                    node.getNodeCode(), e.getMessage());
            return null;
        }
    }

    /**
     * P0-1: 解析边界事件关联的 userTask ID
     *
     * <p>boundaryEvent 节点 ext 中 attachedToRef 指向被附着的节点编码，
     * 查找该节点的当前 PENDING 任务作为 boundaryTaskId。
     * intermediateCatchEvent 无 attachedToRef，返回 null。
     */
    private String resolveBoundaryTaskId(FlowNodeDO node, String instanceId) {
        if (node == null || !StringUtils.hasText(node.getExt())) {
            return null;
        }
        try {
            Map<String, Object> ext = JsonUtils.parseMap(node.getExt());
            if (ext == null) return null;
            String attachedToRef = (String) ext.get("attachedToRef");
            if (!StringUtils.hasText(attachedToRef)) {
                return null;
            }
            // 查找被附着节点的当前 PENDING 任务
            List<FlowRunTaskDO> tasks = taskMapper.selectPendingByNode(instanceId, attachedToRef);
            return tasks.isEmpty() ? null : tasks.get(0).getId();
        } catch (Exception e) {
            log.warn("[Flow] 解析 boundaryTaskId 失败: nodeCode={} err={}",
                    node.getNodeCode(), e.getMessage());
            return null;
        }
    }

    /**
     * P1-3: 判断节点是否为 callActivity（子流程）
     * <p>识别条件：节点 ext JSON 中包含 callActivityFlowCode 字段
     */
    private boolean isCallActivity(FlowNodeDO node) {
        if (node == null || !StringUtils.hasText(node.getExt())) {
            return false;
        }
        try {
            Map<String, Object> ext = JsonUtils.parseMap(node.getExt());
            if (ext == null) return false;
            return ext.containsKey("callActivityFlowCode")
                    || ext.containsKey("subProcessFlowCode");
        } catch (Exception e) {
            log.warn("[FlowInstanceServiceImpl] 节点 ext 解析失败，视为非子流程调用: {}", e.getMessage());
            return false;
        }
    }

    // ============================== GAP-V2-08: 流程模拟运行 ==============================

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> simulate(String flowCode, String version,
                                               Map<String, Object> variables, String tenantId) {
        if (!StringUtils.hasText(flowCode)) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_ebccbe46");
        }
        // 解析租户
        String tid = tenantId != null ? tenantId : AuthContext.getTenantIdOrDefault("1");
        // 查询已发布流程定义
        FlowDefinitionDO def = definitionService.getPublished(flowCode, version, tid);
        if (def == null) {
            throw new SysException(StandardResultCode.NOT_FOUND,
                    "error.workflow.msg_add8d012" + flowCode + " version=" + version);
        }
        // 查询节点 + 跳转
        List<FlowNodeDO> nodes = nodeMapper.selectByDefinitionId(def.getId());
        List<FlowSkipDO> skips = skipMapper.selectByDefinitionId(def.getId());

        // 构建节点查找 Map
        Map<String, FlowNodeDO> nodeMap = new HashMap<>();
        for (FlowNodeDO node : nodes) {
            nodeMap.put(node.getNodeCode(), node);
        }

        // 构建跳转查找 Map: fromNodeCode -> List<FlowSkipDO>
        Map<String, List<FlowSkipDO>> skipMap = new HashMap<>();
        for (FlowSkipDO skip : skips) {
            String fromNodeCode = extractFromNodeCode(skip);
            if (fromNodeCode != null) {
                skipMap.computeIfAbsent(fromNodeCode, k -> new ArrayList<>()).add(skip);
            }
        }

        // 查找开始节点
        FlowNodeDO startNode = null;
        for (FlowNodeDO node : nodes) {
            if (node.getNodeType() != null && node.getNodeType() == FlowNodeType.START.getCode()) {
                startNode = node;
                break;
            }
        }
        if (startNode == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_69a69bcd");
        }

        // 模拟遍历
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        FlowNodeDO currentNode = startNode;
        int step = 0;
        final int MAX_STEPS = 50;

        while (currentNode != null && step < MAX_STEPS) {
            step++;

            // 循环检测
            if (visited.contains(currentNode.getNodeCode())) {
                Map<String, Object> cycleStep = new LinkedHashMap<>();
                cycleStep.put("step", step);
                cycleStep.put("nodeCode", currentNode.getNodeCode());
                cycleStep.put("nodeName", currentNode.getNodeName());
                cycleStep.put("nodeType", currentNode.getNodeType());
                cycleStep.put("assignee", currentNode.getPermissionFlag());
                cycleStep.put("condition", null);
                cycleStep.put("skipped", false);
                cycleStep.put("warning", "检测到循环，模拟终止");
                result.add(cycleStep);
                log.warn("[Flow-Simulate] 检测到循环，终止模拟: flowCode={} nodeCode={}",
                        flowCode, currentNode.getNodeCode());
                break;
            }
            visited.add(currentNode.getNodeCode());

            // 记录当前节点
            Map<String, Object> stepMap = new LinkedHashMap<>();
            stepMap.put("step", step);
            stepMap.put("nodeCode", currentNode.getNodeCode());
            stepMap.put("nodeName", currentNode.getNodeName());
            stepMap.put("nodeType", currentNode.getNodeType());
            stepMap.put("assignee", currentNode.getPermissionFlag());
            stepMap.put("condition", null);
            stepMap.put("skipped", false);
            result.add(stepMap);

            // 遇到 END 节点终止
            if (currentNode.getNodeType() != null
                    && currentNode.getNodeType() == FlowNodeType.END.getCode()) {
                break;
            }

            // 查找当前节点的出边（PASS 类型）
            List<FlowSkipDO> outgoingSkips = skipMap.getOrDefault(
                    currentNode.getNodeCode(), Collections.emptyList());
            List<FlowSkipDO> passSkips = new ArrayList<>();
            for (FlowSkipDO skip : outgoingSkips) {
                if (skip.getSkipType() == null || "PASS".equalsIgnoreCase(skip.getSkipType())) {
                    passSkips.add(skip);
                }
            }

            if (passSkips.isEmpty()) {
                // 无出边，终止
                break;
            }

            // 条件求值，寻找匹配的跳转
            boolean isExclusive = currentNode.getNodeType() != null
                    && currentNode.getNodeType() == FlowNodeType.CONDITION.getCode();
            boolean isInclusive = currentNode.getNodeType() != null
                    && currentNode.getNodeType() == FlowNodeType.INCLUSIVE.getCode();

            FlowSkipDO matchedSkip = null;
            for (FlowSkipDO skip : passSkips) {
                String cond = skip.getSkipCondition();
                if (cond == null || cond.isBlank()
                        || variableStrategy.evaluate(cond, variables)) {
                    matchedSkip = skip;
                    // 记录匹配的条件
                    if (cond != null && !cond.isBlank()) {
                        stepMap.put("condition", cond);
                    }
                    // 排他网关：只取第一条匹配
                    if (isExclusive) {
                        break;
                    }
                    // 包容网关：取所有匹配，模拟时取第一条
                    if (isInclusive) {
                        break;
                    }
                    break;
                }
            }

            // 排他/包容网关兜底：无匹配取默认出边
            if (matchedSkip == null && (isExclusive || isInclusive)) {
                matchedSkip = passSkips.get(0);
                stepMap.put("condition", "default（无匹配条件，取默认出边）");
                log.info("[Flow-Simulate] 网关无匹配条件，取默认出边: nodeCode={}",
                        currentNode.getNodeCode());
            }

            // 普通节点无条件匹配，取第一条
            if (matchedSkip == null) {
                matchedSkip = passSkips.get(0);
            }

            if (matchedSkip == null || matchedSkip.getNextNodeCode() == null) {
                break;
            }

            // 前进到下一节点
            currentNode = nodeMap.get(matchedSkip.getNextNodeCode());
        }

        if (step >= MAX_STEPS) {
            log.warn("[Flow-Simulate] 超过最大步数 {}，终止模拟: flowCode={}", MAX_STEPS, flowCode);
        }

        log.info("[Flow-Simulate] 模拟完成: flowCode={} version={} steps={}",
                flowCode, def.getFlowVersion(), BaseResponse.size());
        return result;
    }

    /**
     * GAP-V2-08: 从 FlowSkipDO.ext 字段中提取源节点编码（sourceRef）
     *
     * @param skip 跳转 DO
     * @return 源节点编码，解析失败返回 null
     */
    private String extractFromNodeCode(FlowSkipDO skip) {
        if (skip.getExt() == null || skip.getExt().isBlank()) {
            return null;
        }
        try {
            Map<String, Object> ext = JsonUtils.parseMap(skip.getExt());
            if (ext != null && ext.containsKey("sourceRef")) {
                return (String) ext.get("sourceRef");
            }
        } catch (Exception e) {
            log.warn("[Flow-Simulate] skip ext 解析失败: skipId={} err={}",
                    skip.getId(), e.getMessage());
        }
        return null;
    }

    // ============================== 事件触发 ==============================

    private void fireInstanceStart(String instanceId, Map<String, Object> variables) {
        if (eventListeners == null) return;
        for (FlowEventListener listener : eventListeners) {
            try {
                listener.onInstanceStart(instanceId, variables);
            } catch (Exception e) {
                log.warn("[Flow] onInstanceStart 事件失败: {}", e.getMessage());
            }
        }
    }

    private void fireEvent(Consumer<FlowEventListener> action) {
        if (eventListeners == null) return;
        for (FlowEventListener listener : eventListeners) {
            try {
                action.accept(listener);
            } catch (Exception e) {
                log.warn("[Flow] 事件监听器异常: {}", e.getMessage());
            }
        }
    }

    private void fireError(String instanceId, Throwable t) {
        if (eventListeners == null) return;
        for (FlowEventListener listener : eventListeners) {
            try {
                listener.onError(instanceId, t);
            } catch (Exception e) {
                log.warn("[Flow] onError 事件失败: {}", e.getMessage());
            }
        }
    }

    /**
     * P2-35: 发布 Spring 异步事件（ApplicationEventPublisher 可能为 null，需检查）
     *
     * @param eventType  事件类型
     * @param instanceId 实例 ID
     * @param taskId     任务 ID（可空）
     */
    private void publishWorkflowEvent(String eventType, String instanceId, String taskId) {
        if (eventPublisher == null) return;
        try {
            eventPublisher.publishEvent(new FlowWorkflowEvent(this, eventType, instanceId, taskId, null));
        } catch (Exception e) {
            log.warn("[Flow] 发布 Spring 事件失败: type={} err={}", eventType, e.getMessage());
        }
    }

    /**
     * P2-37: 构建事件上下文元数据
     *
     * @param instanceId 实例 ID
     * @param taskId     任务 ID
     * @param operatorId 操作人 ID
     * @param action     操作动作
     * @param instance   流程实例（用于提取 tenantId/traceId，可空）
     * @return 事件上下文
     */
    private FlowEventContext buildContext(String instanceId, String taskId, String operatorId,
                                          String action, FlowInstanceDO instance) {
        FlowEventContext ctx = new FlowEventContext();
        ctx.setInstanceId(instanceId);
        ctx.setTaskId(taskId);
        ctx.setOperatorId(operatorId);
        ctx.setAction(action);
        ctx.setOperatedAt(LocalDateTime.now());
        if (instance != null) {
            ctx.setTenantId(instance.getTenantId() == null
                    ? null : String.valueOf(instance.getTenantId()));
            ctx.setTraceId(instance.getProviderTraceId());
        }
        return ctx;
    }

    // ============================== GAP-V2-02: 表单渲染数据 ==============================

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getFormRenderData(String instanceId, String taskId) {
        FlowInstanceDO instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "error.workflow.msg_fc4b1c16", instanceId);
        }
        String nodeCode;
        String nodeName;
        String formFieldsConfig = null;
        Map<String, Object> fieldPermissions = null;
        Map<String, Object> commentConfig = null;
        if (taskId != null) {
            // 优先从任务获取节点信息
            FlowRunTaskDO task = taskMapper.selectById(taskId);
            if (task == null) {
                throw new SysException(StandardResultCode.NOT_FOUND, "error.workflow.msg_6541ab08", taskId);
            }
            nodeCode = task.getNodeCode();
            nodeName = task.getNodeName();
        } else {
            // 回退到实例当前节点
            nodeCode = instance.getCurrentNodeCode();
            nodeName = instance.getCurrentNodeName();
        }
        // 查节点表获取 formFieldsConfig 和 ext 中的字段权限
        if (nodeCode != null) {
            FlowNodeDO node = nodeMapper.selectByCode(
                    instance.getDefinitionId(), nodeCode);
            if (node != null) {
                formFieldsConfig = node.getFormFieldsConfig();
                if (nodeName == null) {
                    nodeName = node.getNodeName();
                }
                // P1-4: 从 ext JSON 解析字段权限和审批意见配置
                if (node.getExt() != null && !node.getExt().isBlank()) {
                    try {
                        Map<String, Object> ext = JsonUtils.parseMap(node.getExt());
                        if (ext != null) {
                            Object fp = ext.get("formFieldPermissions");
                            if (fp instanceof Map<?, ?> m) {
                                // ext JSON 由业务方配置，运行时信任其结构为 Map<String,Object>，强转是安全的
                                fieldPermissions = castToStringObjectMap(m);
                            }
                            Object cc = ext.get("commentConfig");
                            if (cc instanceof Map<?, ?> m2) {
                                // 同上：ext JSON 业务方配置，运行时信任其结构为 Map<String,Object>
                                commentConfig = castToStringObjectMap(m2);
                            }
                        }
                    } catch (Exception e) {
                        log.debug("[Flow] 解析节点 ext 字段权限失败: node={} err={}",
                                nodeCode, e.getMessage());
                    }
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instanceId", instanceId);
        result.put("taskId", taskId);
        result.put("nodeCode", nodeCode);
        result.put("nodeName", nodeName);
        result.put("formFieldsConfig", formFieldsConfig);
        // P1-4: 字段权限配置（READONLY/REQUIRED/HIDDEN/EDITABLE）
        result.put("fieldPermissions", fieldPermissions);
        // P1-4: 审批意见配置（required/minLength/placeholder）
        result.put("commentConfig", commentConfig);
        result.put("variables", getVariables(instanceId));
        result.put("flowStatus", instance.getFlowStatus());
        result.put("title", instance.getTitle());
        return result;
    }

    // ============================== 子流程超时处理 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDueAt(String instanceId, LocalDateTime dueAt) {
        instanceMapper.updateDueAt(instanceId, dueAt);
        log.info("[Flow] 设置实例到期时间: instanceId={} dueAt={}", instanceId, dueAt);
    }

    // ============================== P2-2 (GAP-10): 驳回后快速重审 ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
    public String resubmit(String instanceId, String initiatorId,
                           Map<String, Object> variables, String comment) {
        FlowInstanceDO instance = getByIdOrThrow(instanceId);
        // 1. 状态校验：仅 REJECTED 可重审
        FlowInstanceStatus status = FlowInstanceStatus.valueOf(instance.getFlowStatus());
        if (status != FlowInstanceStatus.REJECTED) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_7f4098fb",
                    "仅被驳回实例可重审，当前状态=" + instance.getFlowStatus());
        }
        // 2. 发起人校验
        if (instance.getInitiatorId() != null
                && !String.valueOf(instance.getInitiatorId()).equals(initiatorId)) {
            throw new SysException(StandardResultCode.FORBIDDEN, "error.workflow.msg_d65b2814",
                    "仅发起人可重审");
        }
        // 3. 合并变量（保留历史变量，覆盖新增）
        Map<String, Object> merged = getVariables(instanceId);
        if (merged == null) {
            merged = new HashMap<>();
        }
        if (variables != null && !variables.isEmpty()) {
            merged.putAll(variables);
        }
        // 4. 重置实例状态为 RUNNING，清掉 REJECTED 标记，重置开始时间
        instance.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        instance.setActivityStatus(1);
        instance.setCurrentNodeCode(null);
        instance.setCurrentNodeName(null);
        instance.setStartAt(LocalDateTime.now());
        instance.setEndAt(null);
        instance.setRejectReason(null);
        instance.setVariable(merged.isEmpty() ? null : JSON.toJSONString(merged));
        instanceMapper.updateById(instance);
        // 5. 记录重审审计（保留原轨迹，仅追加一条 RESUBMIT 记录）
        FlowAuditLogDO audit = new FlowAuditLogDO();
        audit.setInstanceId(instanceId);
        audit.setFlowCode(instance.getFlowCode());
        audit.setBusinessType(instance.getBusinessType());
        audit.setBusinessId(instance.getBusinessId());
        audit.setAction("RESUBMIT");
        audit.setOperatorId(initiatorId);
        audit.setOperatorName(instance.getInitiatorName());
        audit.setComment(comment);
        audit.setTenantId(instance.getTenantId());
        audit.setProviderTraceId(instance.getProviderTraceId());
        audit.setOperatedAt(LocalDateTime.now());
        auditLogMapper.insert(audit);
        // 6. 从开始节点重新推进（复用 advancer.start，保留 pmis_flow_user/his_task 历史）
        try {
            advancer.start(instanceId);
        } catch (Exception e) {
            fireError(instanceId, e);
            throw e;
        }
        log.info("[Flow] 驳回后快速重审: instanceId={} initiatorId={}", instanceId, initiatorId);
        return instanceId;
    }

    // ============================== P1-8: 流程重做（redoMode） ==============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
    public String resubmit(String instanceId, String initiatorId,
                           Map<String, Object> variables, String comment, String redoMode) {
        String mode = (redoMode == null || redoMode.isBlank()) ? "RESTART" : redoMode.toUpperCase();
        if ("NEW_INSTANCE".equals(mode)) {
            return resubmitAsNewInstance(instanceId, initiatorId, variables, comment);
        }
        // 默认 RESTART 模式：委托到现有 resubmit（向后兼容）
        return resubmit(instanceId, initiatorId, variables, comment);
    }

    /**
     * NEW_INSTANCE 模式：创建全新实例，复用原实例的 flowCode / businessType / businessId / initiator，
     * 合并原变量与传入变量。原实例保持不变，仅追加一条 REDO_NEW_INSTANCE 审计日志。
     */
    private String resubmitAsNewInstance(String instanceId, String initiatorId,
                                          Map<String, Object> variables, String comment) {
        FlowInstanceDO instance = getByIdOrThrow(instanceId);
        // 1. 状态校验：仅非运行态可重做（RUNNING / SUSPENDED 不可）
        FlowInstanceStatus status = FlowInstanceStatus.valueOf(instance.getFlowStatus());
        if (status == FlowInstanceStatus.RUNNING || status == FlowInstanceStatus.SUSPENDED) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "error.workflow.msg_c9d0e1f2",
                    "运行中/挂起的实例不可重做，当前状态=" + instance.getFlowStatus());
        }
        // 2. 发起人校验
        if (instance.getInitiatorId() != null
                && !String.valueOf(instance.getInitiatorId()).equals(initiatorId)) {
            throw new SysException(StandardResultCode.FORBIDDEN, "error.workflow.msg_d65b2814",
                    "仅发起人可重做");
        }
        // 3. 合并变量（保留原实例变量，覆盖新增）
        Map<String, Object> merged = getVariables(instanceId);
        if (merged == null) {
            merged = new HashMap<>();
        }
        if (variables != null && !variables.isEmpty()) {
            merged.putAll(variables);
        }
        // 4. 构建新实例启动 DTO
        FlowStartProcessDTO dto = new FlowStartProcessDTO();
        dto.setFlowCode(instance.getFlowCode());
        dto.setVersion(instance.getFlowVersion());
        dto.setBusinessType(instance.getBusinessType());
        dto.setBusinessId(instance.getBusinessId());
        dto.setBusinessNo(instance.getBusinessNo());
        dto.setTitle(instance.getTitle());
        dto.setInitiatorId(initiatorId);
        dto.setInitiatorName(instance.getInitiatorName());
        dto.setVariables(merged.isEmpty() ? null : merged);
        dto.setTenantId(instance.getTenantId());
        dto.setProviderTraceId(instance.getProviderTraceId());
        // 5. 启动新实例
        String newInstanceId = start(dto);
        // 6. 在原实例上追加 REDO 审计日志（保留原轨迹，仅追加）
        FlowAuditLogDO audit = new FlowAuditLogDO();
        audit.setInstanceId(instanceId);
        audit.setFlowCode(instance.getFlowCode());
        audit.setBusinessType(instance.getBusinessType());
        audit.setBusinessId(instance.getBusinessId());
        audit.setAction("REDO_NEW_INSTANCE");
        audit.setOperatorId(initiatorId);
        audit.setOperatorName(instance.getInitiatorName());
        String redoComment = comment != null && !comment.isBlank()
                ? comment + " → 新实例[" + newInstanceId + "]"
                : "重做为新实例[" + newInstanceId + "]";
        audit.setComment(redoComment);
        audit.setTenantId(instance.getTenantId());
        audit.setProviderTraceId(instance.getProviderTraceId());
        audit.setOperatedAt(LocalDateTime.now());
        auditLogMapper.insert(audit);
        log.info("[Flow] 重做为新实例: 原实例={} 新实例={} initiatorId={}",
                instanceId, newInstanceId, initiatorId);
        return newInstanceId;
    }

    // ============================== P2-6: 批量发起流程实例 ==============================

    /** P2-6: 单次批量发起的最大数量限制（防止事务过多） */
    private static final int BATCH_START_MAX_SIZE = 100;

    /**
     * P2-6: 批量发起流程实例。
     *
     * <p>每个 {@link FlowStartProcessDTO} 通过 {@link #self}.start() 独立事务发起，
     * 单个失败不影响其他实例。返回成功发起的 instanceId 列表 + 失败项明细。
     */
    @Override
    public Map<String, Object> batchStartInstances(List<FlowStartProcessDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "error.workflow.msg_e4f5a6b7");
        }
        if (dtos.size() > BATCH_START_MAX_SIZE) {
            throw new SysException(StandardResultCode.BAD_REQUEST,
                    "error.workflow.msg_f5a6b7c8",
                    dtos.size(), BATCH_START_MAX_SIZE);
        }

        int successCount = 0;
        List<String> instanceIds = new ArrayList<>();
        List<Map<String, Object>> failedItems = new ArrayList<>();

        for (int i = 0; i < dtos.size(); i++) {
            FlowStartProcessDTO dto = dtos.get(i);
            String businessId = dto != null ? dto.getBusinessId() : null;
            try {
                // 通过 self 代理调用，确保 start() 的 @Transactional 生效（独立事务）
                String instanceId = self.start(dto);
                successCount++;
                instanceIds.add(instanceId);
                log.info("[Flow] 批量发起第 {} 条成功: businessId={} instanceId={}",
                        i + 1, businessId, instanceId);
            } catch (Exception e) {
                Map<String, Object> fail = new LinkedHashMap<>();
                fail.put("index", i + 1);
                fail.put("businessId", businessId);
                String reason = e.getMessage() != null
                        ? e.getMessage() : e.getClass().getSimpleName();
                fail.put("reason", reason);
                failedItems.add(fail);
                log.warn("[Flow] 批量发起第 {} 条失败: businessId={} reason={}",
                        i + 1, businessId, reason);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("successCount", successCount);
        result.put("failedCount", failedItems.size());
        result.put("instanceIds", instanceIds);
        result.put("failedItems", failedItems);
        log.info("[Flow] 批量发起完成: total={} success={} failed={}",
                dtos.size(), successCount, failedItems.size());
        return result;
    }
}
