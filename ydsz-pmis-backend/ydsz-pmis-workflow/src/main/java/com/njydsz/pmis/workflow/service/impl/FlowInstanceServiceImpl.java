package com.njydsz.pmis.workflow.service.impl;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.engine.FlowAdvancer;
import com.njydsz.pmis.workflow.engine.FlowEventContext;
import com.njydsz.pmis.workflow.engine.FlowEventListener;
import com.njydsz.pmis.workflow.engine.FlowVariableStrategy;
import com.njydsz.pmis.workflow.engine.FlowWorkflowEvent;
import com.njydsz.pmis.workflow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.entity.FlowSkipDO;
import com.njydsz.pmis.workflow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.enums.FlowNodeType;
import com.njydsz.pmis.workflow.enums.FlowTaskStatus;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.mapper.FlowSkipMapper;
import com.njydsz.pmis.workflow.mapper.FlowTaskMapper;
import com.njydsz.pmis.workflow.metrics.FlowMetrics;
import com.njydsz.pmis.workflow.service.FlowDefinitionService;
import com.njydsz.pmis.workflow.service.FlowInstanceService;
import com.njydsz.pmis.workflow.service.FlowSubProcessService;
import com.njydsz.pmis.workflow.service.FlowTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
    /** P3-1: 灰度发布服务（启动流程时按 canary 配置切流） */
    private final com.njydsz.pmis.workflow.service.FlowCanaryService canaryService;
    private final FlowAdvancer advancer;
    private final FlowTaskService taskService;
    private final FlowTaskMapper taskMapper;
    /** GAP-V2-08: 流程节点 Mapper（模拟运行时查询节点） */
    private final FlowNodeMapper nodeMapper;
    /** GAP-V2-08: 流程跳转 Mapper（模拟运行时查询跳转） */
    private final FlowSkipMapper skipMapper;
    /** GAP-V2-08: 条件求值策略（模拟运行时复用 SpEL 条件解析） */
    private final FlowVariableStrategy variableStrategy;
    private final List<FlowEventListener> eventListeners;
    /** P2-3: Prometheus 指标收集（可能为 null：测试环境） */
    private final FlowMetrics flowMetrics;
    /** P2-35: Spring 事件发布器，用于异步事件机制（测试环境可能为 null） */
    private final ApplicationEventPublisher eventPublisher;
    /** P1-3: 子流程服务（处理 callActivity 子流程启动） */
    private final FlowSubProcessService subProcessService;
    /** GAP-P1: 抄送服务（CC 节点处理） */
    private final com.njydsz.pmis.workflow.service.FlowCcService ccService;

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
        // P2-16: 多租户上下文 - DTO 显式传入优先，否则从 SecurityContext 获取
        Long tenantId = dto.getTenantId() != null
                ? dto.getTenantId()
                : SecurityContext.getTenantIdOrDefault(1L);
        // P3-1: 灰度发布 - 启动时按 canary 配置切流到稳定版或灰度版
        FlowDefinitionDO def = canaryService.resolveEffectiveDefinition(
                dto.getFlowCode(),
                StringUtils.hasText(dto.getVersion()) ? dto.getVersion() : "1.0",
                tenantId,
                dto.getInitiatorId());
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
        Long instanceId = instance.getId();

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
    public void activate(Long instanceId) {
        FlowInstanceDO instance = getByIdOrThrow(instanceId);
        if (!FlowInstanceStatus.SUSPENDED.name().equals(instance.getFlowStatus())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "仅挂起流程可激活");
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
        // P2-3: Prometheus 指标 — 实例完成 + 耗时
        if (flowMetrics != null) {
            flowMetrics.incInstanceFinished(instance.getFlowCode(), "COMPLETED");
            flowMetrics.recordInstanceDuration(instance, "COMPLETED");
        }

        // 业务侧事件：onInstanceCompleted
        fireEvent(l -> l.onInstanceCompleted(instanceId));
        // P2-35: 发布 Spring 异步事件
        publishWorkflowEvent("INSTANCE_COMPLETED", instanceId, null);
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
        // P2-3: Prometheus 指标 — 撤回
        if (flowMetrics != null) {
            flowMetrics.incRecall(instance.getFlowCode());
        }
        // P2-34: 触发 onInstanceRecalled 事件
        fireEvent(l -> l.onInstanceRecalled(instanceId, initiatorId));
        // P2-35: 发布 Spring 异步事件
        publishWorkflowEvent("INSTANCE_RECALLED", instanceId, null);
        return true;
    }

    // ============================== P2-23: 实例多维分页查询 ==============================

    @Override
    public PageResult<FlowInstanceDO> page(String businessType, Long initiatorId, String flowStatus,
                                           LocalDateTime startTime, LocalDateTime endTime,
                                           Long tenantId, int pageNo, int pageSize) {
        // P2-23: 真分页（SQL LIMIT/OFFSET），支持多维度过滤
        int safePage = Math.max(1, pageNo);
        int safeSize = pageSize > 0 ? pageSize : 20;
        int offset = (safePage - 1) * safeSize;
        List<FlowInstanceDO> list = instanceMapper.selectPage(
                businessType, initiatorId, flowStatus, startTime, endTime, tenantId, offset, safeSize);
        long total = instanceMapper.countPage(
                businessType, initiatorId, flowStatus, startTime, endTime, tenantId);
        return PageResult.of(list, total, safePage, safeSize);
    }

    // ============================== P2-24: 流程变量读写 ==============================

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getVariables(Long instanceId) {
        // P2-24: 读取实例 variable JSON 并解析为 Map
        FlowInstanceDO instance = instanceMapper.selectById(instanceId);
        if (instance == null || !StringUtils.hasText(instance.getVariable())) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> map = JSON.parseObject(instance.getVariable(), Map.class);
            return map == null ? Collections.emptyMap() : map;
        } catch (Exception e) {
            log.warn("[Flow] 解析 variable JSON 失败: instanceId={} err={}",
                    instanceId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setVariable(Long instanceId, String key, Object value) {
        // P2-24: 合并写入单个变量并持久化
        if (!StringUtils.hasText(key)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "变量名不能为空");
        }
        FlowInstanceDO instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "流程实例不存在: " + instanceId);
        }
        Map<String, Object> map = parseVariables(instance.getVariable());
        map.put(key, value);
        instanceMapper.updateVariable(instanceId, JSON.toJSONString(map));
        log.info("[Flow] 设置变量: instanceId={} key={}", instanceId, key);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setVariables(Long instanceId, Map<String, Object> variables) {
        // P2-24: 批量合并写入变量并持久化
        if (variables == null || variables.isEmpty()) {
            return;
        }
        FlowInstanceDO instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "流程实例不存在: " + instanceId);
        }
        Map<String, Object> map = parseVariables(instance.getVariable());
        map.putAll(variables);
        instanceMapper.updateVariable(instanceId, JSON.toJSONString(map));
        log.info("[Flow] 批量设置变量: instanceId={} keys={}", instanceId, variables.keySet());
    }

    /** 解析 variable JSON 为 Map，空值返回空 Map */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseVariables(String variable) {
        if (!StringUtils.hasText(variable)) {
            return new HashMap<>();
        }
        try {
            Map<String, Object> map = JSON.parseObject(variable, Map.class);
            return map == null ? new HashMap<>() : map;
        } catch (Exception e) {
            log.warn("[Flow] 解析 variable JSON 失败，返回空 Map: {}", e.getMessage());
            return new HashMap<>();
        }
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
                    throw new BizException(BizErrorCode.INTERNAL_ERROR,
                            "子流程启动失败: " + e.getMessage());
                }
                continue;
            }
            taskService.createTask(instanceId, node, variables);
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
            java.util.Map<String, Object> ext = com.alibaba.fastjson2.JSON.parseObject(
                    node.getExt(), java.util.Map.class);
            if (ext == null) return false;
            return ext.containsKey("callActivityFlowCode")
                    || ext.containsKey("subProcessFlowCode");
        } catch (Exception e) {
            return false;
        }
    }

    // ============================== GAP-V2-08: 流程模拟运行 ==============================

    @Override
    public List<Map<String, Object>> simulate(String flowCode, String version,
                                               Map<String, Object> variables, Long tenantId) {
        if (!StringUtils.hasText(flowCode)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "flowCode 不能为空");
        }
        // 解析租户
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        // 查询已发布流程定义
        FlowDefinitionDO def = definitionService.getPublished(flowCode, version, tid);
        if (def == null) {
            throw new BizException(BizErrorCode.NOT_FOUND,
                    "流程定义未发布: code=" + flowCode + " version=" + version);
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
            throw new BizException(BizErrorCode.BAD_REQUEST, "流程定义缺少开始节点");
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
                flowCode, def.getVersion(), result.size());
        return result;
    }

    /**
     * GAP-V2-08: 从 FlowSkipDO.ext 字段中提取源节点编码（sourceRef）
     *
     * @param skip 跳转 DO
     * @return 源节点编码，解析失败返回 null
     */
    @SuppressWarnings("unchecked")
    private String extractFromNodeCode(FlowSkipDO skip) {
        if (skip.getExt() == null || skip.getExt().isBlank()) {
            return null;
        }
        try {
            Map<String, Object> ext = JSON.parseObject(skip.getExt(), Map.class);
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

    /**
     * P2-35: 发布 Spring 异步事件（ApplicationEventPublisher 可能为 null，需检查）
     *
     * @param eventType  事件类型
     * @param instanceId 实例 ID
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
    private FlowEventContext buildContext(Long instanceId, Long taskId, Long operatorId,
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
    public Map<String, Object> getFormRenderData(Long instanceId, Long taskId) {
        FlowInstanceDO instance = instanceMapper.selectById(instanceId);
        if (instance == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "实例不存在: " + instanceId);
        }
        String nodeCode;
        String nodeName;
        String formFieldsConfig = null;
        if (taskId != null) {
            // 优先从任务获取节点信息
            FlowTaskDO task = taskMapper.selectById(taskId);
            if (task == null) {
                throw new BizException(BizErrorCode.NOT_FOUND, "任务不存在: " + taskId);
            }
            nodeCode = task.getNodeCode();
            nodeName = task.getNodeName();
        } else {
            // 回退到实例当前节点
            nodeCode = instance.getCurrentNodeCode();
            nodeName = instance.getCurrentNodeName();
        }
        // 查节点表获取 formFieldsConfig
        if (nodeCode != null) {
            FlowNodeDO node = nodeMapper.selectByCode(
                    instance.getDefinitionId(), nodeCode);
            if (node != null) {
                formFieldsConfig = node.getFormFieldsConfig();
                if (nodeName == null) {
                    nodeName = node.getNodeName();
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instanceId", instanceId);
        result.put("taskId", taskId);
        result.put("nodeCode", nodeCode);
        result.put("nodeName", nodeName);
        result.put("formFieldsConfig", formFieldsConfig);
        result.put("variables", getVariables(instanceId));
        result.put("flowStatus", instance.getFlowStatus());
        result.put("title", instance.getTitle());
        return result;
    }
}
