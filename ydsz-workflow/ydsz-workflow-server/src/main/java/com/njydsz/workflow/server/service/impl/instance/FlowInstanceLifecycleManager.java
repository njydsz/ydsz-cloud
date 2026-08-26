package com.njydsz.workflow.server.service.impl.instance;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
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
import com.njydsz.workflow.server.engine.FlowEventContext;
import com.njydsz.workflow.server.engine.FlowEventListener;
import com.njydsz.workflow.server.engine.FlowNodeExt;
import com.njydsz.workflow.server.engine.impl.DefaultFlowAdvancer;
import com.njydsz.workflow.server.metrics.FlowMetrics;
import com.njydsz.workflow.server.service.FlowAutoTriggerService;
import com.njydsz.workflow.server.service.FlowCcService;
import com.njydsz.workflow.server.service.FlowDefinitionService;
import com.njydsz.workflow.server.service.FlowEventSubscriptionService;
import com.njydsz.workflow.server.service.FlowSubProcessService;
import com.njydsz.workflow.server.service.FlowTaskService;
import com.njydsz.workflow.server.service.FlowTimerService;

/**
 * 流程实例生命周期管理器
 *
 * <p>负责流程实例的完整生命周期管理，包含所有<b>写操作</b>（带 {@code @Transactional}）。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>启动</b>：{@link #start} — 创建实例并推进到开始节点
 *   <li><b>终止</b>：{@link #terminate} — 强制终止实例
 *   <li><b>挂起/激活</b>：{@link #suspend} / {@link #activate} — 冻结/恢复实例
 *   <li><b>完成</b>：{@link #complete} — 推进到结束节点
 *   <li><b>撤回</b>：{@link #recall} — 撤回到开始节点或指定历史节点
 *   <li><b>回滚</b>：{@link #rollback} — 撤销已完成的实例
 *   <li><b>重审</b>：{@link #resubmit} — 驳回后快速重审
 *   <li><b>内部任务生成</b>：{@link #createFirstTask} / {@link #generateTasksForNodes}
 * </ul>
 *
 * <p><b>事务边界：</b>所有写方法开启 {@code @Transactional(rollbackFor = Exception.class)}， 确保「实例 + 任务 + 审计日志 + 事件」原子性。
 *
 * <p><b>并发控制：</b>关键操作通过 {@link YdszDistributedLock} 注解保护。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowInstanceLifecycleManager {

  /** P2-3: 默认允许回滚的最大天数 */
  private static final int DEFAULT_ROLLBACK_DAYS = 7;

  /** P2-3: 管理员回滚权限编码 */
  private static final String PERM_INSTANCE_ROLLBACK = "workflow:instance:rollback";

  /** P3-1: 管理员重审权限编码 */
  private static final String PERM_INSTANCE_REOPEN = "workflow:instance:reopen";

  /** P2-6: 单次批量发起的最大数量限制（防止事务过多） */
  private static final int BATCH_START_MAX_SIZE = 100;

  /** 流程实例仓储，负责 ydsz_flow_instance 的领域持久化 */
  private final FlowInstanceRepository instanceRepository;

  /** 流程定义服务，启动实例时解析流程定义节点和跳转 */
  private final FlowDefinitionService definitionService;

  /** 流程推进引擎，负责节点推进/跳转/网关条件求值 */
  private final DefaultFlowAdvancer advancer;

  /** 流程任务服务，创建/推进/终止任务 */
  private final FlowTaskService taskService;

  /** 运行时任务仓储，负责 ydsz_flow_run_task 的领域持久化 */
  private final FlowRunTaskRepository taskRepository;

  /** 流程节点仓储，负责 ydsz_flow_node 的领域持久化 */
  private final FlowNodeRepository nodeRepository;

  /** P2-3: Prometheus 指标收集（可能为 null：测试环境） */
  private final FlowMetrics flowMetrics;

  /** P2-3: 事件支持组件，统一处理事件监听器调用与 Spring 事件发布 */
  private final FlowTaskSupport flowTaskSupport;

  /** P1-3: 子流程服务（处理 callActivity 子流程启动） */
  private final FlowSubProcessService subProcessService;

  /** GAP-P1: 抄送服务（CC 节点处理） */
  private final FlowCcService ccService;

  /** 流程自动触发服务（实例完成时检查是否需要自动发起下一流程） */
  private final FlowAutoTriggerService autoTriggerService;

  /**
   * P0-1: BPMN 事件订阅服务 — 流程推进到事件捕获节点时创建订阅
   *
   * <p>使用 @Lazy 避免循环依赖：FlowEventSubscriptionServiceImpl → DefaultFlowAdvancer → FlowInstanceService →
   * FlowEventSubscriptionService
   */
  @Lazy
  private final FlowEventSubscriptionService eventSubscriptionService;

  /** 审计日志仓储，负责 ydsz_flow_audit_log 的领域持久化 */
  private final FlowAuditLogRepository auditLogRepository;

  /** 历史任务仓储，负责 ydsz_flow_his_task 的领域持久化 */
  private final FlowHisTaskRepository hisTaskRepository;

  /**
   * P0-2: 定时器服务 — boundaryEvent 含 timer 配置时注册边界定时器自动触发
   *
   * <p>使用 @Lazy 避免循环依赖：FlowTimerServiceImpl → DefaultFlowAdvancer → FlowInstanceService → FlowTimerService
   */
  @Lazy
  private final FlowTimerService timerService;

  /**
   * P0-4: 跨服务名称解析门面，用于读路径兜底富化 initiatorName。
   */
  private final NameAssembler nameAssembler;

  /** 流程变量管理器，负责变量读写与解析 */
  private final FlowInstanceVariableManager variableManager;

  /**
   * 启动流程实例
   *
   * <p>完整执行链路：
   *
   * <ol>
   *   <li><b>参数校验</b>：{@code flowCode / businessType / businessId} 必填
   *   <li><b>幂等检查</b>：按 {@code (tenantId, businessType, businessId)} 查已存在活跃实例，存在则直接返回其 ID（防重）
   *   <li><b>定义解析</b>：通过 {@link FlowDefinitionService#getPublished} 查询最新已发布流程定义
   *   <li><b>变量策略</b>：合并发起人自选审批人变量
   *   <li><b>实例落库</b>：{@code ydsz_flow_instance} 写入，{@code flowStatus=RUNNING}
   *<li><b>推进到开始节点</b>：通过 {@link com.njydsz.workflow.server.engine.impl.DefaultFlowAdvancer} 计算下一节点并创建首个待办任务
   *   <li><b>事件触发</b>：发布 {@code onInstanceStart} 事件
   * </ol>
   *
   * @param dto 启动参数 DTO（含 {@code flowCode/businessType/businessId/variables/initiatorId}）
   * @return 流程实例 ID（新建或已存在）
   * @throws SysException {@code BAD_REQUEST} — 参数缺失；{@code NOT_FOUND} — 流程定义未找到
   */
  @Transactional(rollbackFor = Exception.class)
  public String start(FlowStartProcessDTO dto) {
    validateStartParams(dto);
    String tenantId = dto.getTenantId() != null ? dto.getTenantId() : AuthContextUtils.getTenantIdOrDefault();

    // 幂等：同 business 已有活跃实例则直接返回
    FlowInstanceVO existing = findExistingActiveInstance(tenantId, dto.getBusinessType(), dto.getBusinessId());
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

    log.info("[Flow] 启动流程: code={} bizId={} instanceId={}", dto.getFlowCode(), dto.getBusinessId(), instanceId);
    return instanceId;
  }

  /**
   * 校验发起流程参数。
   *
   * @param dto 参数说明
   */
  private void validateStartParams(FlowStartProcessDTO dto) {
    if (dto == null || !StringUtils.hasText(dto.getFlowCode())
        || !StringUtils.hasText(dto.getBusinessType()) || !StringUtils.hasText(dto.getBusinessId())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_208e3c66")
          .build();
    }
  }

  /**
   * 查找已存在的活跃实例（RUNNING/SUSPENDED），不存在返回 null。
   *
   * @param tenantId 参数说明
   * @param businessType 参数说明
   * @param businessId 参数说明
   * @return 返回值说明
   */
  private FlowInstanceVO findExistingActiveInstance(String tenantId, String businessType, String businessId) {
    FlowInstanceVO existing = instanceRepository.findByBusiness(tenantId, businessType, businessId).orElse(null);
    if (existing != null) {
      log.info("[Flow] 实例已存在: businessType={} businessId={} id={} status={}",
          businessType, businessId, existing.getId(), existing.getFlowStatus());
    }
    return existing;
  }

  /**
   * 查找最新已发布流程定义，不存在则抛出 NOT_FOUND。
   *
   * @param dto 参数说明
   * @param tenantId 参数说明
   * @return 返回值说明
   */
  private FlowDefinitionVO findPublishedDefinition(FlowStartProcessDTO dto, String tenantId) {
    FlowDefinitionVO def = definitionService.getPublished(dto.getFlowCode(),
        StringUtils.hasText(dto.getVersion()) ? dto.getVersion() : null, tenantId);
    if (def == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.msg_add8d012")
          .params(dto.getFlowCode())
          .build();
    }
    return def;
  }

  /**
   * 构建流程实例 DTO。
   *
   * @param dto 参数说明
   * @param def 参数说明
   * @return 返回值说明
   */
  private FlowInstanceDTO buildInstanceDto(FlowStartProcessDTO dto, FlowDefinitionVO def) {
    FlowInstanceDTO instanceDto = new FlowInstanceDTO();
    instanceDto.setFlowCode(def.getFlowCode());
    instanceDto.setFlowName(def.getFlowName());
    instanceDto.setDefinitionId(def.getId());
    instanceDto.setFlowVersion(def.getFlowVersion());
    instanceDto.setBusinessType(dto.getBusinessType());
    instanceDto.setBusinessId(dto.getBusinessId());
    instanceDto.setBusinessNo(dto.getBusinessNo());
    instanceDto.setTitle(dto.getTitle() == null ? def.getFlowName() + "-" + dto.getBusinessId() : dto.getTitle());
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

  /**
   * 构建流程实例变量（含发起人自选审批人）。
   *
   * @param dto 参数说明
   * @return 返回值说明
   */
  private String buildInstanceVariables(FlowStartProcessDTO dto) {
    Map<String, Object> mergedVars = dto.getVariables() == null ? new HashMap<>() : new HashMap<>(dto.getVariables());
    if (dto.getNodeAssignees() != null && !dto.getNodeAssignees().isEmpty()) {
      for (Map.Entry<String, List<Long>> entry : dto.getNodeAssignees().entrySet()) {
        mergedVars.put("_selfSelect_" + entry.getKey(), entry.getValue());
      }
    }
    return mergedVars.isEmpty() ? null : YdszJson.toJson(mergedVars);
  }

  /**
   * 记录发起人自选审批人变量日志。
   *
   * @param instanceId 参数说明
   * @param variables 参数说明
   */
  private void logSelfSelectVariables(String instanceId, Map<String, Object> variables) {
    if (variables == null) {
      return;
    }
    for (Map.Entry<String, Object> entry : variables.entrySet()) {
      if (entry.getKey() != null && entry.getKey().startsWith("_selfSelect_")) {
        log.info("[Flow] 发起人自选审批人变量: instanceId={} key={} value={}",
            instanceId, entry.getKey(), entry.getValue());
      }
    }
  }

  /**
   * 终止流程实例
   *
   * @param instanceId 实例 ID
   * @param reason 终止原因
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public void terminate(String instanceId, String reason) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    if (FlowInstanceStatus.valueOf(instance.getFlowStatus()).isFinished()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_2246960b")
          .build();
    }
    LocalDateTime now = LocalDateTime.now();
    Long durationMs =
        instance.getStartAt() == null
            ? null
            : Duration.between(instance.getStartAt(), now).toMillis();
    // P2-18: reason 持久化到 variable JSON
    String var = instance.getVariable();
    if (StringUtils.hasText(reason)) {
      try {
        Map<String, Object> m = variableManager.parseVariables(var);
        m.put("_terminateReason", reason);
        var = YdszJson.toJson(m);
        // 修复 P2-18: 写回 DB（之前仅改局部变量未持久化）
        instanceRepository.updateVariable(instanceId, var);
      } catch (Exception e) {
        log.warn(
            "[Flow] terminate reason 持久化失败: instanceId={} reason={}", instanceId, e.getMessage());
      }
    }
    instanceRepository.updateStatus(
        instanceId, FlowInstanceStatus.TERMINATED.name(), null, null, now, durationMs);
    // 取消所有 PENDING 任务
    taskService.cancelByInstance(instanceId, FlowTaskStatus.CANCELLED.name());
    // P0-1: 取消所有 WAITING 事件订阅
    eventSubscriptionService.cancelByInstance(instanceId, "INSTANCE_TERMINATED: " + reason);
    log.info("[Flow] 终止流程: instanceId={} reason={}", instanceId, reason);
    // P2-3: Prometheus 指标 — 实例终止 + 耗时
    if (flowMetrics != null) {
      flowMetrics.incInstance(instance.getFlowCode(), "terminated");
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

  /**
   * 挂起流程实例
   *
   * @param instanceId 实例 ID
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public void suspend(String instanceId) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    if (!FlowInstanceStatus.RUNNING.name().equals(instance.getFlowStatus())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_543fc92f")
          .build();
    }
    instanceRepository.updateStatus(
        instanceId,
        FlowInstanceStatus.SUSPENDED.name(),
        instance.getCurrentNodeCode(),
        instance.getCurrentNodeName(),
        null,
        null);
    // P2-18: 冻结 PENDING/CLAIMED 任务为 FROZEN，禁止办理
    taskRepository.freezeByInstance(instanceId);
    log.info("[Flow] 挂起流程: instanceId={}", instanceId);
    // P2-3: Prometheus 指标
    if (flowMetrics != null) {
      flowMetrics.incInstance(instance.getFlowCode(), "suspended");
    }
    // P2-34: 触发 onInstanceSuspended 事件
    fireEvent(l -> l.onInstanceSuspended(instanceId));
    // P2-35: 发布 Spring 异步事件
    publishWorkflowEvent("INSTANCE_SUSPENDED", instanceId, null);
  }

  /**
   * 激活流程实例
   *
   * @param instanceId 实例 ID
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public void activate(String instanceId) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    if (!FlowInstanceStatus.SUSPENDED.name().equals(instance.getFlowStatus())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_ab594c75")
          .build();
    }
    instanceRepository.updateStatus(
        instanceId,
        FlowInstanceStatus.RUNNING.name(),
        instance.getCurrentNodeCode(),
        instance.getCurrentNodeName(),
        null,
        null);
    // P2-18: 解冻 FROZEN 任务，回到 PENDING 可办理
    taskRepository.unfreezeByInstance(instanceId);
    log.info("[Flow] 激活流程: instanceId={}", instanceId);
    // P2-3: Prometheus 指标
    if (flowMetrics != null) {
      flowMetrics.incInstance(instance.getFlowCode(), "activated");
    }
    // P2-34: 触发 onInstanceActivated 事件
    fireEvent(l -> l.onInstanceActivated(instanceId));
    // P2-35: 发布 Spring 异步事件
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
  public void complete(String instanceId, String endNodeCode) {
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
    // P2-3: Prometheus 指标 — 实例完成 + 耗时
    if (flowMetrics != null) {
      flowMetrics.incInstance(instance.getFlowCode(), "completed");
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

  /**
   * P1-1: 撤回到指定历史节点（对标钉钉/飞书"撤回到指定节点"）。
   *
   * @param instanceId 实例 ID
   * @param initiatorId 发起人 ID
   * @param targetNodeCode 目标节点编码（null/空时降级到 {@link #recall(String, String)}）
   * @return 是否撤回成功
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public boolean recall(String instanceId, String initiatorId, String targetNodeCode) {
    // 向后兼容：targetNodeCode 为空时降级到原有 recall
    if (!StringUtils.hasText(targetNodeCode)) {
      return recall(instanceId, initiatorId);
    }

    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    // 校验：仅发起人可撤回
    if (!instance.getInitiatorId().equals(initiatorId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.FORBIDDEN)
          .message("error.workflow.msg_cc712a3a")
          .build();
    }
    // 校验：仅运行中可撤回
    if (!FlowInstanceStatus.RUNNING.name().equals(instance.getFlowStatus())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_3095a676")
          .build();
    }
    // 校验：下一节点未被处理（PENDING 状态的任务可以撤回）
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
          .message("error.workflow.msg_c55fe642")
          .build();
    }
    // 校验：targetNodeCode 必须在可撤回节点列表中
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
          .key("error.workflow.msg_e5f6a7b8")
          .params(targetNodeCode)
          .build();
    }

    // 取消当前待办（审计：CANCELLED，原因 RECALL）
    String currentNodeCode =
        pendingTasks.isEmpty() ? instance.getCurrentNodeCode() : pendingTasks.get(0).getNodeCode();
    taskService.cancelByInstance(instanceId, FlowTaskStatus.CANCELLED.name());

    // 退回到目标节点（复用 advancer.advance 的 REJECT 通道，保持审计轨迹一致）
    Map<String, Object> variables = variableManager.parseVariables(instance.getVariable());
    try {
      advancer.advance(instance, currentNodeCode, "REJECT", targetNodeCode, variables);
    } catch (Exception e) {
      log.error("[Flow] 撤回到指定节点失败: instanceId={} targetNodeCode={}", instanceId, targetNodeCode, e);
      throw SysException.builder()
          .resultCode(YdszResultCode.INTERNAL_ERROR)
          .key("error.workflow.msg_3d726320")
          .params(e.getMessage())
          .build();
    }

    log.info(
        "[Flow] 撤回流程到指定节点: instanceId={} initiatorId={} targetNodeCode={}",
        instanceId,
        initiatorId,
        targetNodeCode);
    // P2-3: Prometheus 指标 — 撤回
    if (flowMetrics != null) {
      flowMetrics.incInstance(instance.getFlowCode(), "recalled");
    }
    // P2-34: 触发 onInstanceRecalled 事件
    fireEvent(l -> l.onInstanceRecalled(instanceId, initiatorId));
    // P2-35: 发布 Spring 异步事件
    publishWorkflowEvent("INSTANCE_RECALLED", instanceId, null);
    return true;
  }

  /**
   * P1-8: 撤回流程（仅发起人可撤回，仅运行中可撤回，下一节点未被处理才可撤回）
   *
   * @param instanceId 实例 ID
   * @param initiatorId 发起人 ID
   * @return 是否撤回成功
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public boolean recall(String instanceId, String initiatorId) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    // 校验：仅发起人可撤回
    if (!instance.getInitiatorId().equals(initiatorId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.FORBIDDEN)
          .message("error.workflow.msg_cc712a3a")
          .build();
    }
    // 校验：仅运行中可撤回
    if (!FlowInstanceStatus.RUNNING.name().equals(instance.getFlowStatus())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_3095a676")
          .build();
    }
    // 校验：下一节点未被处理（PENDING 状态的任务可以撤回）
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
          .message("error.workflow.msg_c55fe642")
          .build();
    }
    // 取消当前待办
    taskService.cancelByInstance(instanceId, FlowTaskStatus.CANCELLED.name());
    // 回退到开始节点的下一节点（重新生成第一批待办）
    try {
      advancer.start(instanceId);
    } catch (Exception e) {
      log.error("[Flow] 撤回后重新推进失败: instanceId={}", instanceId, e);
      throw SysException.builder()
          .resultCode(YdszResultCode.INTERNAL_ERROR)
          .key("error.workflow.msg_3d726320")
          .params(e.getMessage())
          .build();
    }
    log.info("[Flow] 撤回流程: instanceId={} initiatorId={}", instanceId, initiatorId);
    // P2-3: Prometheus 指标 — 撤回
    if (flowMetrics != null) {
      flowMetrics.incInstance(instance.getFlowCode(), "recalled");
    }
    // P2-34: 触发 onInstanceRecalled 事件
    fireEvent(l -> l.onInstanceRecalled(instanceId, initiatorId));
    // P2-35: 发布 Spring 异步事件
    publishWorkflowEvent("INSTANCE_RECALLED", instanceId, null);
    return true;
  }

  /**
   * P2-3: 回滚已完成的流程实例（撤销）
   *
   * @param instanceId 实例 ID
   * @param operatorId 操作人 ID（发起人或管理员）
   * @param reason 回滚原因
   * @param maxRollbackDays 允许回滚的最大天数（&lt;=0 时使用默认值 7）
   * @return 是否回滚成功
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public boolean rollback(
      String instanceId, String operatorId, String reason, int maxRollbackDays) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);

    // 1. 校验：仅 COMPLETED 状态可回滚
    if (!FlowInstanceStatus.COMPLETED.name().equals(instance.getFlowStatus())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.msg_a1b2c3d4")
          .params(instance.getFlowStatus())
          .build();
    }

    // 2. 校验：仅发起人或管理员可回滚
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
          .message("error.workflow.msg_b2c3d4e5")
          .build();
    }

    // 3. 校验：回滚原因不能为空
    if (!StringUtils.hasText(reason)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_d4e5f6a7")
          .build();
    }

    // 4. 校验：时间窗口
    int days = maxRollbackDays > 0 ? maxRollbackDays : DEFAULT_ROLLBACK_DAYS;
    if (instance.getEndAt() != null) {
      long elapsedDays = Duration.between(instance.getEndAt(), LocalDateTime.now()).toDays();
      if (elapsedDays > days) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .key("error.workflow.msg_c3d4e5f6")
            .params(days)
            .build();
      }
    }

    // 5. 更新实例状态为 ROLLED_BACK
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

    // 6. 记录回滚元信息到 variable JSON
    try {
      Map<String, Object> vars = variableManager.parseVariables(instance.getVariable());
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

    // 7. Prometheus 指标
    if (flowMetrics != null) {
      flowMetrics.incInstance(instance.getFlowCode(), "recalled");
    }

    // 8. 触发 onInstanceRolledBack 事件（业务侧可执行补偿）
    fireEvent(l -> l.onInstanceRolledBack(instanceId, operatorId, reason));

    // 9. 发布 Spring 异步事件
    publishWorkflowEvent("INSTANCE_ROLLED_BACK", instanceId, null);

    return true;
  }

  /**
   * P3-1: 重审已结束实例（对标 flowlong reopen）。
   *
   * <p>将已完成（COMPLETED）的流程实例重新打开，回填到指定历史节点重新审批。
   * 与 {@link #rollback} 的区别：rollback 是"撤销"（实例变为 ROLLED_BACK 终态），
   * reopen 是"重审"（实例恢复为 RUNNING 态，继续推进）。
   *
   * @param instanceId    实例 ID
   * @param operatorId    操作人 ID（发起人或管理员）
   * @param targetNodeCode 目标节点编码（回填到哪个历史节点）
   * @param reason        重审原因
   * @return 是否重审成功
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public boolean reopen(
      String instanceId, String operatorId, String targetNodeCode, String reason) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);

    // 1. 校验：仅 COMPLETED 状态可重审
    if (!FlowInstanceStatus.COMPLETED.name().equals(instance.getFlowStatus())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.msg_a1b2c3d4")
          .params("仅已完成实例可重审，当前状态=" + instance.getFlowStatus())
          .build();
    }

    // 2. 校验：仅发起人或管理员可重审
    boolean isInitiator =
        instance.getInitiatorId() != null
            && String.valueOf(instance.getInitiatorId()).equals(operatorId);
    boolean isAdmin = false;
    LoginUser user = AuthContextUtils.getCurrentOrNull();
    if (user != null) {
      isAdmin = user.isSuperAdmin() || user.hasPermission(PERM_INSTANCE_REOPEN);
    }
    if (!isInitiator && !isAdmin) {
      throw SysException.builder()
          .resultCode(YdszResultCode.FORBIDDEN)
          .message("error.workflow.msg_reopen_forbidden")
          .build();
    }

    // 3. 校验：重审原因不能为空
    if (!StringUtils.hasText(reason)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_reopen_reason_required")
          .build();
    }

    // 4. 校验：目标节点必须是该实例已办过的历史节点
    if (!StringUtils.hasText(targetNodeCode)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_reopen_target_required")
          .build();
    }
    List<Map<String, Object>> passedNodes = hisTaskRepository.listPassedNodes(instanceId);
    boolean nodeExists =
        passedNodes.stream().anyMatch(t -> targetNodeCode.equals(String.valueOf(t.getOrDefault("nodeCode", ""))));
    if (!nodeExists) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.msg_reopen_node_not_found")
          .params(targetNodeCode)
          .build();
    }

    // 5. 更新实例状态为 RUNNING
    LocalDateTime now = LocalDateTime.now();
    instance.setFlowStatus(FlowInstanceStatus.RUNNING.name());
    instance.setCurrentNodeCode(targetNodeCode);
    instance.setEndAt(null);
    instanceRepository.save(toDto(instance));

    // 6. 在目标节点创建新的待办任务
    FlowNodeVO targetNodeVO =
        nodeRepository
            .findByCode(instance.getDefinitionId(), targetNodeCode)
            .orElseThrow(
                () ->
                    SysException.builder()
                        .resultCode(YdszResultCode.NOT_FOUND)
                        .key("error.workflow.msg_reopen_node_missing")
                        .params(targetNodeCode)
                        .build());
    Map<String, Object> variables = variableManager.getVariables(instanceId);
    taskService.createTask(instanceId, targetNodeVO, variables);

    // 7. 记录重审元信息到 variable JSON
    try {
      Map<String, Object> vars = variableManager.parseVariables(instance.getVariable());
      Map<String, Object> reopenInfo = new LinkedHashMap<>();
      reopenInfo.put("operatorId", operatorId);
      reopenInfo.put("reason", reason);
      reopenInfo.put("reopenedAt", now.toString());
      reopenInfo.put("targetNodeCode", targetNodeCode);
      reopenInfo.put("byAdmin", isAdmin && !isInitiator);
      vars.put("_reopen", reopenInfo);
      instanceRepository.updateVariable(instanceId, YdszJson.toJson(vars));
    } catch (Exception e) {
      log.warn("[Flow] 重审元信息持久化失败: instanceId={} err={}", instanceId, e.getMessage());
    }

    // 8. 写审计日志
    FlowAuditLogVO audit = new FlowAuditLogVO();
    audit.setInstanceId(instanceId);
    audit.setFlowCode(instance.getFlowCode());
    audit.setBusinessType(instance.getBusinessType());
    audit.setBusinessId(instance.getBusinessId());
    audit.setAction("REOPEN");
    audit.setNodeCode(targetNodeCode);
    audit.setOperatorId(operatorId);
    audit.setComment(reason);
    audit.setProviderTraceId(MDC.get("traceId"));
    auditLogRepository.save(audit);

    log.info(
        "[Flow] 重审流程: instanceId={} operatorId={} targetNode={} reason={} isAdmin={}",
        instanceId,
        operatorId,
        targetNodeCode,
        reason,
        isAdmin && !isInitiator);

    // 9. Prometheus 指标
    if (flowMetrics != null) {
      flowMetrics.incInstance(instance.getFlowCode(), "reopened");
    }

    // 10. 触发 onInstanceReopened 事件
    fireEvent(l -> l.onInstanceReopened(instanceId, operatorId, targetNodeCode, reason));

    // 11. 发布 Spring 异步事件
    publishWorkflowEvent("INSTANCE_REOPENED", instanceId, null);

    return true;
  }

  /**
   * P2-2 (GAP-10): 驳回后快速重审
   *
   * @param instanceId 被驳回的实例 ID
   * @param initiatorId 发起人 ID
   * @param variables 重审时新增/覆盖的变量（可空）
   * @param comment 重审说明（可选）
   * @return 实例 ID
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public String resubmit(
      String instanceId, String initiatorId, Map<String, Object> variables, String comment) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    // 1. 状态校验：仅 REJECTED 可重审
    FlowInstanceStatus status = FlowInstanceStatus.valueOf(instance.getFlowStatus());
    if (status != FlowInstanceStatus.REJECTED) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.msg_7f4098fb")
          .params("仅被驳回实例可重审，当前状态=" + instance.getFlowStatus())
          .build();
    }
    // 2. 发起人校验
    if (instance.getInitiatorId() != null
        && !String.valueOf(instance.getInitiatorId()).equals(initiatorId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.FORBIDDEN)
          .key("error.workflow.msg_d65b2814")
          .params("仅发起人可重审")
          .build();
    }
    // 3. 合并变量（保留历史变量，覆盖新增）
    Map<String, Object> merged = variableManager.getVariables(instanceId);
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
    instance.setVariable(merged.isEmpty() ? null : YdszJson.toJson(merged));
    instanceRepository.save(toDto(instance));
    // 5. 记录重审审计（保留原轨迹，仅追加一条 RESUBMIT 记录）
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
    // 6. 从开始节点重新推进（复用 advancer.start，保留 ydsz_flow_user/his_task 历史）
    try {
      advancer.start(instanceId);
    } catch (Exception e) {
      // P2-3: 触发 onError 事件（统一事件机制）
      fireEvent(l -> l.onError(instanceId, e));
      throw e;
    }
    log.info("[Flow] 驳回后快速重审: instanceId={} initiatorId={}", instanceId, initiatorId);
    return instanceId;
  }

  /**
   * P1-8: 流程重做 — 支持 redoMode 指定重做策略。
   *
   * @param instanceId 原实例 ID
   * @param initiatorId 发起人 ID
   * @param variables 重做时新增/覆盖的变量（可空）
   * @param comment 重做说明（可选）
   * @param redoMode 重做模式：RESTART / NEW_INSTANCE
   * @return 实例 ID
   */
  @Transactional(rollbackFor = Exception.class)
  @YdszDistributedLock(key = "'flow:instance:op:' + #instanceId", waitTime = 3, leaseTime = 30)
  public String resubmit(
      String instanceId,
      String initiatorId,
      Map<String, Object> variables,
      String comment,
      String redoMode) {
    String mode = (redoMode == null || redoMode.isBlank()) ? "RESTART" : redoMode.toUpperCase();
    if ("NEW_INSTANCE".equals(mode)) {
      return resubmitAsNewInstance(instanceId, initiatorId, variables, comment);
    }
    // 默认 RESTART 模式：委托到现有 resubmit（向后兼容）
    return resubmit(instanceId, initiatorId, variables, comment);
  }

  /**
   * 设置实例的 dueAt 字段（子流程超时处理）
   *
   * @param instanceId 实例 ID
   * @param dueAt 超时时间（传 null 清除超时标记）
   */
  @Transactional(rollbackFor = Exception.class)
  public void setDueAt(String instanceId, LocalDateTime dueAt) {
    instanceRepository.updateDueAt(instanceId, dueAt);
    log.info("[Flow] 设置实例到期时间: instanceId={} dueAt={}", instanceId, dueAt);
  }

  // ============================== 内部方法 ==============================

  /**
   * 内部方法：创建第一个待办任务（供 DefaultFlowAdvancer 调用）
   *
   * @param instanceId 参数说明
   * @param startNode 参数说明
   * @param variables 参数说明
   * @return 返回值说明
   */
  public String createFirstTask(
      String instanceId, FlowNodeVO startNode, Map<String, Object> variables) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    List<FlowNodeVO> nextNodes =
        advancer.advance(instance, startNode.getNodeCode(), "PASS", null, variables);
    if (nextNodes.isEmpty()) {
      log.warn("[Flow] 流程无下游节点: instanceId={}", instanceId);
      complete(instanceId, startNode.getNodeCode());
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
   * 内部方法：推进后批量生成任务（供 DefaultFlowAdvancer 调用）
   *
   * @param instanceId 参数说明
   * @param nextNodes 参数说明
   * @param variables 参数说明
   */
  public void generateTasksForNodes(
      String instanceId, List<FlowNodeVO> nextNodes, Map<String, Object> variables) {
    if (nextNodes == null || nextNodes.isEmpty()) {
      return;
    }
    List<FlowNodeVO> doNodes = nextNodes;
    for (FlowNodeVO node : doNodes) {
      // P0-2: 优先判断事件捕获节点（boundaryEvent / intermediateCatchEvent）
      if (eventSubscriptionService.isEventCatchNode(node)) {
        String boundaryTaskId = resolveBoundaryTaskId(node, instanceId);
        eventSubscriptionService.createSubscription(instanceId, node, variables, boundaryTaskId);
        // P0-2: 如果 ext.timer 存在，注册边界定时器自动触发（timer boundary 语义）
        scheduleBoundaryTimerIfPresent(node, instanceId, boundaryTaskId);
        // 更新实例当前节点为事件捕获节点（流程在此等待事件触发）
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
        // GAP-P1: 抄送节点 — 展开接收人并写入 ydsz_flow_cc，然后自动推进到下一节点
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
        // 抄送节点是穿透节点：自动推进到下游
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
        complete(instanceId, node.getNodeCode());
        return;
      }
      // P1-3 / fix-1: SUBPROCESS 节点或 ext 中含 callActivityFlowCode 的节点触发子流程
      if (node.getNodeType().equals(FlowNodeType.SUBPROCESS.getCode()) || isCallActivity(node)) {
        try {
          FlowInstanceVO instance = instanceRepository.findById(instanceId).orElse(null);
          subProcessService.startSubProcess(instance, node, variables);
          // 子流程启动后，父流程"停在" callActivity 节点，更新 currentNodeCode
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
              .key("error.workflow.msg_f2bd498c")
              .params(e.getMessage())
              .build();
        }
        continue;
      }
      taskService.createTask(instanceId, node, variables);
    }
  }

  /**
   * GAP-V2-03: 动态追加节点（对标 flowlong executeAppendNodeModel）。
   *
   * <p>在运行中的流程实例上动态追加一个审批节点，不修改流程定义。
   * 新节点作为"插入节点"在当前节点之后、下一节点之前执行。
   *
   * <p><b>实现原理：</b>
   *
   * <ol>
   *   <li>校验实例状态为 RUNNING</li>
   *   <li>构建临时 FlowNode（nodeType=APPROVAL）</li>
   *   <li>调用 taskService.createTask 创建待办</li>
   *   <li>记录追加信息到实例 variable（appendedNodes）</li>
   *   <li>写审计日志</li>
   * </ol>
   *
   * @param instanceId     流程实例 ID
   * @param currentNodeCode 当前节点编码
   * @param nodeName       新节点名称
   * @param assigneeType   办理人类型
   * @param assigneeId     办理人 ID
   * @param operatorId     操作人 ID
   * @param comment        追加原因
   * @return 新创建的任务 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public String appendNode(
      String instanceId,
      String currentNodeCode,
      String nodeName,
      String assigneeType,
      String assigneeId,
      String operatorId,
      String comment) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    FlowInstanceStatus status = FlowInstanceStatus.valueOf(instance.getFlowStatus());
    if (status != FlowInstanceStatus.RUNNING) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.msg_c9d0e1f2")
          .params("仅运行中实例可追加节点，当前状态=" + instance.getFlowStatus())
          .build();
    }

    String appendedNodeCode = "appended_" + currentNodeCode + "_" + System.currentTimeMillis();

    FlowNodeVO appendedNode = new FlowNodeVO();
    appendedNode.setDefinitionId(instance.getDefinitionId());
    appendedNode.setFlowCode(instance.getFlowCode());
    appendedNode.setNodeType(FlowNodeType.APPROVAL.getCode());
    appendedNode.setNodeCode(appendedNodeCode);
    appendedNode.setNodeName(nodeName);
    appendedNode.setPermissionFlag(assigneeType.toLowerCase() + ":" + assigneeId);

    Map<String, Object> variables = variableManager.getVariables(instanceId);
    String taskId = taskService.createTask(instanceId, appendedNode, variables);

    Map<String, Object> appendedInfo = new HashMap<>();
    appendedInfo.put("nodeCode", appendedNodeCode);
    appendedInfo.put("nodeName", nodeName);
    appendedInfo.put("assigneeType", assigneeType);
    appendedInfo.put("assigneeId", assigneeId);
    appendedInfo.put("operatorId", operatorId);
    appendedInfo.put("comment", comment);
    appendedInfo.put("createdAt", LocalDateTime.now().toString());

    Map<String, Object> vars = variableManager.getVariables(instanceId);
    List<Map<String, Object>> appendedNodes = new ArrayList<>();
    Object existing = vars.get("appendedNodes");
    if (existing instanceof List<?> list) {
      for (Object item : list) {
        if (item instanceof Map) {
          @SuppressWarnings("unchecked")
          Map<String, Object> map = (Map<String, Object>) item;
          appendedNodes.add(map);
        }
      }
    }
    appendedNodes.add(appendedInfo);
    variableManager.setVariable(instanceId, "appendedNodes", appendedNodes);

    FlowAuditLogVO audit = new FlowAuditLogVO();
    audit.setInstanceId(instanceId);
    audit.setFlowCode(instance.getFlowCode());
    audit.setBusinessType(instance.getBusinessType());
    audit.setBusinessId(instance.getBusinessId());
    audit.setAction("APPEND_NODE");
    audit.setNodeCode(appendedNodeCode);
    audit.setOperatorId(operatorId);
    audit.setComment(comment);
    audit.setProviderTraceId(MDC.get("traceId"));
    auditLogRepository.save(audit);

    log.info("[Flow] 动态追加节点: instanceId={} nodeCode={} taskId={}", instanceId, appendedNodeCode, taskId);
    return taskId;
  }

  // ============================== 私有辅助方法 ==============================

  private FlowInstanceVO getByIdOrThrow(String id) {
    FlowInstanceVO instance = instanceRepository.findById(id).orElse(null);
    if (instance == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.msg_67a10717")
          .params(id)
          .build();
    }
    return instance;
  }

  /**
   * NEW_INSTANCE 模式：创建全新实例，复用原实例的 flowCode / businessType / businessId / initiator，
   * 合并原变量与传入变量。原实例保持不变，仅追加一条 REDO_NEW_INSTANCE 审计日志。
   *
   * @param instanceId 参数说明
   * @param initiatorId 参数说明
   * @param variables 参数说明
   * @param comment 参数说明
   * @return 返回值说明
   */
  private String resubmitAsNewInstance(
      String instanceId, String initiatorId, Map<String, Object> variables, String comment) {
    FlowInstanceVO instance = getByIdOrThrow(instanceId);
    // 1. 状态校验：仅非运行态可重做（RUNNING / SUSPENDED 不可）
    FlowInstanceStatus status = FlowInstanceStatus.valueOf(instance.getFlowStatus());
    if (status == FlowInstanceStatus.RUNNING || status == FlowInstanceStatus.SUSPENDED) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.msg_c9d0e1f2")
          .params("运行中/挂起的实例不可重做，当前状态=" + instance.getFlowStatus())
          .build();
    }
    // 2. 发起人校验
    if (instance.getInitiatorId() != null
        && !String.valueOf(instance.getInitiatorId()).equals(initiatorId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.FORBIDDEN)
          .key("error.workflow.msg_d65b2814")
          .params("仅发起人可重做")
          .build();
    }
    // 3. 合并变量（保留原实例变量，覆盖新增）
    Map<String, Object> merged = variableManager.getVariables(instanceId);
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
    log.info("[Flow] 重做为新实例: 原实例={} 新实例={} initiatorId={}", instanceId, newInstanceId, initiatorId);
    return newInstanceId;
  }

  /**
   * P0-2: 解析 boundaryEvent 的 timer 配置并注册边界定时器
   * 
   * <p>BPMN timer event definition 支持三种形式：
   * 
   * <ul>
   * <li>{@code timeDuration} — ISO 8601 持续时间（如 "PT1H30M"），到点触发一次
   * <li>{@code timeDate} — ISO 8601 绝对时间（如 "2026-07-07T10:00:00"），到点触发一次
   * <li>{@code timeCycle} — ISO 8601 循环（如 "R3/PT10M"），目前仅支持首次触发，循环触发待后续实现
   * </ul>
   * 
   * <p>解析失败时不抛异常，仅记录 warn 日志，避免阻塞流程实例创建。
   *
   * @param node 参数说明
   * @param instanceId 参数说明
   * @param boundaryTaskId 参数说明
   */
  private void scheduleBoundaryTimerIfPresent(
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
      log.warn("[Flow] 边界定时器配置无法解析或已过期，跳过: node={} timer={}", node.getNodeCode(), timerRaw);
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

  /**
   * P0-2: 解析 BPMN timer 配置为 Duration
   * 
   * <p>优先级：duration > date > cycle（cycle 仅取首次）
   *
   * @param timer 参数说明
   * @return 返回值说明
   */
  private Duration parseTimerDelay(Map<?, ?> timer) {
    Object duration = timer.get("duration");
    if (duration != null) {
      try {
        return Duration.parse(duration.toString()); // ISO 8601, e.g. "PT1H30M"
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

  /** P0-2: 解析节点 ext JSON 为 Map（容错） */
  private Map<String, Object> parseExtMap(FlowNodeVO node) {
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

  /**
   * P0-1: 解析边界事件关联的 userTask ID
   * 
   * <p>boundaryEvent 节点 ext 中 attachedToRef 指向被附着的节点编码， 查找该节点的当前 PENDING 任务作为 boundaryTaskId。
   * intermediateCatchEvent 无 attachedToRef，返回 null。
   *
   * @param node 参数说明
   * @param instanceId 参数说明
   * @return 返回值说明
   */
  private String resolveBoundaryTaskId(FlowNodeVO node, String instanceId) {
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
      // 查找被附着节点的当前 PENDING 任务
      List<FlowRunTaskVO> tasks = taskRepository.findPendingByNode(instanceId, attachedToRef);
      return tasks.isEmpty() ? null : tasks.get(0).getId();
    } catch (Exception e) {
      log.warn(
          "[Flow] 解析 boundaryTaskId 失败: nodeCode={} err={}", node.getNodeCode(), e.getMessage());
      return null;
    }
  }

  /**
   * P1-3: 判断节点是否为 callActivity（子流程）
   * 
   * <p>识别条件：节点 ext JSON 中包含 callActivityFlowCode 字段
   *
   * @param node 参数说明
   * @return 返回值说明
   */
  private boolean isCallActivity(FlowNodeVO node) {
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
      log.warn("[FlowInstanceLifecycleManager] 节点 ext 解析失败，视为非子流程调用: {}", e.getMessage());
      return false;
    }
  }

  // ============================== 事件触发 ==============================

  /**
   * P2-3: 触发事件监听器（委托给 FlowTaskSupport 统一处理）
   *
   * @param action 参数说明
   */
  private void fireEvent(Consumer<FlowEventListener> action) {
    flowTaskSupport.fireEvent(action, null);
  }

  /**
   * P2-3: 发布 Spring 异步事件（委托给 FlowTaskSupport 统一处理）
   *
   * @param eventType 参数说明
   * @param instanceId 参数说明
   * @param taskId 参数说明
   */
  private void publishWorkflowEvent(String eventType, String instanceId, String taskId) {
    flowTaskSupport.publishWorkflowEvent(eventType, instanceId, taskId);
  }

  /**
   * P2-37: 构建事件上下文元数据
   *
   * @param instanceId 实例 ID
   * @param taskId 任务 ID
   * @param operatorId 操作人 ID
   * @param action 操作动作
   * @param instance 流程实例（用于提取 tenantId/traceId，可空）
   * @return 事件上下文
   */
  private FlowEventContext buildContext(
      String instanceId, String taskId, String operatorId, String action, FlowInstanceVO instance) {
    FlowEventContext ctx = new FlowEventContext();
    ctx.setInstanceId(instanceId);
    ctx.setTaskId(taskId);
    ctx.setOperatorId(operatorId);
    ctx.setAction(action);
    ctx.setOperatedAt(LocalDateTime.now());
    if (instance != null) {
      ctx.setTenantId(
          instance.getTenantId() == null ? null : String.valueOf(instance.getTenantId()));
      // P1-5: 优先使用实例的 providerTraceId，回退 RequestContext / MDC 分布式追踪 ID
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

  /**
   * 将流程实例 VO 转换为 DTO（用于 Repository 保存操作）。
   *
   * <p>DDD 分层规范：Service 层内部完成 VO→DTO 转换，避免依赖 infra 层转换器。
   *
   * @param vo 流程实例 VO
   * @return 流程实例 DTO
   */
  private static FlowInstanceDTO toDto(FlowInstanceVO vo) {
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
