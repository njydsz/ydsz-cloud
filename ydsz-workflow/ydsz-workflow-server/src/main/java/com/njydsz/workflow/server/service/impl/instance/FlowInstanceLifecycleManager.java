package com.njydsz.workflow.server.service.impl.instance;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.feign.assembler.NameAssembler;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.lock.annotation.YdszDistributedLock;
import com.njydsz.common.security.LoginUser;
import com.njydsz.workflow.domain.dto.FlowStartProcessDTO;
import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.domain.enums.FlowNodeType;
import com.njydsz.workflow.domain.repository.FlowAuditLogRepository;
import com.njydsz.workflow.domain.repository.FlowHisTaskRepository;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.repository.FlowNodeRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowAuditLogVO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
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
 *   <li><b>重审（重开）</b>：{@link #reopen} — 重审已结束实例
 *   <li><b>动态追加节点</b>：{@link #appendNode} — 运行中实例动态插入审批节点
 * </ul>
 *
 * <p><b>事务边界：</b>所有写方法开启 {@code @Transactional(rollbackFor = Exception.class)}，
 * 确保「实例 + 任务 + 审计日志 + 事件」原子性。
 *
 * <p><b>并发控制：</b>关键操作通过 {@link YdszDistributedLock} 注解保护。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class FlowInstanceLifecycleManager extends AbstractFlowInstanceLifecycle {

  /** P3-1: 管理员重审权限编码 */
  private static final String PERM_INSTANCE_REOPEN = "workflow:instance:reopen";

  /** 流程变量管理器，负责变量读写与解析 */
  private final FlowInstanceVariableManager variableManager;

  /**
   * 构造函数
   *
   * @param instanceRepository 流程实例仓储
   * @param definitionService 流程定义服务
   * @param advancer 流程推进引擎
   * @param taskService 流程任务服务
   * @param taskRepository 运行时任务仓储
   * @param nodeRepository 流程节点仓储
   * @param flowMetrics Prometheus 指标
   * @param flowTaskSupport 事件支持组件
   * @param subProcessService 子流程服务
   * @param ccService 抄送服务
   * @param autoTriggerService 自动触发服务
   * @param eventSubscriptionService 事件订阅服务
   * @param auditLogRepository 审计日志仓储
   * @param hisTaskRepository 历史任务仓储
   * @param timerService 定时器服务
   * @param nameAssembler 名称解析门面
   * @param variableManager 流程变量管理器
   */
  public FlowInstanceLifecycleManager(
      FlowInstanceRepository instanceRepository,
      FlowDefinitionService definitionService,
      DefaultFlowAdvancer advancer,
      FlowTaskService taskService,
      FlowRunTaskRepository taskRepository,
      FlowNodeRepository nodeRepository,
      FlowMetrics flowMetrics,
      FlowTaskSupport flowTaskSupport,
      FlowSubProcessService subProcessService,
      FlowCcService ccService,
      FlowAutoTriggerService autoTriggerService,
      FlowEventSubscriptionService eventSubscriptionService,
      FlowAuditLogRepository auditLogRepository,
      FlowHisTaskRepository hisTaskRepository,
      FlowTimerService timerService,
      NameAssembler nameAssembler,
      FlowInstanceVariableManager variableManager) {
    super(
        instanceRepository,
        definitionService,
        advancer,
        taskService,
        taskRepository,
        nodeRepository,
        flowMetrics,
        flowTaskSupport,
        subProcessService,
        ccService,
        autoTriggerService,
        eventSubscriptionService,
        auditLogRepository,
        hisTaskRepository,
        timerService,
        nameAssembler);
    this.variableManager = variableManager;
  }

  // ============================== 子类策略实现 ==============================

  @Override
  protected Map<String, Object> parseVariables(String variable) {
    return variableManager.parseVariables(variable);
  }

  @Override
  protected Map<String, Object> getVariables(String instanceId) {
    return variableManager.getVariables(instanceId);
  }

  @Override
  protected FlowInstanceVO saveInstance(FlowInstanceVO instance) {
    return instanceRepository.save(toDto(instance));
  }

  // ============================== 委托方法（保持向后兼容） ==============================

  /**
   * 启动流程实例
   *
   * @param dto 启动参数 DTO
   * @return 流程实例 ID
   */
  public String start(FlowStartProcessDTO dto) {
    return doStartInstance(dto);
  }

  /**
   * 终止流程实例
   *
   * @param instanceId 实例 ID
   * @param reason 终止原因
   */
  public void terminate(String instanceId, String reason) {
    doTerminateInstance(instanceId, reason);
  }

  /**
   * 挂起流程实例
   *
   * @param instanceId 实例 ID
   */
  public void suspend(String instanceId) {
    doSuspendInstance(instanceId);
  }

  /**
   * 激活流程实例
   *
   * @param instanceId 实例 ID
   */
  public void activate(String instanceId) {
    doActivateInstance(instanceId);
  }

  /**
   * 强制完成（推进到结束节点）
   *
   * @param instanceId 实例 ID
   * @param endNodeCode 终止节点编码
   */
  public void complete(String instanceId, String endNodeCode) {
    doCompleteInstance(instanceId, endNodeCode);
  }

  /**
   * 撤回到指定历史节点
   *
   * @param instanceId 实例 ID
   * @param initiatorId 发起人 ID
   * @param targetNodeCode 目标节点编码
   * @return 是否撤回成功
   */
  public boolean recall(String instanceId, String initiatorId, String targetNodeCode) {
    return doRecallInstance(instanceId, initiatorId, targetNodeCode);
  }

  /**
   * 撤回流程（回退到开始节点的下一节点）
   *
   * @param instanceId 实例 ID
   * @param initiatorId 发起人 ID
   * @return 是否撤回成功
   */
  public boolean recall(String instanceId, String initiatorId) {
    return doRecallInstance(instanceId, initiatorId);
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
  public boolean rollback(
      String instanceId, String operatorId, String reason, int maxRollbackDays) {
    return doRollbackInstance(instanceId, operatorId, reason, maxRollbackDays);
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
  public String resubmit(
      String instanceId, String initiatorId, Map<String, Object> variables, String comment) {
    return doResubmitInstance(instanceId, initiatorId, variables, comment);
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
  public String resubmit(
      String instanceId,
      String initiatorId,
      Map<String, Object> variables,
      String comment,
      String redoMode) {
    return doResubmitInstance(instanceId, initiatorId, variables, comment, redoMode);
  }

  /**
   * 设置实例的 dueAt 字段
   *
   * @param instanceId 实例 ID
   * @param dueAt 超时时间
   */
  public void setDueAt(String instanceId, LocalDateTime dueAt) {
    doSetDueAt(instanceId, dueAt);
  }

  // ============================== Manager 独有方法 ==============================

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
          .key("error.workflow.instance.not.completed")
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
        passedNodes.stream()
            .anyMatch(t -> targetNodeCode.equals(String.valueOf(t.getOrDefault("nodeCode", ""))));
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
    saveInstance(instance);

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
    Map<String, Object> variables = getVariables(instanceId);
    taskService.createTask(instanceId, targetNodeVO, variables);

    // 7. 记录重审元信息到 variable JSON
    try {
      Map<String, Object> vars = parseVariables(instance.getVariable());
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
          .key("error.workflow.instance.appendnode.not.running")
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

    Map<String, Object> variables = getVariables(instanceId);
    String taskId = taskService.createTask(instanceId, appendedNode, variables);

    Map<String, Object> appendedInfo = new HashMap<>();
    appendedInfo.put("nodeCode", appendedNodeCode);
    appendedInfo.put("nodeName", nodeName);
    appendedInfo.put("assigneeType", assigneeType);
    appendedInfo.put("assigneeId", assigneeId);
    appendedInfo.put("operatorId", operatorId);
    appendedInfo.put("comment", comment);
    appendedInfo.put("createdAt", LocalDateTime.now().toString());

    Map<String, Object> vars = getVariables(instanceId);
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

    log.info(
        "[Flow] 动态追加节点: instanceId={} nodeCode={} taskId={}",
        instanceId,
        appendedNodeCode,
        taskId);
    return taskId;
  }
}
