package com.njydsz.workflow.server.service.impl.instance;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.domain.enums.FlowNodeType;
import com.njydsz.workflow.domain.enums.FlowPerformType;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.repository.FlowNodeRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowSkipVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.engine.impl.DefaultFlowAdvancer;
import com.njydsz.workflow.server.form.FlowFormEngineService;
import com.njydsz.workflow.server.form.FlowFormSchema;
import com.njydsz.workflow.server.metrics.FlowMetrics;
import com.njydsz.workflow.server.service.FlowAttachmentService;
import com.njydsz.workflow.server.engine.FlowDefinitionCacheService;
import com.njydsz.workflow.server.service.FlowFormFieldPermService;
import com.njydsz.workflow.server.service.FlowInstanceService;
import com.njydsz.workflow.server.service.FlowTodoCountPushService;
import com.njydsz.workflow.server.service.impl.CountersignStrategy;
import com.njydsz.workflow.server.service.impl.CountersignStrategyFactory;
import com.njydsz.workflow.server.service.impl.instance.FlowTaskAuditService;

/**
 * 任务核心操作服务（由 FlowTaskPassService + FlowTaskRejectService + FlowTaskCompleteServiceImpl 合并）
 *
  * <p>工作流引擎中任务核心操作的统一入口，承担任务创建、签收、通过、驳回、转办、委派的职责。 是从原 {@code FlowTaskCompleteServiceImpl}（门面） + {@code 
  * FlowTaskPassService}（通过） + {@code FlowTaskRejectService}
 * （驳回）合并的产物，合并后消除了子服务之间的委托调用链，直接内联核心逻辑。
 *
 * <p><b>合并范围：</b>
 *
 * <ul>
 *   <li>{@link #createTask} — 任务创建（委托 {@link FlowTaskCreateService}）
 *   <li>{@link #claim} — 任务签收（委托 {@link FlowTaskClaimService}）
 *   <li>{@link #pass} — 任务通过（从 {@code FlowTaskPassService} 内联）
 *   <li>{@link #reject} — 任务驳回（从 {@code FlowTaskRejectService} 内联）
 *   <li>{@link #transfer} — 任务转办（委托 {@link FlowTaskOperateService}）
 *   <li>{@link #delegate} — 任务委派（委托 {@link FlowTaskOperateService}）
 *   <li>{@link #jump} — 自由跳转（委托 {@link FlowTaskOperateService}）
 *   <li>{@link #retract} — 取回（委托 {@link FlowTaskOperateService}）
 *   <li>{@link #urge} / {@link #urgeByNode} — 催办（委托 {@link FlowTaskUrgeService}）
 *   <li>{@link #timeoutTask} / {@link #suspendTask} / {@link #activateTask} / {@link #cancelByInstance}
 *       — 超时/挂起/激活/取消（委托 {@link FlowTaskTimeoutService}）
 * </ul>
 *
 * <p><b>事务边界：</b>所有公共方法开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>循环依赖处理</b>：与 {@link FlowTaskCreateService} / {@link FlowTaskOperateService}
 *       等服务存在循环依赖，通过 {@code @Lazy} 注解打破
 *   <li><b>空安全</b>：所有集合 / 字符串参数均做空检查，避免 NPE
 *   <li><b>指标埋点</b>：通过 {@link FlowMetrics} 暴露任务操作数等 Prometheus 指标
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowTaskServiceImpl 任务门面（上层委托入口）
 * @see FlowRunTaskVO 运行时任务视图对象
 * @see FlowNodeVO 流程节点视图对象
 * @see DefaultFlowAdvancer 流程推进引擎
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskCoreService {

  // ============================== 子服务委托 ==============================

  /** 任务创建服务（复杂创建逻辑委托） */
  @Lazy private final FlowTaskCreateService flowTaskCreateService;

  /** 任务签收服务（签收逻辑委托） */
  @Lazy private final FlowTaskClaimService flowTaskClaimService;

  /** 任务操作服务（转办 / 委派 / 跳转 / 撤回逻辑委托） */
  @Lazy private final FlowTaskOperateService flowTaskOperateService;

  /** 任务催办服务（实例级 / 节点级催办逻辑委托） */
  @Lazy private final FlowTaskUrgeService flowTaskUrgeService;

  /** 超时 / 挂起 / 激活 / 取消服务（超时 / 挂起 / 激活 / 取消逻辑委托） */
  @Lazy private final FlowTaskTimeoutService flowTaskTimeoutService;

  // ============================== 审计和指标类型常量 ==============================

  /** 审计类型：通过 */
  private static final String AUDIT_TYPE_PASS = "PASS";

  /** 审计类型：驳回 */
  private static final String AUDIT_TYPE_REJECT = "REJECT";

  /** 审计类型：委派回归 */
  private static final String AUDIT_TYPE_DELEGATE_RETURN = "DELEGATE_RETURN";

  /** 指标类型：已通过 */
  private static final String METRIC_TASK_PASSED = "passed";

  /** 指标类型：已驳回 */
  private static final String METRIC_TASK_REJECTED = "rejected";

  /** 指标类型：已通过（记录耗时） */
  private static final String METRIC_TASK_STATUS_PASSED = "PASSED";

  /** 指标类型：已驳回（记录耗时） */
  private static final String METRIC_TASK_STATUS_REJECTED = "REJECTED";

  /** 指标类型：实例已驳回 */
  private static final String METRIC_INSTANCE_REJECTED = "rejected";

  /** 审计类型：任务 */
  private static final String AUDIT_TYPE_TASK = "TASK";

  // ============================== 通过 / 驳回所需依赖 ==============================

  /** 运行时任务仓储，查询/更新任务状态 */
  private final FlowRunTaskRepository taskRepository;

  /** 流程实例仓储，查询实例状态和流程变量 */
  private final FlowInstanceRepository instanceRepository;

  /** 流程节点仓储，查询节点配置（审批人/权限/SLA 等） */
  private final FlowNodeRepository nodeRepository;

  /** 流程推进引擎，会签完成后推进到下一节点 */
  private final DefaultFlowAdvancer advancer;

  /** 流程实例服务，更新实例状态和变量 */
  private final FlowInstanceService instanceService;

  /** 跨子 Service 共享的任务校验/审计/事件辅助 */
  private final FlowTaskSupport support;

  /** 任务事件通知服务，推送任务通过/驳回通知 */
  private final FlowTaskNotificationService notificationService;

  /** 委派代理审计服务，记录代理人审批操作 */
  @Lazy private final FlowTaskAuditService auditService;

  /** 会签策略工厂，根据 performType 选择会签策略 */
  private final CountersignStrategyFactory strategyFactory;

  /** 表单字段权限服务，校验表单字段读写权限 */
  private final FlowFormFieldPermService formFieldPermService;

  /** P0-3: 表单引擎服务 */
  private final FlowFormEngineService formEngineService;

  /** P1-6: 审批附件服务 */
  @Lazy private final FlowAttachmentService attachmentService;

  /** 任务归档服务，完成当前任务后写入历史任务表 */
  private final FlowTaskArchiveService archiveService;

  /** P1-7: 待办数 WebSocket 推送服务 */
  @Lazy private final FlowTodoCountPushService todoCountPushService;

  /** P1-2: 流程定义缓存服务（解析 startNode 下游第一节点） */
  @Lazy private final FlowDefinitionCacheService definitionCacheService;

  /** P2-3: Prometheus 指标（可能为 null：测试环境） */
  private final FlowMetrics flowMetrics;

  // ============================== 任务创建 ==============================

  /**
   * 创建任务（向后兼容重载）
   *
   * @param instanceId 流程实例 ID
   * @param node 流程节点（含 nodeId、nodeCode、nodeName 等）
   * @param variables 流程变量 Map
   * @return 新创建的任务 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public String createTask(String instanceId, FlowNodeVO node, Map<String, Object> variables) {
    return flowTaskCreateService.createTask(instanceId, node, variables);
  }

  /**
   * 创建任务（支持显式指定办理人）
   *
   * @param instanceId 流程实例 ID
   * @param node 流程节点（含 nodeId、nodeCode、nodeName 等）
   * @param variables 流程变量 Map
   * @param explicitAssignees 显式指定的办理人 ID 列表
   * @return 新创建的任务 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public String createTask(
      String instanceId,
      FlowNodeVO node,
      Map<String, Object> variables,
      List<String> explicitAssignees) {
    return flowTaskCreateService.createTask(instanceId, node, variables, explicitAssignees);
  }

  // ============================== 签收 ==============================

  /**
   * 任务签收
   *
   * @param taskId 任务 ID
   * @param userId 签收用户 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void claim(String taskId, String userId) {
    flowTaskClaimService.claim(taskId, userId);
  }

  // ============================== 通过 ==============================

  /**
   * 通过任务。
   *
   * @param dto 操作参数（taskId/userId/comment/variables/attachments）
   */
  @Transactional(rollbackFor = Exception.class)
  public void pass(FlowTaskOperateDTO dto) {
    FlowRunTaskVO task = support.getTaskOrThrow(dto.getTaskId());
    validateTaskNotFinished(task);

    Map<String, Object> variables = dto.getVariables() == null ? Collections.emptyMap() : dto.getVariables();
    FlowInstanceVO instance = instanceRepository.findById(task.getInstanceId()).orElse(null);
    Map<String, Object> mergedVars = mergeVariables(instance, variables);

    // P0-2: 表单字段权限校验
    validateFormFieldPerms(task, dto.getVariables(), instance);

    // P1-10: 委派回归 — 被委派人通过后任务回到原办理人
    if (isDelegatedWithAssignor(task)) {
      handleDelegateReturn(task, dto);
      return;
    }

    FlowPerformType performType = resolvePerformType(task);

    // 标记当前用户已处理（ydsz_flow_user）
    markUserProcessed(task, dto);

    // P1-6: 保存审批附件
    savePassAttachments(task, dto);

    // 策略模式处理会签
    applyCountersignStrategy(task, dto, performType);

    // P2-38: 触发个人完成事件（会签中单个办理人完成审批，无论会签是否全部完成）
    firePersonalCompletedEvent(task, dto, mergedVars);

    if (shouldAdvance(task, dto, performType)) {
      advanceProcess(instance, task, mergedVars, performType, dto);
    } else {
      logPartialPass(performType, task);
    }

    // P1-7/P2-3: 推送 + 指标
    pushTaskCompleted(task, flowMetrics, dto);
  }

  @Transactional(rollbackFor = Exception.class)
  public void reject(FlowTaskOperateDTO dto) {
    FlowRunTaskVO task = support.getTaskOrThrow(dto.getTaskId());
    validateTaskNotFinishedReject(task);

    LocalDateTime now = LocalDateTime.now();
    Long durationMs = task.getCreatedAt() == null ? null : Duration.between(task.getCreatedAt(), now).toMillis();
    markTaskRejected(task, dto, now, durationMs);

    archiveService.archiveToHistory(task, FlowTaskStatus.REJECTED);
    saveRejectAttachments(task, dto);

    FlowInstanceVO instance = instanceRepository.findById(task.getInstanceId()).orElse(null);
    Map<String, Object> mergedVars = mergeVariables(instance, dto.getVariables());

    // P1-2: 退回到发起人
    resolveRejectToInitiator(dto, instance);

    // GAP-P0-2: 优先使用多节点同退；为空时降级到单节点
    List<FlowNodeVO> rejectTargets = resolveRejectTargets(task, dto, instance, mergedVars);
    if (rejectTargets.isEmpty()) {
      handleRejectToEnd(task, instance, dto, now);
      return;
    }

    instanceService.generateTasksForNodes(instance.getId(), rejectTargets, mergedVars);
    instanceRepository.updateStatus(instance.getId(), instance.getFlowStatus(),
        rejectTargets.get(0).getNodeCode(), rejectTargets.get(0).getNodeName(), null, null);
    support.audit(task, AUDIT_TYPE_REJECT, dto.getUserId(), null, dto.getComment(), dto.getCommentType());
    log.info("[Flow] 退回任务: taskId={} targets={} multi={}", task.getId(),
        rejectTargets.stream().map(FlowNodeVO::getNodeCode).toList(),
        dto.getTargetNodeCodes() != null && dto.getTargetNodeCodes().size() > 1);

    // P1-7: WebSocket 推送
    if (todoCountPushService != null) {
      todoCountPushService.pushTaskRejected(task, dto.getUserId(), dto.getComment());
    }
  }

  /**
   * 校验任务未处于终态。
   *
   * @param task 运行时任务实体
   */
  private void validateTaskNotFinished(FlowRunTaskVO task) {
    if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.msg_7f4098fb")
          .params(task.getTaskStatus())
          .build();
    }
  }

  /**
   * 校验任务未处于终态（驳回专用）。
   *
   * @param task 运行时任务实体
   */
  private void validateTaskNotFinishedReject(FlowRunTaskVO task) {
    if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.msg_b35e6ea3")
          .build();
    }
  }

  /**
   * 判断任务是否为委派状态且有指派人。
   *
   * @param task 运行时任务实体
   * @return true=委派状态且有指派人；false=非委派状态或无指派人
   */
  private boolean isDelegatedWithAssignor(FlowRunTaskVO task) {
    return FlowTaskStatus.DELEGATED.name().equals(task.getTaskStatus()) && task.getAssignorId() != null;
  }

  /**
   * 解析任务执行策略，默认 OR。
   *
   * @param task 运行时任务实体
   * @return 任务执行策略枚举（OR/AND 等）
   */
  private FlowPerformType resolvePerformType(FlowRunTaskVO task) {
    return FlowPerformType.valueOf(
        task.getPerformType() == null ? FlowPerformType.OR.name() : task.getPerformType());
  }

  /**
   * 标记当前用户已处理。
   *
   * @param task 运行时任务实体
   * @param dto 任务操作 DTO
   */
  private void markUserProcessed(FlowRunTaskVO task, FlowTaskOperateDTO dto) {
    if (dto.getUserId() != null) {
      taskRepository.markProcessed(task.getId(), String.valueOf(dto.getUserId()), dto.getComment(), LocalDateTime.now());
    }
  }

  /**
   * 保存通过附件。
   *
   * @param task 运行时任务实体
   * @param dto 任务操作 DTO（含 attachments）
   */
  private void savePassAttachments(FlowRunTaskVO task, FlowTaskOperateDTO dto) {
    attachmentService.saveBatch(task.getInstanceId(), task.getId(), task.getNodeCode(),
        AUDIT_TYPE_TASK, dto.getUserId(), dto.getUserName(), dto.getAttachments(),
        task.getTenantId(), task.getProviderTraceId());
  }

  /**
   * 保存驳回附件。
   *
   * @param task 运行时任务实体
   * @param dto 任务操作 DTO（含 attachments）
   */
  private void saveRejectAttachments(FlowRunTaskVO task, FlowTaskOperateDTO dto) {
    attachmentService.saveBatch(task.getInstanceId(), task.getId(), task.getNodeCode(),
        AUDIT_TYPE_TASK, dto.getUserId(), dto.getUserName(), dto.getAttachments(),
        task.getTenantId(), task.getProviderTraceId());
  }

  /**
   * 应用会签策略：preCheck + onUserPassed。
   *
   * @param task 运行时任务实体
   * @param dto 任务操作 DTO
   * @param performType 任务执行策略（OR/AND 等）
   */
  private void applyCountersignStrategy(FlowRunTaskVO task, FlowTaskOperateDTO dto, FlowPerformType performType) {
    CountersignStrategy strategy = strategyFactory.getStrategy(performType);
    strategy.preCheck(task, dto);
    strategy.onUserPassed(task, dto);
  }

  /**
   * 判断是否应推进流程。
   *
   * @param task 运行时任务实体
   * @param dto 任务操作 DTO
   * @param performType 任务执行策略（OR/AND 等）
   * @return true=应推进流程；false=会签未完成，不推进
   */
  private boolean shouldAdvance(FlowRunTaskVO task, FlowTaskOperateDTO dto, FlowPerformType performType) {
    CountersignStrategy strategy = strategyFactory.getStrategy(performType);
    boolean advance = strategy.shouldAdvance(task);
    if (advance) {
      strategy.onAdvance(task, dto);
    }
    return advance;
  }

  /**
   * 记录部分通过日志。
   *
   * @param performType 任务执行策略（OR/AND 等）
   * @param task 运行时任务实体
   */
  private void logPartialPass(FlowPerformType performType, FlowRunTaskVO task) {
    support.audit(task, performType.name() + "_PASS", null, null, null, null);
    log.info("[Flow] {} 部分通过: taskId={} finished={}/{}",
        performType, task.getId(), task.getApproveFinished(), task.getApproveCount());
  }

  /**
   * WebSocket 推送 + Prometheus 指标。
   *
   * @param task 运行时任务实体
   * @param metrics Prometheus 指标收集器
   * @param dto 任务操作 DTO
   */
  private void pushTaskCompleted(FlowRunTaskVO task, FlowMetrics metrics, FlowTaskOperateDTO dto) {
    if (todoCountPushService != null) {
      todoCountPushService.pushTaskCompleted(task, dto.getUserId());
    }
    if (metrics != null) {
      metrics.incTask(task.getFlowCode(), task.getNodeCode(), METRIC_TASK_PASSED);
      metrics.recordTaskDuration(task, METRIC_TASK_STATUS_PASSED);
    }
  }

  /**
   * 标记任务为驳回状态并更新。
   *
   * @param task 运行时任务实体
   * @param dto 任务操作 DTO
   * @param now 当前时间
   * @param durationMs 任务处理耗时（毫秒）
   */
  private void markTaskRejected(FlowRunTaskVO task, FlowTaskOperateDTO dto, LocalDateTime now, Long durationMs) {
    task.setTaskStatus(FlowTaskStatus.REJECTED.name());
    task.setComment(dto.getComment());
    task.setCompletedAt(now);
    task.setDurationMs(durationMs);
    taskRepository.update(task);
  }

  /**
   * 处理驳回目标为发起人的场景。
   *
   * @param dto 任务操作 DTO
   * @param instance 流程实例 VO
   */
  private void resolveRejectToInitiator(FlowTaskOperateDTO dto, FlowInstanceVO instance) {
    if (Boolean.TRUE.equals(dto.getRejectToInitiator())) {
      String initiatorNodeCode = resolveInitiatorNodeCode(instance.getDefinitionId());
      if (initiatorNodeCode != null) {
        dto.setTargetNodeCode(initiatorNodeCode);
        dto.setTargetNodeCodes(null);
      } else {
        log.warn("[Flow] 退回发起人失败：无法解析开始节点下游第一节点: instanceId={}", instance.getId());
      }
    }
  }

  /**
   * 解析驳回目标节点列表（多节点同退或单节点）。
   *
   * @param task 运行时任务实体
   * @param dto 任务操作 DTO
   * @param instance 流程实例 VO
   * @param mergedVars 合并后的流程变量
   * @return 驳回目标节点列表
   */
  private List<FlowNodeVO> resolveRejectTargets(FlowRunTaskVO task, FlowTaskOperateDTO dto,
      FlowInstanceVO instance, Map<String, Object> mergedVars) {
    boolean multiReject = dto.getTargetNodeCodes() != null && dto.getTargetNodeCodes().size() > 1;
    if (multiReject) {
      return advancer.advanceMulti(instance, task.getNodeCode(), AUDIT_TYPE_REJECT, dto.getTargetNodeCodes(), mergedVars);
    }
    String singleTarget = dto.getTargetNodeCodes() != null && !dto.getTargetNodeCodes().isEmpty()
        ? dto.getTargetNodeCodes().get(0) : dto.getTargetNodeCode();
    return advancer.advance(instance, task.getNodeCode(), AUDIT_TYPE_REJECT, singleTarget, mergedVars);
  }

  /**
   * 驳回到终止状态处理。
   *
   * @param task 运行时任务实体
   * @param instance 流程实例 VO
   * @param dto 任务操作 DTO
   * @param now 当前时间
   */
  private void handleRejectToEnd(FlowRunTaskVO task, FlowInstanceVO instance, FlowTaskOperateDTO dto, LocalDateTime now) {
    instanceRepository.updateStatus(instance.getId(), FlowInstanceStatus.REJECTED.name(),
        null, null, now, instance.getStartAt() == null ? null : Duration.between(instance.getStartAt(), now).toMillis());
    taskRepository.updateStatusByInstance(instance.getId(), FlowTaskStatus.CANCELLED.name());
    notificationService.fireInstanceRejected(instance.getId(), dto.getComment());
    support.audit(task, AUDIT_TYPE_REJECT, dto.getUserId(), null, dto.getComment(), dto.getCommentType());
    if (flowMetrics != null) {
      flowMetrics.incTask(task.getFlowCode(), task.getNodeCode(), METRIC_TASK_REJECTED);
      flowMetrics.recordTaskDuration(task, METRIC_TASK_STATUS_REJECTED);
      flowMetrics.incInstance(instance.getFlowCode(), METRIC_INSTANCE_REJECTED);
      flowMetrics.recordInstanceDuration(instance, METRIC_TASK_STATUS_REJECTED);
    }
  }

  // ============================== 转办 ==============================

  /**
   * 任务转办
   *
   * @param dto 任务操作 DTO（含 taskId、userId、targetUserId 等）
   */
  @Transactional(rollbackFor = Exception.class)
  public void transfer(FlowTaskOperateDTO dto) {
    flowTaskOperateService.transfer(dto);
  }

  // ============================== 委派 ==============================

  /**
   * 任务委派
   *
   * @param dto 任务操作 DTO（含 taskId、userId、targetUserId 等）
   */
  @Transactional(rollbackFor = Exception.class)
  public void delegate(FlowTaskOperateDTO dto) {
    flowTaskOperateService.delegate(dto);
  }

  // ============================== 跳转 ==============================

  /**
   * 自由跳转
   *
   * @param dto 任务操作 DTO（含 taskId、userId、targetNodeCode 等）
   */
  @Transactional(rollbackFor = Exception.class)
  public void jump(FlowTaskOperateDTO dto) {
    flowTaskOperateService.jump(dto);
  }

  // ============================== 取回 ==============================

  /**
   * 取回（已审批后取回）
   *
   * @param hisTaskId 历史任务 ID
   * @param operatorId 取回操作人 ID
   * @param comment 取回原因
   * @return 新创建的任务 ID（取回失败时返回 null）
   */
  @Transactional(rollbackFor = Exception.class)
  public String retract(String hisTaskId, String operatorId, String comment) {
    return flowTaskOperateService.retract(hisTaskId, operatorId, comment);
  }

  // ============================== 催办 ==============================

  /**
   * 实例级催办
   *
   * @param instanceId 流程实例 ID
   * @param operatorId 催办操作人 ID
   * @param comment 催办意见
   * @return 被催办的任务 ID 列表
   */
  public List<String> urge(String instanceId, String operatorId, String comment) {
    return flowTaskUrgeService.urge(instanceId, operatorId, comment);
  }

  /**
   * 节点级催办
   *
   * @param instanceId 流程实例 ID
   * @param nodeCode 节点编码
   * @param operatorId 催办操作人 ID
   * @param comment 催办意见
   * @return 被催办的任务 ID 列表
   */
  public List<String> urgeByNode(
      String instanceId, String nodeCode, String operatorId, String comment) {
    return flowTaskUrgeService.urgeByNode(instanceId, nodeCode, operatorId, comment);
  }

  // ============================== 超时 / 挂起 / 激活 / 取消 ==============================

  /**
   * 标记任务超时
   *
   * @param taskId 参数说明
   * @param reason 参数说明
   */
  @Transactional(rollbackFor = Exception.class)
  public void timeoutTask(String taskId, String reason) {
    flowTaskTimeoutService.timeoutTask(taskId, reason);
  }

  /**
   * 任务级挂起
   *
   * @param taskId 参数说明
   * @param operatorId 参数说明
   * @param reason 参数说明
   */
  @Transactional(rollbackFor = Exception.class)
  public void suspendTask(String taskId, String operatorId, String reason) {
    flowTaskTimeoutService.suspendTask(taskId, operatorId, reason);
  }

  /**
   * 任务级激活
   *
   * @param taskId 参数说明
   * @param operatorId 参数说明
   */
  @Transactional(rollbackFor = Exception.class)
  public void activateTask(String taskId, String operatorId) {
    flowTaskTimeoutService.activateTask(taskId, operatorId);
  }

  /**
   * 取消某实例全部 PENDING 任务
   *
   * @param instanceId 参数说明
   * @param taskStatus 参数说明
   */
  public void cancelByInstance(String instanceId, String taskStatus) {
    flowTaskTimeoutService.cancelByInstance(instanceId, taskStatus);
  }

  // ============================== 内部辅助方法（通过） ==============================

  /**
   * 委派回归处理：被委派人通过后任务回到原办理人
   *
   * @param task 参数说明
   * @param dto 参数说明
   */
  private void handleDelegateReturn(FlowRunTaskVO task, FlowTaskOperateDTO dto) {
    auditService.logDelegateOperation(task, AUDIT_TYPE_DELEGATE_RETURN);
    task.setAssigneeId(String.valueOf(task.getAssignorId()));
    task.setAssigneeName(task.getAssignorName());
    task.setAssignorId(null);
    task.setAssignorName(null);
    task.setTaskStatus(FlowTaskStatus.CLAIMED.name());
    taskRepository.update(task);
    support.audit(
        task, AUDIT_TYPE_DELEGATE_RETURN, dto.getUserId(), null, dto.getComment(), dto.getCommentType());
    log.info("[Flow] 委派回归: taskId={} → 原办理人={}", task.getId(), task.getAssigneeId());
  }

  /**
   * 表单字段权限校验 + P0-3 表单 Schema 校验
   *
   * @param task 参数说明
   * @param variables 参数说明
   * @param instance 参数说明
   */
  private void validateFormFieldPerms(
      FlowRunTaskVO task, Map<String, Object> variables, FlowInstanceVO instance) {
    FlowNodeVO formNode = nodeRepository.findByCode(task.getDefinitionId(), task.getNodeCode()).orElse(null);
    if (formNode == null) {
      return;
    }
    // 字段权限校验
    Map<String, String> fieldPerms = null;
    if (StringUtils.hasText(formNode.getFormFieldsConfig())) {
      fieldPerms = formFieldPermService.parseFieldPerms(formNode.getFormFieldsConfig());
      if (!fieldPerms.isEmpty()) {
        Map<String, Object> existingVars = mergeVariables(instance, Collections.emptyMap());
        formFieldPermService.validateFieldPerms(fieldPerms, variables, existingVars);
      }
    }
    // P0-3: 表单 Schema 校验
    FlowFormSchema schema = formEngineService.getFormSchema(formNode.getExt());
    if (schema != null) {
      formEngineService.validateAndThrow(schema, variables, fieldPerms);
    }
  }

  /**
   * 流程推进
   *
   * @param instance 参数说明
   * @param task 参数说明
   * @param vars 参数说明
   * @param performType 参数说明
   * @param dto 参数说明
   */
  private void advanceProcess(
      FlowInstanceVO instance,
      FlowRunTaskVO task,
      Map<String, Object> vars,
      FlowPerformType performType,
      FlowTaskOperateDTO dto) {
    List<FlowNodeVO> nextNodes = advancer.advance(instance, task.getNodeCode(), AUDIT_TYPE_PASS, null, vars);
    instanceService.generateTasksForNodes(task.getInstanceId(), nextNodes, vars);
    updateInstanceNode(instance, nextNodes);
    notificationService.fireTaskCompleted(task.getId(), AUDIT_TYPE_PASS, vars);
    support.audit(
        task,
        performType.name() + "_PASS_ALL",
        dto.getUserId(),
        null,
        dto.getComment(),
        dto.getCommentType());
    log.info("[Flow] {} 全部通过: taskId={} next={}", performType, task.getId(), nextNodes.size());
  }

  /**
   * 更新实例当前节点
   *
   * @param instance 参数说明
   * @param nextNodes 参数说明
   */
  private void updateInstanceNode(FlowInstanceVO instance, List<FlowNodeVO> nextNodes) {
    if (!nextNodes.isEmpty() && nextNodes.get(0).getNodeType() != FlowNodeType.END.getCode()) {
      instanceRepository.updateStatus(
          instance.getId(),
          instance.getFlowStatus(),
          nextNodes.get(0).getNodeCode(),
          nextNodes.get(0).getNodeName(),
          null,
          null);
    }
  }

  /**
   * 触发个人完成事件
   *
   * <p>会签中某个办理人完成审批后，无论会签是否全部完成，均触发此事件。 业务方可实时跟踪会签进度（如"3/5 人已通过"）。
   *
   * @param task       运行时任务（已更新的 approveFinished 计数）
   * @param dto        操作参数（userId 作为个人审批人）
   * @param variables  合并后的流程变量
   */
  private void firePersonalCompletedEvent(
      FlowRunTaskVO task, FlowTaskOperateDTO dto, Map<String, Object> variables) {
    try {
      String nodeExt = nodeRepository
          .findByCode(task.getDefinitionId(), task.getNodeCode())
          .map(n -> n.getExt())
          .orElse(null);
      int finished = task.getApproveFinished() == null ? 1 : task.getApproveFinished();
      int count = task.getApproveCount() == null ? 1 : task.getApproveCount();
      notificationService.fireTaskPersonalCompleted(task, dto.getUserId(), AUDIT_TYPE_PASS, finished, count,
          nodeExt, variables);
    } catch (Exception e) {
      log.warn("[Flow] 触发个人完成事件失败: taskId={} err={}", task.getId(), e.getMessage());
    }
  }

  /** 合并流程变量：实例已有变量 + dto 增量 */
  private Map<String, Object> mergeVariables(FlowInstanceVO instance, Map<String, Object> extra) {
    if (instance == null || !StringUtils.hasText(instance.getVariable())) {
      return extra == null ? Collections.emptyMap() : extra;
    }
    try {
      Map<String, Object> base = YdszJson.parseMap(instance.getVariable());
      if (extra != null && !extra.isEmpty()) {
        base.putAll(extra);
      }
      return base;
    } catch (Exception e) {
      return extra == null ? Collections.emptyMap() : extra;
    }
  }

  // ============================== 内部辅助方法（驳回） ==============================

  /**
   * P0-1 修复: 退回到发起人 — 解析 startNode 下游第一个审批节点作为退回目标。
   * 
   * <p>原实现直接返回 startNode.getNodeCode()（开始节点本身）， 导致退回后不会生成有意义的待办任务。修正为沿 PASS 出边找到 第一个 APPROVAL
   * 类型节点，找不到时回退到开始节点。
   *
   * @param definitionId 参数说明
   * @return 返回值说明
   */
  private String resolveInitiatorNodeCode(String definitionId) {
    if (definitionCacheService == null || definitionId == null) {
      return null;
    }
    try {
      FlowNodeVO startNode = definitionCacheService.getStartNode(definitionId);
      if (startNode == null) {
        return null;
      }
      // 沿 PASS 出边找下游第一个 APPROVAL 节点
      String found = findFirstApprovalNode(definitionId, startNode.getNodeCode(), new HashSet<>());
      return found != null ? found : startNode.getNodeCode();
    } catch (Exception e) {
      log.warn("[Flow] 解析开始节点下游失败: definitionId={} err={}", definitionId, e.getMessage());
      return null;
    }
  }

  /**
   * P0-1 修复: BFS 遍历，找定义中从指定节点出发可达的第一个 APPROVAL 节点。
   *
   * @param definitionId 流程定义 ID
   * @param startNodeCode 遍历起点
   * @param visited 已访问节点（防环路）
   * @return 第一个 APPROVAL 节点编码，未找到返回 null
   */
  private String findFirstApprovalNode(
      String definitionId, String startNodeCode, Set<String> visited) {
    Queue<String> queue = new ArrayDeque<>();
    queue.add(startNodeCode);
    visited.add(startNodeCode);
    while (!queue.isEmpty()) {
      String currentCode = queue.poll();
      List<FlowSkipVO> skips = definitionCacheService.getSkipsByNodeCode(definitionId, currentCode);
      for (FlowSkipVO skip : skips) {
        String nextCode = skip.getNextNodeCode();
        if (nextCode == null || visited.contains(nextCode)) {
          continue;
        }
        visited.add(nextCode);
        FlowNodeVO nextNode = definitionCacheService.getNodeByCode(definitionId, nextCode);
        if (nextNode != null && nextNode.getNodeType() == FlowNodeType.APPROVAL.getCode()) {
          return nextCode;
        }
        // 跳过 CC/SERVICE/END 等非审批节点，继续 BFS
        if (nextNode != null && nextNode.getNodeType() != FlowNodeType.END.getCode()) {
          queue.add(nextCode);
        }
      }
    }
    return null;
  }
}
