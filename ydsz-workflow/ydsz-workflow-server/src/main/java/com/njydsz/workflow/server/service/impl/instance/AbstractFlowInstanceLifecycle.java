package com.njydsz.workflow.server.service.impl.instance;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.feign.assembler.NameAssembler;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.lock.annotation.YdszDistributedLock;
import com.njydsz.common.security.LoginUser;
import com.njydsz.workflow.domain.dto.FlowInstanceDTO;
import com.njydsz.workflow.domain.dto.FlowStartProcessDTO;
import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.domain.enums.FlowNodeType;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.domain.repository.FlowAuditLogRepository;
import com.njydsz.workflow.domain.repository.FlowHisTaskRepository;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.repository.FlowNodeRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowAuditLogVO;
import com.njydsz.workflow.domain.vo.FlowDefinitionVO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.engine.FlowEventContext;
import com.njydsz.workflow.engine.FlowEventListener;
import com.njydsz.workflow.engine.FlowNodeExt;
import com.njydsz.workflow.engine.impl.DefaultFlowAdvancer;
import com.njydsz.workflow.server.metrics.FlowMetrics;
import com.njydsz.workflow.server.service.FlowAutoTriggerService;
import com.njydsz.workflow.server.service.FlowCcService;
import com.njydsz.workflow.server.service.FlowDefinitionService;
import com.njydsz.workflow.server.service.FlowEventSubscriptionService;
import com.njydsz.workflow.server.service.FlowSubProcessService;
import com.njydsz.workflow.server.service.FlowTaskService;
import com.njydsz.workflow.server.service.FlowTimerService;

/**
 * 流程实例生命周期抽象基类
 *
 * <p>封装流程实例核心生命周期管理的公共逻辑（启动/终止/挂起/激活/完成/撤回/回滚/重审/重做），
 * 供 {@link FlowInstanceLifecycleManager} 和 {@link FlowInstanceLifecycleService} 共享，消除重复代码。
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>启动</b>：{@link #doStartInstance} — 创建实例并推进到开始节点
 *   <li><b>终止</b>：{@link #doTerminateInstance} — 强制终止实例
 *   <li><b>挂起/激活</b>：{@link #doSuspendInstance} / {@link #doActivateInstance} — 冻结/恢复实例
 *   <li><b>完成</b>：{@link #doCompleteInstance} — 推进到结束节点
 *   <li><b>撤回</b>：{@link #doRecallInstance} — 撤回到开始节点或指定历史节点
 *   <li><b>回滚</b>：{@link #doRollbackInstance} — 撤销已完成的实例
 *   <li><b>重审</b>：{@link #doResubmitInstance} — 驳回后快速重审
 * </ul>
 *
 * <p><b>设计约定：</b>
 * <ul>
 *   <li>子类负责提供变量解析策略（{@link #parseVariables} / {@link #getVariables}）</li>
 *   <li>子类负责提供实例保存策略（{@link #saveInstance}）</li>
 *   <li>所有写方法开启 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>关键操作通过 {@link YdszDistributedLock} 注解保护</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractFlowInstanceLifecycle {

  /** 默认允许回滚的最大天数 */
  protected static final int DEFAULT_ROLLBACK_DAYS = 7;

  /** 管理员回滚权限编码 */
  protected static final String PERM_INSTANCE_ROLLBACK = "workflow:instance:rollback";

  /** 单次批量发起的最大数量限制（防止事务过多） */
  protected static final int BATCH_START_MAX_SIZE = 100;

  /** 流程实例仓储 */
  protected final FlowInstanceRepository instanceRepository;

  /** 流程定义服务 */
  protected final FlowDefinitionService definitionService;

  /** 流程推进引擎 */
  protected final DefaultFlowAdvancer advancer;

  /** 流程任务服务 */
  protected final FlowTaskService taskService;

  /** 运行时任务仓储 */
  protected final FlowRunTaskRepository taskRepository;

  /** 流程节点仓储 */
  protected final FlowNodeRepository nodeRepository;

  /** Prometheus 指标收集（可能为 null：测试环境） */
  protected final FlowMetrics flowMetrics;

  /** 事件支持组件 */
  protected final FlowTaskSupport flowTaskSupport;

  /** 子流程服务 */
  protected final FlowSubProcessService subProcessService;

  /** 抄送服务 */
  protected final FlowCcService ccService;

  /** 流程自动触发服务 */
  protected final FlowAutoTriggerService autoTriggerService;

  /** BPMN 事件订阅服务 */
  @Lazy
  protected final FlowEventSubscriptionService eventSubscriptionService;

  /** 审计日志仓储 */
  protected final FlowAuditLogRepository auditLogRepository;

  /** 历史任务仓储 */
  protected final FlowHisTaskRepository hisTaskRepository;

  /** 定时器服务 */
  @Lazy
  protected final FlowTimerService timerService;

  /** 跨服务名称解析门面 */
  protected final NameAssembler nameAssembler;

  // ============================== 子类需实现的策略方法 ==============================

  /**
   * 解析 variable JSON 为 Map
   *
   * @param variable variable JSON 字符串
   * @return 解析后的 Map
   */
  protected abstract Map<String, Object> parseVariables(String variable);

  /**
   * 读取实例流程变量
   *
   * @param instanceId 实例 ID
   * @return 变量 Map
   */
  protected abstract Map<String, Object> getVariables(String instanceId);

  /**
   * 保存流程实例
   *
   * @param instance 实例 VO
   * @return 保存后的实例 VO
   */
  protected abstract FlowInstanceVO saveInstance(FlowInstanceVO instance);

  // ============================== 公共生命周期操作（模板方法） ==============================

  /**
   * 启动流程实例
   *
   * @param dto 启动参数 DTO
   * @return 流程实例 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public String doStartInstance(FlowStartProcessDTO dto) {
    validateStartParams(dto);
    String tenantId =
        dto.getTenantId() != null ? dto.getTenantId() : AuthContextUtils.getTenantIdOrDefault();

    // 幂等：同 business 已有活跃实例则直接返回
    FlowInstanceVO existing =
        findExistingActiveInstance(tenantId, dto.getBusinessType(), dto.getBusinessId());
    if (existing != null) {
      return existing.getId();
    }

    // 查定义并构建实例
    FlowDefinitionVO def = findPublishedDefinition(dto, tenantId);
    FlowInstanceDTO instanceDto = buildInstanceDto(dto, def);
    FlowInstanceVO savedInstance = instanceRepository.save(instanceDto);
    String instanceId = savedInstance.getId();

    // 记录发起人自选审批人变量
    logSelfSelectVariables(instanceId, dto.getVariables());

    // 触发事件 + 指标
    fireEvent(l -> l.onInstanceStart(instanceId, dto.getVariables()));
    if (flowMetrics != null) {
      flowMetrics.incInstance(def.getFlowCode(), "created");
    }

    // 引擎推进
    try {
      advancer.start(instanceId);
    } catch (Exception e) {
      fireEvent(l -> l.onError(instanceId, e));
      if (flowMetrics != null) {
        flowMetrics.incError(def.getFlowCode(), "start_error");
      }
      throw e;
    }

    log.info(
        "[Flow] 启动流程: code={} bizId={} instanceId={}",
        dto.getFlowCode(),
        dto.getBusinessId(),
        instanceId);
    return instanceId;
  }

  /**
   * 终止流程实例
   *
   * @param instanceId 实例 ID
   * @param reason 终止原因
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public void doTerminateInstance(String instanceId, String reason) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    if (FlowInstanceStatus.valueOf(instance.getFlowStatus()).isFinished()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.instance.already.finished")
          .build();
    }
    LocalDateTime now = LocalDateTime.now();
    Long durationMs =
        instance.getStartAt() == null
            ? null
            : Duration.between(instance.getStartAt(), now).toMillis();
    // reason 持久化到 variable JSON
    persistTerminateReason(instanceId, instance.getVariable(), reason);
    instanceRepository.updateStatus(
        instanceId, FlowInstanceStatus.TERMINATED.name(), null, null, now, durationMs);
    taskService.cancelByInstance(instanceId, FlowTaskStatus.CANCELLED.name());
    eventSubscriptionService.cancelByInstance(instanceId, "INSTANCE_TERMINATED: " + reason);
    log.info("[Flow] 终止流程: instanceId={} reason={}", instanceId, reason);
    if (flowMetrics != null) {
      flowMetrics.incInstance(instance.getFlowCode(), "terminated");
      flowMetrics.recordInstanceDuration(instance, "TERMINATED");
    }
    fireEvent(l -> l.onInstanceTerminated(instanceId, reason));
    FlowEventContext ctx = buildContext(instanceId, null, null, "TERMINATE", instance);
    fireEvent(l -> l.onInstanceTerminated(instanceId, reason, ctx));
    publishWorkflowEvent("INSTANCE_TERMINATED", instanceId, null);
  }

  /**
   * 挂起流程实例
   *
   * @param instanceId 实例 ID
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public void doSuspendInstance(String instanceId) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    if (!FlowInstanceStatus.RUNNING.name().equals(instance.getFlowStatus())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.instance.suspend.not.running")
          .build();
    }
    instanceRepository.updateStatus(
        instanceId,
        FlowInstanceStatus.SUSPENDED.name(),
        instance.getCurrentNodeCode(),
        instance.getCurrentNodeName(),
        null,
        null);
    taskRepository.freezeByInstance(instanceId);
    log.info("[Flow] 挂起流程: instanceId={}", instanceId);
    if (flowMetrics != null) {
      flowMetrics.incInstance(instance.getFlowCode(), "suspended");
    }
    fireEvent(l -> l.onInstanceSuspended(instanceId));
    publishWorkflowEvent("INSTANCE_SUSPENDED", instanceId, null);
  }

  /**
   * 激活流程实例
   *
   * @param instanceId 实例 ID
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public void doActivateInstance(String instanceId) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    if (!FlowInstanceStatus.SUSPENDED.name().equals(instance.getFlowStatus())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.instance.activate.not.suspended")
          .build();
    }
    instanceRepository.updateStatus(
        instanceId,
        FlowInstanceStatus.RUNNING.name(),
        instance.getCurrentNodeCode(),
        instance.getCurrentNodeName(),
        null,
        null);
    taskRepository.unfreezeByInstance(instanceId);
    log.info("[Flow] 激活流程: instanceId={}", instanceId);
    if (flowMetrics != null) {
      flowMetrics.incInstance(instance.getFlowCode(), "activated");
    }
    fireEvent(l -> l.onInstanceActivated(instanceId));
    publishWorkflowEvent("INSTANCE_ACTIVATED", instanceId, null);
  }

  /**
   * 强制完成（推进到结束节点）
   *
   * @param instanceId 实例 ID
   * @param endNodeCode 终止节点编码
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public void doCompleteInstance(String instanceId, String endNodeCode) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    if (FlowInstanceStatus.valueOf(instance.getFlowStatus()).isFinished()) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    Long durationMs =
        instance.getStartAt() == null
            ? null
            : Duration.between(instance.getStartAt(), now).toMillis();
    instanceRepository.updateStatus(
        instanceId, FlowInstanceStatus.COMPLETED.name(), endNodeCode, null, now, durationMs);
    taskService.cancelByInstance(instanceId, FlowTaskStatus.SKIPPED.name());
    log.info("[Flow] 流程完成: instanceId={} endNode={}", instanceId, endNodeCode);
    if (flowMetrics != null) {
      flowMetrics.incInstance(instance.getFlowCode(), "completed");
      flowMetrics.recordInstanceDuration(instance, "COMPLETED");
    }
    fireEvent(l -> l.onInstanceCompleted(instanceId));
    publishWorkflowEvent("INSTANCE_COMPLETED", instanceId, null);
    try {
      autoTriggerService.onInstanceCompleted(instanceId);
    } catch (Exception e) {
      log.warn("[Flow] 自动触发检查失败: instanceId={} err={}", instanceId, e.getMessage());
    }
  }

  /**
   * 撤回到指定历史节点
   *
   * @param instanceId 实例 ID
   * @param initiatorId 发起人 ID
   * @param targetNodeCode 目标节点编码
   * @return 是否撤回成功
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public boolean doRecallInstance(String instanceId, String initiatorId, String targetNodeCode) {
    if (!StringUtils.hasText(targetNodeCode)) {
      return doRecallInstance(instanceId, initiatorId);
    }
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    validateRecallPermission(instance, initiatorId);
    List<FlowRunTaskVO> pendingTasks = validateNextTasksAllPending(instanceId);
    validateTargetNodeRecallable(instanceId, targetNodeCode);

    String currentNodeCode =
        pendingTasks.isEmpty() ? instance.getCurrentNodeCode() : pendingTasks.get(0).getNodeCode();
    taskService.cancelByInstance(instanceId, FlowTaskStatus.CANCELLED.name());
    advanceToTargetNode(instance, currentNodeCode, targetNodeCode);

    log.info(
        "[Flow] 撤回流程到指定节点: instanceId={} initiatorId={} targetNodeCode={}",
        instanceId,
        initiatorId,
        targetNodeCode);
    fireRecallEvents(instance);
    return true;
  }

  /**
   * 撤回流程（回退到开始节点的下一节点）
   *
   * @param instanceId 实例 ID
   * @param initiatorId 发起人 ID
   * @return 是否撤回成功
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public boolean doRecallInstance(String instanceId, String initiatorId) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    validateRecallPermission(instance, initiatorId);
    validateNextTasksAllPending(instanceId);

    taskService.cancelByInstance(instanceId, FlowTaskStatus.CANCELLED.name());
    try {
      advancer.start(instanceId);
    } catch (Exception e) {
      log.error("[Flow] 撤回后重新推进失败: instanceId={}", instanceId, e);
      throw SysException.builder()
          .resultCode(YdszResultCode.INTERNAL_ERROR)
          .key("error.workflow.instance.recall.failed")
          .params(e.getMessage())
          .build();
    }
    log.info("[Flow] 撤回流程: instanceId={} initiatorId={}", instanceId, initiatorId);
    fireRecallEvents(instance);
    return true;
  }

  /**
   * 回滚已完成的流程实例
   *
   * @param instanceId 实例 ID
   * @param operatorId 操作人 ID
   * @param reason 回滚原因
   * @param maxRollbackDays 允许回滚的最大天数
   * @return 是否回滚成功
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public boolean doRollbackInstance(
      String instanceId, String operatorId, String reason, int maxRollbackDays) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);

    if (!FlowInstanceStatus.COMPLETED.name().equals(instance.getFlowStatus())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.instance.not.completed")
          .params(instance.getFlowStatus())
          .build();
    }

    boolean isInitiator =
        instance.getInitiatorId() != null && instance.getInitiatorId().equals(operatorId);
    boolean isAdmin = false;
    LoginUser user = AuthContextUtils.getCurrentOrNull();
    if (user != null) {
      isAdmin = user.isSuperAdmin() || user.hasPermission(PERM_INSTANCE_ROLLBACK);
    }
    if (!isInitiator && !isAdmin) {
      throw SysException.builder()
          .resultCode(YdszResultCode.FORBIDDEN)
          .message("error.workflow.instance.rollback.no.permission")
          .build();
    }

    if (!StringUtils.hasText(reason)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.instance.rollback.reason.required")
          .build();
    }

    int days = maxRollbackDays > 0 ? maxRollbackDays : DEFAULT_ROLLBACK_DAYS;
    if (instance.getEndAt() != null) {
      long elapsedDays = Duration.between(instance.getEndAt(), LocalDateTime.now()).toDays();
      if (elapsedDays > days) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .key("error.workflow.instance.rollback.time.exceeded")
            .params(days)
            .build();
      }
    }

    LocalDateTime now = LocalDateTime.now();
    Long durationMs =
        instance.getStartAt() == null
            ? null
            : Duration.between(instance.getStartAt(), now).toMillis();
    instanceRepository.updateStatus(
        instanceId,
        FlowInstanceStatus.ROLLED_BACK.name(),
        instance.getCurrentNodeCode(),
        instance.getCurrentNodeName(),
        now,
        durationMs);

    try {
      Map<String, Object> vars = parseVariables(instance.getVariable());
      Map<String, Object> rollbackInfo = new LinkedHashMap<>();
      rollbackInfo.put("operatorId", operatorId);
      rollbackInfo.put("reason", reason);
      rollbackInfo.put("rolledBackAt", now.toString());
      rollbackInfo.put("byAdmin", isAdmin && !isInitiator);
      vars.put("_rollback", rollbackInfo);
      instanceRepository.updateVariable(instanceId, YdszJson.toJson(vars));
    } catch (Exception e) {
      log.warn("[Flow] 回滚元信息持久化失败: instanceId={} err={}", instanceId, e.getMessage());
    }

    log.info(
        "[Flow] 回滚流程: instanceId={} operatorId={} reason={} isAdmin={}",
        instanceId,
        operatorId,
        reason,
        isAdmin && !isInitiator);

    if (flowMetrics != null) {
      flowMetrics.incInstance(instance.getFlowCode(), "recalled");
    }
    fireEvent(l -> l.onInstanceRolledBack(instanceId, operatorId, reason));
    publishWorkflowEvent("INSTANCE_ROLLED_BACK", instanceId, null);
    return true;
  }

  /**
   * 驳回后快速重审
   *
   * @param instanceId 被驳回的实例 ID
   * @param initiatorId 发起人 ID
   * @param variables 重审时新增/覆盖的变量
   * @param comment 重审说明
   * @return 实例 ID
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public String doResubmitInstance(
      String instanceId, String initiatorId, Map<String, Object> variables, String comment) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    FlowInstanceStatus status = FlowInstanceStatus.valueOf(instance.getFlowStatus());
    if (status != FlowInstanceStatus.REJECTED) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.instance.resubmit.not.rejected")
          .params("仅被驳回实例可重审，当前状态=" + instance.getFlowStatus())
          .build();
    }
    if (instance.getInitiatorId() != null
        && !String.valueOf(instance.getInitiatorId()).equals(initiatorId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.FORBIDDEN)
          .key("error.workflow.instance.resubmit.not.initiator")
          .params("仅发起人可重审")
          .build();
    }

    Map<String, Object> merged = getVariables(instanceId);
    if (merged == null) {
      merged = new HashMap<>();
    }
    if (variables != null && !variables.isEmpty()) {
      merged.putAll(variables);
    }
    instance.setFlowStatus(FlowInstanceStatus.RUNNING.name());
    instance.setActivityStatus(1);
    instance.setCurrentNodeCode(null);
    instance.setCurrentNodeName(null);
    instance.setStartAt(LocalDateTime.now());
    instance.setEndAt(null);
    instance.setRejectReason(null);
    instance.setVariable(merged.isEmpty() ? null : YdszJson.toJson(merged));
    saveInstance(instance);

    FlowAuditLogVO audit = new FlowAuditLogVO();
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
    auditLogRepository.save(audit);

    try {
      advancer.start(instanceId);
    } catch (Exception e) {
      fireEvent(l -> l.onError(instanceId, e));
      throw e;
    }
    log.info("[Flow] 驳回后快速重审: instanceId={} initiatorId={}", instanceId, initiatorId);
    return instanceId;
  }

  /**
   * 流程重做 — 支持 redoMode 指定重做策略
   *
   * @param instanceId 原实例 ID
   * @param initiatorId 发起人 ID
   * @param variables 重做时新增/覆盖的变量
   * @param comment 重做说明
   * @param redoMode 重做模式：RESTART / NEW_INSTANCE
   * @return 实例 ID
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public String doResubmitInstance(
      String instanceId,
      String initiatorId,
      Map<String, Object> variables,
      String comment,
      String redoMode) {
    String mode = (redoMode == null || redoMode.isBlank()) ? "RESTART" : redoMode.toUpperCase();
    if ("NEW_INSTANCE".equals(mode)) {
      return doResubmitAsNewInstance(instanceId, initiatorId, variables, comment);
    }
    return doResubmitInstance(instanceId, initiatorId, variables, comment);
  }

  /**
   * 设置实例的 dueAt 字段
   *
   * @param instanceId 实例 ID
   * @param dueAt 超时时间
   */
  @Transactional(rollbackFor = Exception.class)
  public void doSetDueAt(String instanceId, LocalDateTime dueAt) {
    instanceRepository.updateDueAt(instanceId, dueAt);
    log.info("[Flow] 设置实例到期时间: instanceId={} dueAt={}", instanceId, dueAt);
  }

  // ============================== 内部方法 ==============================

  /**
   * 创建第一个待办任务
   *
   * @param instanceId 实例 ID
   * @param startNode 开始节点
   * @param variables 流程变量
   * @return 实例 ID
   */
  public String createFirstTask(
      String instanceId, FlowNodeVO startNode, Map<String, Object> variables) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    List<FlowNodeVO> nextNodes =
        advancer.advance(instance, startNode.getNodeCode(), "PASS", null, variables);
    if (nextNodes.isEmpty()) {
      log.warn("[Flow] 流程无下游节点: instanceId={}", instanceId);
      doCompleteInstance(instanceId, startNode.getNodeCode());
      return null;
    }
    for (FlowNodeVO node : nextNodes) {
      taskService.createTask(instanceId, node, variables);
    }
    instanceRepository.updateStatus(
        instanceId,
        instance.getFlowStatus(),
        nextNodes.get(0).getNodeCode(),
        nextNodes.get(0).getNodeName(),
        null,
        null);
    return instanceId;
  }

  /**
   * 推进后批量生成任务
   *
   * @param instanceId 实例 ID
   * @param nextNodes 下一批节点
   * @param variables 流程变量
   */
  public void generateTasksForNodes(
      String instanceId, List<FlowNodeVO> nextNodes, Map<String, Object> variables) {
    if (nextNodes == null || nextNodes.isEmpty()) {
      return;
    }
    for (FlowNodeVO node : nextNodes) {
      if (eventSubscriptionService.isEventCatchNode(node)) {
        String boundaryTaskId = resolveBoundaryTaskId(node, instanceId);
        eventSubscriptionService.createSubscription(instanceId, node, variables, boundaryTaskId);
        scheduleBoundaryTimerIfPresent(node, instanceId, boundaryTaskId);
        instanceRepository.updateStatus(
            instanceId, null, node.getNodeCode(), node.getNodeName(), null, null);
        log.info(
            "[Flow] 事件捕获节点等待触发: instanceId={} node={} type={}",
            instanceId,
            node.getNodeCode(),
            node.getNodeType());
        continue;
      }
      if (node.getNodeType().equals(FlowNodeType.CC.getCode())) {
        try {
          ccService.handleCcNode(instanceId, node, variables);
          log.info("[Flow] 抄送节点处理完成: instanceId={} node={}", instanceId, node.getNodeCode());
        } catch (Exception e) {
          log.warn(
              "[Flow] 抄送节点处理失败，跳过继续: instanceId={} node={} err={}",
              instanceId,
              node.getNodeCode(),
              e.getMessage());
        }
        FlowInstanceVO ccInstance = instanceRepository.findById(instanceId).orElse(null);
        if (ccInstance != null) {
          List<FlowNodeVO> ccNext =
              advancer.advance(ccInstance, node.getNodeCode(), "PASS", null, variables);
          if (!ccNext.isEmpty()) {
            generateTasksForNodes(instanceId, ccNext, variables);
          }
        }
        continue;
      }
      if (node.getNodeType().equals(FlowNodeType.END.getCode())) {
        doCompleteInstance(instanceId, node.getNodeCode());
        return;
      }
      if (node.getNodeType().equals(FlowNodeType.SUBPROCESS.getCode()) || isCallActivity(node)) {
        try {
          FlowInstanceVO instance = instanceRepository.findById(instanceId).orElse(null);
          subProcessService.startSubProcess(instance, node, variables);
          instanceRepository.updateStatus(
              instanceId,
              instance.getFlowStatus(),
              node.getNodeCode(),
              node.getNodeName(),
              null,
              null);
          log.info(
              "[Flow] callActivity 触发子流程: instanceId={} node={}", instanceId, node.getNodeCode());
        } catch (Exception e) {
          log.error(
              "[Flow] callActivity 启动子流程失败: instanceId={} node={} err={}",
              instanceId,
              node.getNodeCode(),
              e.getMessage(),
              e);
          throw SysException.builder()
              .resultCode(YdszResultCode.INTERNAL_ERROR)
              .key("error.workflow.instance.subprocess.start.failed")
              .params(e.getMessage())
              .build();
        }
        continue;
      }
      taskService.createTask(instanceId, node, variables);
    }
  }

  // ============================== 私有辅助方法 ==============================

  protected void validateStartParams(FlowStartProcessDTO dto) {
    if (dto == null
        || !StringUtils.hasText(dto.getFlowCode())
        || !StringUtils.hasText(dto.getBusinessType())
        || !StringUtils.hasText(dto.getBusinessId())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.instance.start.params.invalid")
          .build();
    }
  }

  protected FlowInstanceVO findExistingActiveInstance(
      String tenantId, String businessType, String businessId) {
    FlowInstanceVO existing =
        instanceRepository.findByBusiness(tenantId, businessType, businessId).orElse(null);
    if (existing != null) {
      log.info(
          "[Flow] 实例已存在: businessType={} businessId={} id={} status={}",
          businessType,
          businessId,
          existing.getId(),
          existing.getFlowStatus());
    }
    return existing;
  }

  protected FlowDefinitionVO findPublishedDefinition(FlowStartProcessDTO dto, String tenantId) {
    FlowDefinitionVO def =
        definitionService.getPublished(
            dto.getFlowCode(),
            StringUtils.hasText(dto.getVersion()) ? dto.getVersion() : null,
            tenantId);
    if (def == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.instance.definition.not.found")
          .params(dto.getFlowCode())
          .build();
    }
    return def;
  }

  protected FlowInstanceDTO buildInstanceDto(FlowStartProcessDTO dto, FlowDefinitionVO def) {
    FlowInstanceDTO instanceDto = new FlowInstanceDTO();
    instanceDto.setFlowCode(def.getFlowCode());
    instanceDto.setFlowName(def.getFlowName());
    instanceDto.setDefinitionId(def.getId());
    instanceDto.setFlowVersion(def.getFlowVersion());
    instanceDto.setBusinessType(dto.getBusinessType());
    instanceDto.setBusinessId(dto.getBusinessId());
    instanceDto.setBusinessNo(dto.getBusinessNo());
    instanceDto.setTitle(
        dto.getTitle() == null ? def.getFlowName() + "-" + dto.getBusinessId() : dto.getTitle());
    instanceDto.setInitiatorId(dto.getInitiatorId());
    instanceDto.setInitiatorName(dto.getInitiatorName());
    instanceDto.setFlowStatus(FlowInstanceStatus.RUNNING.name());
    instanceDto.setActivityStatus(1);
    instanceDto.setStartAt(LocalDateTime.now());
    instanceDto.setVariable(buildInstanceVariables(dto));
    instanceDto.setProviderTraceId(dto.getProviderTraceId());
    instanceDto.setParentInstanceId(dto.getParentInstanceId());
    instanceDto.setParentNodeCode(dto.getParentNodeCode());
    return instanceDto;
  }

  protected String buildInstanceVariables(FlowStartProcessDTO dto) {
    Map<String, Object> mergedVars =
        dto.getVariables() == null ? new HashMap<>() : new HashMap<>(dto.getVariables());
    if (dto.getNodeAssignees() != null && !dto.getNodeAssignees().isEmpty()) {
      for (Map.Entry<String, List<Long>> entry : dto.getNodeAssignees().entrySet()) {
        mergedVars.put("_selfSelect_" + entry.getKey(), entry.getValue());
      }
    }
    return mergedVars.isEmpty() ? null : YdszJson.toJson(mergedVars);
  }

  protected void logSelfSelectVariables(String instanceId, Map<String, Object> variables) {
    if (variables == null) {
      return;
    }
    for (Map.Entry<String, Object> entry : variables.entrySet()) {
      if (entry.getKey() != null && entry.getKey().startsWith("_selfSelect_")) {
        log.info(
            "[Flow] 发起人自选审批人变量: instanceId={} key={} value={}",
            instanceId,
            entry.getKey(),
            entry.getValue());
      }
    }
  }

  protected void persistTerminateReason(String instanceId, String var, String reason) {
    if (StringUtils.hasText(reason)) {
      try {
        Map<String, Object> m = parseVariables(var);
        m.put("_terminateReason", reason);
        instanceRepository.updateVariable(instanceId, YdszJson.toJson(m));
      } catch (Exception e) {
        log.warn(
            "[Flow] terminate reason 持久化失败: instanceId={} reason={}", instanceId, e.getMessage());
      }
    }
  }

  protected void validateRecallPermission(FlowInstanceVO instance, String initiatorId) {
    if (!instance.getInitiatorId().equals(initiatorId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.FORBIDDEN)
          .message("error.workflow.instance.recall.not.initiator")
          .build();
    }
    if (!FlowInstanceStatus.RUNNING.name().equals(instance.getFlowStatus())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.instance.recall.not.running")
          .build();
    }
  }

  protected List<FlowRunTaskVO> validateNextTasksAllPending(String instanceId) {
    List<FlowRunTaskVO> pendingTasks = taskRepository.findPendingByInstance(instanceId);
    boolean anyProcessed =
        pendingTasks.stream()
            .anyMatch(
                t ->
                    FlowTaskStatus.CLAIMED.name().equals(t.getTaskStatus())
                        || FlowTaskStatus.COMPLETED.name().equals(t.getTaskStatus()));
    if (anyProcessed) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.instance.recall.next.processed")
          .build();
    }
    return pendingTasks;
  }

  protected void validateTargetNodeRecallable(String instanceId, String targetNodeCode) {
    List<Map<String, Object>> recallable = hisTaskRepository.listPassedNodes(instanceId);
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
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.instance.recall.target.invalid")
          .params(targetNodeCode)
          .build();
    }
  }

  protected void advanceToTargetNode(
      FlowInstanceVO instance, String currentNodeCode, String targetNodeCode) {
    Map<String, Object> variables = parseVariables(instance.getVariable());
    try {
      advancer.advance(instance, currentNodeCode, "REJECT", targetNodeCode, variables);
    } catch (Exception e) {
      log.error(
          "[Flow] 撤回到指定节点失败: instanceId={} targetNodeCode={}",
          instance.getId(),
          targetNodeCode,
          e);
      throw SysException.builder()
          .resultCode(YdszResultCode.INTERNAL_ERROR)
          .key("error.workflow.instance.recall.failed")
          .params(e.getMessage())
          .build();
    }
  }

  protected void fireRecallEvents(FlowInstanceVO instance) {
    if (flowMetrics != null) {
      flowMetrics.incInstance(instance.getFlowCode(), "recalled");
    }
    fireEvent(l -> l.onInstanceRecalled(instance.getId(), instance.getInitiatorId()));
    publishWorkflowEvent("INSTANCE_RECALLED", instance.getId(), null);
  }

  protected String doResubmitAsNewInstance(
      String instanceId, String initiatorId, Map<String, Object> variables, String comment) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    FlowInstanceStatus status = FlowInstanceStatus.valueOf(instance.getFlowStatus());
    if (status == FlowInstanceStatus.RUNNING || status == FlowInstanceStatus.SUSPENDED) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.instance.appendnode.not.running")
          .params("运行中/挂起的实例不可重做，当前状态=" + instance.getFlowStatus())
          .build();
    }
    if (instance.getInitiatorId() != null
        && !String.valueOf(instance.getInitiatorId()).equals(initiatorId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.FORBIDDEN)
          .key("error.workflow.instance.resubmit.not.initiator")
          .params("仅发起人可重做")
          .build();
    }

    Map<String, Object> merged = getVariables(instanceId);
    if (merged == null) {
      merged = new HashMap<>();
    }
    if (variables != null && !variables.isEmpty()) {
      merged.putAll(variables);
    }
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
    String newInstanceId = doStartInstance(dto);

    FlowAuditLogVO audit = new FlowAuditLogVO();
    audit.setInstanceId(instanceId);
    audit.setFlowCode(instance.getFlowCode());
    audit.setBusinessType(instance.getBusinessType());
    audit.setBusinessId(instance.getBusinessId());
    audit.setAction("REDO_NEW_INSTANCE");
    audit.setOperatorId(initiatorId);
    audit.setOperatorName(instance.getInitiatorName());
    String redoComment =
        comment != null && !comment.isBlank()
            ? comment + " → 新实例[" + newInstanceId + "]"
            : "重做为新实例[" + newInstanceId + "]";
    audit.setComment(redoComment);
    audit.setTenantId(instance.getTenantId());
    audit.setProviderTraceId(instance.getProviderTraceId());
    audit.setOperatedAt(LocalDateTime.now());
    auditLogRepository.save(audit);
    log.info(
        "[Flow] 重做为新实例: 原实例={} 新实例={} initiatorId={}",
        instanceId,
        newInstanceId,
        initiatorId);
    return newInstanceId;
  }

  protected FlowInstanceVO getByIdOrThrow(String id) {
    FlowInstanceVO instance = instanceRepository.findById(id).orElse(null);
    if (instance == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.instance.not.found")
          .params(id)
          .build();
    }
    return instance;
  }

  protected void scheduleBoundaryTimerIfPresent(
      FlowNodeVO node, String instanceId, String boundaryTaskId) {
    if (timerService == null || boundaryTaskId == null) {
      return;
    }
    Map<String, Object> ext = parseExtMap(node);
    if (ext == null) {
      return;
    }
    Object timerObj = ext.get("timer");
    if (!(timerObj instanceof Map<?, ?> timerRaw)) {
      return;
    }
    Duration delay = parseTimerDelay(timerRaw);
    if (delay == null || delay.isNegative() || delay.isZero()) {
      log.warn(
          "[Flow] 边界定时器配置无法解析或已过期，跳过: node={} timer={}",
          node.getNodeCode(),
          timerRaw);
      return;
    }
    try {
      timerService.scheduleBoundary(boundaryTaskId, instanceId, node.getNodeCode(), delay);
      log.info(
          "[Flow] 边界定时器已注册: instanceId={} node={} delay={} taskId={}",
          instanceId,
          node.getNodeCode(),
          delay,
          boundaryTaskId);
    } catch (Exception e) {
      log.warn(
          "[Flow] 边界定时器注册失败: instanceId={} node={} err={}",
          instanceId,
          node.getNodeCode(),
          e.getMessage());
    }
  }

  protected Duration parseTimerDelay(Map<?, ?> timer) {
    Object duration = timer.get("duration");
    if (duration != null) {
      try {
        return Duration.parse(duration.toString());
      } catch (Exception e) {
        log.warn("[Flow] timer.duration 解析失败: {} err={}", duration, e.getMessage());
      }
    }
    Object date = timer.get("date");
    if (date != null) {
      try {
        LocalDateTime target =
            LocalDateTime.parse(date.toString(), DateTimeFormatter.ISO_DATE_TIME);
        Duration d = Duration.between(LocalDateTime.now(), target);
        return d.isNegative() ? null : d;
      } catch (Exception e) {
        log.warn("[Flow] timer.date 解析失败: {} err={}", date, e.getMessage());
      }
    }
    Object cycle = timer.get("cycle");
    if (cycle != null) {
      String cycleStr = cycle.toString();
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

  protected Map<String, Object> parseExtMap(FlowNodeVO node) {
    if (node == null || !StringUtils.hasText(node.getExt())) {
      return Collections.emptyMap();
    }
    try {
      return FlowNodeExt.parseSafe(node.getExt());
    } catch (Exception e) {
      log.warn("[Flow] 节点 ext 解析失败: nodeCode={} err={}", node.getNodeCode(), e.getMessage());
      return Collections.emptyMap();
    }
  }

  protected String resolveBoundaryTaskId(FlowNodeVO node, String instanceId) {
    if (node == null || !StringUtils.hasText(node.getExt())) {
      return null;
    }
    try {
      Map<String, Object> ext = FlowNodeExt.parseSafe(node.getExt());
      if (ext == null) {
        return null;
      }
      String attachedToRef = (String) ext.get("attachedToRef");
      if (!StringUtils.hasText(attachedToRef)) {
        return null;
      }
      List<FlowRunTaskVO> tasks = taskRepository.findPendingByNode(instanceId, attachedToRef);
      return tasks.isEmpty() ? null : tasks.get(0).getId();
    } catch (Exception e) {
      log.warn(
          "[Flow] 解析 boundaryTaskId 失败: nodeCode={} err={}", node.getNodeCode(), e.getMessage());
      return null;
    }
  }

  protected boolean isCallActivity(FlowNodeVO node) {
    if (node == null || !StringUtils.hasText(node.getExt())) {
      return false;
    }
    try {
      Map<String, Object> ext = FlowNodeExt.parseSafe(node.getExt());
      if (ext == null) {
        return false;
      }
      return ext.containsKey("callActivityFlowCode") || ext.containsKey("subProcessFlowCode");
    } catch (Exception e) {
      log.warn("[Flow] 节点 ext 解析失败，视为非子流程调用: {}", e.getMessage());
      return false;
    }
  }

  protected void fireEvent(Consumer<FlowEventListener> action) {
    flowTaskSupport.fireEvent(action, null);
  }

  protected void publishWorkflowEvent(String eventType, String instanceId, String taskId) {
    flowTaskSupport.publishWorkflowEvent(eventType, instanceId, taskId);
  }

  protected FlowEventContext buildContext(
      String instanceId,
      String taskId,
      String operatorId,
      String action,
      FlowInstanceVO instance) {
    FlowEventContext ctx = new FlowEventContext();
    ctx.setInstanceId(instanceId);
    ctx.setTaskId(taskId);
    ctx.setOperatorId(operatorId);
    ctx.setAction(action);
    ctx.setOperatedAt(LocalDateTime.now());
    if (instance != null) {
      ctx.setTenantId(
          instance.getTenantId() == null ? null : String.valueOf(instance.getTenantId()));
      String traceId = instance.getProviderTraceId();
      if (traceId == null || traceId.isBlank()) {
        traceId = RequestContext.getTraceId();
        if (traceId == null || traceId.isBlank()) {
          traceId = MDC.get("traceId");
          if (traceId == null) {
            traceId = MDC.get("tid");
          }
        }
      }
      ctx.setTraceId(traceId);
    }
    return ctx;
  }

  protected static FlowInstanceDTO toDto(FlowInstanceVO vo) {
    FlowInstanceDTO dto = new FlowInstanceDTO();
    dto.setId(vo.getId());
    dto.setFlowCode(vo.getFlowCode());
    dto.setFlowName(vo.getFlowName());
    dto.setDefinitionId(vo.getDefinitionId());
    dto.setFlowVersion(vo.getFlowVersion());
    dto.setBusinessType(vo.getBusinessType());
    dto.setBusinessId(vo.getBusinessId());
    dto.setBusinessNo(vo.getBusinessNo());
    dto.setTitle(vo.getTitle());
    dto.setInitiatorId(vo.getInitiatorId());
    dto.setInitiatorName(vo.getInitiatorName());
    dto.setCurrentNodeCode(vo.getCurrentNodeCode());
    dto.setCurrentNodeName(vo.getCurrentNodeName());
    dto.setVariable(vo.getVariable());
    dto.setFlowStatus(vo.getFlowStatus());
    dto.setActivityStatus(vo.getActivityStatus());
    dto.setStartAt(vo.getStartAt());
    dto.setEndAt(vo.getEndAt());
    dto.setDurationMs(vo.getDurationMs());
    dto.setParentInstanceId(vo.getParentInstanceId());
    dto.setParentNodeCode(vo.getParentNodeCode());
    dto.setProviderTraceId(vo.getProviderTraceId());
    dto.setDueAt(vo.getDueAt());
    dto.setRejectReason(vo.getRejectReason());
    return dto;
  }
}
