package com.njydsz.workflow.server.service.impl.instance;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.domain.dto.FlowRunTaskDTO;
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.enums.FlowInstanceStatus;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.domain.repository.FlowHisTaskRepository;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.repository.FlowNodeRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowHisTaskVO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.engine.FlowDefinitionCacheService;
import com.njydsz.workflow.server.engine.FlowNodeExt;
import com.njydsz.workflow.server.metrics.FlowMetrics;

/**
 * 流程任务操作服务实现。
 *
 * <p>提供任务级别的转办/委派/加签/减签/沟通/传阅等运营操作，
 *
 * <p>每种操作均产生审计记录与流程轨迹。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskOperateService {

  /** 运行时任务仓储，查询/更新任务状态 */
  private final FlowRunTaskRepository taskRepository;

  /** 历史任务仓储，查询已归档任务（撤回时使用） */
  private final FlowHisTaskRepository hisTaskRepository;

  /** 流程实例仓储，查询实例状态 */
  private final FlowInstanceRepository instanceRepository;

  /** 流程节点仓储，查询节点配置（跳转白名单校验） */
  private final FlowNodeRepository nodeRepository;


  /** 跨子 Service 共享的任务校验/审计/事件辅助 */
  private final FlowTaskSupport support;

  /** 任务归档服务，完成任务后写入历史任务表 */
  private final FlowTaskArchiveService archiveService;

  /** 任务事件通知服务，推送转办/委派/撤回通知 */
  private final FlowTaskNotificationService notificationService;

  /** 任务创建服务（用于 jump 后在目标节点创建新任务） */
  private final FlowTaskCreateService taskCreateService;

  /** P1-2: 流程定义缓存服务（解析 startNode 下游第一节点，撤回时使用） */
  @Lazy private final FlowDefinitionCacheService definitionCacheService;

  /** P2-3: Prometheus 指标（可能为 null：测试环境） */
  private final FlowMetrics flowMetrics;

  // ============================== 转办 ==============================

  /**
   * 转办：将任务办理人换为他人。
   * 
   * <p>原办理人变为 assignorId，新办理人变为 assigneeId，状态保持 CLAIMED。
   *
   * @param dto 参数说明
   */
  @Transactional(rollbackFor = Exception.class)
  public void transfer(FlowTaskOperateDTO dto) {
    if (dto.getTargetUserId() == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.transfer.target.user.required")
          .build();
    }
    FlowRunTaskVO task = support.getTaskOrThrow(dto.getTaskId());
    String originalAssignorId = parseAssignorId(task.getAssigneeId());
    String originalAssignorName = task.getAssigneeName();
    task.setAssigneeId(String.valueOf(dto.getTargetUserId()));
    task.setAssigneeName(dto.getTargetUserName());
    task.setAssignorId(originalAssignorId);
    task.setAssignorName(originalAssignorName);
    task.setTaskStatus(FlowTaskStatus.CLAIMED.name());
    taskRepository.update(task);
    support.audit(task, "TRANSFER", dto.getUserId(), dto.getTargetUserId(), dto.getComment());
    log.info("[Flow] 转办任务: taskId={} → userId={}", task.getId(), dto.getTargetUserId());
    if (flowMetrics != null) {
      flowMetrics.incTask(task.getFlowCode(), task.getNodeCode(), "transferred");
    }
    // P2-34: 触发 onTaskTransferred 事件
    support.fireEvent(
        l -> l.onTaskTransferred(task.getId(), originalAssignorId, dto.getTargetUserId()),
        task.getId());
    // P2-35: 发布 Spring 异步事件
    support.publishWorkflowEvent("TASK_TRANSFERRED", task.getInstanceId(), task.getId());
  }

  // ============================== 委派 ==============================

  /**
   * 委派：被委派人处理后任务回到原办理人。
   * 
   * <p>原办理人变为 assignorId，新办理人变为 assigneeId，任务状态置为 DELEGATED。 被委派人通过时（FlowTaskPassService）会检测
   * DELEGATED 状态，自动回归原办理人。
   *
   * @param dto 参数说明
   */
  @Transactional(rollbackFor = Exception.class)
  public void delegate(FlowTaskOperateDTO dto) {
    if (dto.getTargetUserId() == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.delegate.target.user.required")
          .build();
    }
    FlowRunTaskVO task = support.getTaskOrThrow(dto.getTaskId());
    String originalAssigneeId = parseAssignorId(task.getAssigneeId());
    String originalAssigneeName = task.getAssigneeName();
    task.setAssignorId(originalAssigneeId);
    task.setAssignorName(originalAssigneeName);
    task.setAssigneeId(String.valueOf(dto.getTargetUserId()));
    task.setAssigneeName(dto.getTargetUserName());
    task.setTaskStatus(FlowTaskStatus.DELEGATED.name());
    taskRepository.update(task);
    support.audit(task, "DELEGATE", dto.getUserId(), dto.getTargetUserId(), dto.getComment());
    log.info(
        "[Flow] 委派任务: taskId={} → 被委派人={} (处理完回到 {})",
        task.getId(),
        dto.getTargetUserId(),
        originalAssigneeName);
    if (flowMetrics != null) {
      flowMetrics.incTask(task.getFlowCode(), task.getNodeCode(), "delegated");
    }
    // P2-34: 触发 onTaskDelegated 事件
    support.fireEvent(
        l -> l.onTaskDelegated(task.getId(), originalAssigneeId, dto.getTargetUserId()),
        task.getId());
    // P2-35: 发布 Spring 异步事件
    support.publishWorkflowEvent("TASK_DELEGATED", task.getInstanceId(), task.getId());
  }

  // ============================== 自由跳转 ==============================

  /**
   * 自由跳转：完成当前任务，强制跳转到任意节点。
   * 
   * <p>GAP-P2-9: 节点级 freeJump 白名单校验（仅自由流操作 action=JUMP 时启用）。 历史管理员强制跳转（无 action 字段或 action !=
   * JUMP）保持原有放行语义。
   *
   * @param dto 参数说明
   */
  @Transactional(rollbackFor = Exception.class)
  public void jump(FlowTaskOperateDTO dto) {
    FlowRunTaskVO task = support.getTaskOrThrow(dto.getTaskId());
    if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.task.already.handled")
          .params(task.getTaskStatus())
          .build();
    }
    if (!StringUtils.hasText(dto.getTargetNodeCode())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.jump.target.node.required")
          .build();
    }
    FlowInstanceVO instance = instanceRepository.findById(task.getInstanceId()).orElse(null);
    if (instance == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.instance.not.found")
          .params(task.getInstanceId())
          .build();
    }
    // 校验目标节点存在
    FlowNodeVO targetNode = nodeRepository.findByCode(task.getDefinitionId(), dto.getTargetNodeCode()).orElse(null);
    if (targetNode == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.jump.target.node.not.found")
          .params(dto.getTargetNodeCode())
          .build();
    }
    // GAP-P2-9: 节点级 freeJump 白名单校验
    if ("JUMP".equals(dto.getAction()) && !isFreeJumpEnabled(targetNode)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message(String.format("目标节点未开启自由跳转白名单: nodeCode=%s", dto.getTargetNodeCode()))
          .build();
    }
    // 完成当前任务
    archiveService.completeAndArchive(task, dto.getComment());
    // 取消同实例其他 PENDING 任务
    taskRepository.updateStatusByInstance(instance.getId(), FlowTaskStatus.CANCELLED.name());
    // 更新实例当前节点为目标节点
    instanceRepository.updateStatus(
        instance.getId(),
        instance.getFlowStatus(),
        targetNode.getNodeCode(),
        targetNode.getNodeName(),
        null,
        null);
    // 在目标节点创建新任务
    Map<String, Object> vars = mergeVariables(instance, dto.getVariables());
    taskCreateService.createTask(instance.getId(), targetNode, vars, dto.getTargetAssignees());
    // 触发任务完成事件
    notificationService.fireTaskCompleted(task.getId(), "JUMP", vars);
    support.audit(task, "JUMP", dto.getUserId(), null, dto.getComment());
    log.info(
        "[Flow] 自由跳转: taskId={} → targetNode={} explicitAssignees={}",
        task.getId(),
        dto.getTargetNodeCode(),
        dto.getTargetAssignees() != null ? dto.getTargetAssignees().size() : 0);
    // P2-34: 触发 onTaskJumped 事件
    support.fireEvent(
        l -> l.onTaskJumped(task.getId(), task.getNodeCode(), dto.getTargetNodeCode()),
        task.getId());
    // P2-35: 发布 Spring 异步事件
    support.publishWorkflowEvent("TASK_JUMPED", instance.getId(), task.getId());
  }

  // ============================== 撤回（取回） ==============================

  /**
   * P1-3: 取回 — 审批人已审批后，在下一节点未处理前，把自己的审批撤回。
   * 
   * <p>对标钉钉/飞书"取回"。审批人 PASS 后下一节点待办尚未处理时， 可取回自己的审批：取消下一节点待办，在本节点重新生成 PENDING 任务。
   *
   * @param hisTaskId 参数说明
   * @param operatorId 参数说明
   * @param comment 参数说明
   * @return 返回值说明
   */
  @Transactional(rollbackFor = Exception.class)
  public String retract(String hisTaskId, String operatorId, String comment) {
    // 1. 查询并校验历史任务
    FlowHisTaskVO hisTask = findHisTaskOrThrow(hisTaskId);
    validateRetractPermission(hisTask, operatorId);

    // 2. 校验实例存在且为 RUNNING
    FlowInstanceVO instance = findInstanceOrThrow(hisTask.getInstanceId());
    validateInstanceRunning(instance);

    // 3. 校验：下一节点待办必须全部为 PENDING（未被 CLAIMED/COMPLETED）
    validateNextTasksAllPending(instance.getId());

    // 4. 取消下一节点待办
    taskRepository.updateStatusByInstance(instance.getId(), FlowTaskStatus.CANCELLED.name());

    // 5. 重新生成本节点的 PENDING 任务
    FlowRunTaskVO newTask = recreateRetractTask(hisTask, instance, comment);

    // 6. 更新实例 currentNodeCode 回退到本节点
    instanceRepository.updateStatus(instance.getId(), null, hisTask.getNodeCode(), hisTask.getNodeName(), null, null);

    // 7. 审计日志
    support.audit(newTask, "RETRACT", operatorId, null,
        "取回审批" + (StringUtils.hasText(comment) ? "：" + comment : ""));

    // 8. 标记历史任务为 RETRACTED
    markHisTaskRetracted(hisTask.getId(), comment);

    // 9. Prometheus 指标
    if (flowMetrics != null) {
      flowMetrics.incInstance(instance.getFlowCode(), "recalled");
    }

    log.info("[Flow] 取回审批: instanceId={} hisTaskId={} operatorId={} nodeCode={} newTaskId={}",
        instance.getId(), hisTaskId, operatorId, hisTask.getNodeCode(), newTask.getId());
    return newTask.getId();
  }

  /**
   * 根据 ID 查询历史任务，不存在则抛出 NOT_FOUND。
   *
   * @param hisTaskId 参数说明
   * @return 返回值说明
   */
  private FlowHisTaskVO findHisTaskOrThrow(String hisTaskId) {
    FlowHisTaskVO hisTask = hisTaskRepository.findById(hisTaskId).orElse(null);
    if (hisTask == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.histask.not.found")
          .params(hisTaskId)
          .build();
    }
    return hisTask;
  }

  /**
   * 校验取回权限：历史任务状态为 COMPLETED 且操作人为办理人。
   *
   * @param hisTask 参数说明
   * @param operatorId 参数说明
   */
  private void validateRetractPermission(FlowHisTaskVO hisTask, String operatorId) {
    if (!FlowTaskStatus.COMPLETED.name().equals(hisTask.getTaskStatus())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.histask.status.invalid")
          .params(hisTask.getTaskStatus())
          .build();
    }
    if (!hisTask.getAssigneeId().equals(operatorId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.FORBIDDEN)
          .message("error.workflow.retract.no.permission")
          .build();
    }
  }

  /**
   * 根据 ID 查询流程实例，不存在则抛出 NOT_FOUND。
   *
   * @param instanceId 参数说明
   * @return 返回值说明
   */
  private FlowInstanceVO findInstanceOrThrow(String instanceId) {
    return instanceRepository.findById(instanceId)
        .orElseThrow(() -> SysException.builder()
            .resultCode(YdszResultCode.NOT_FOUND)
            .key("error.workflow.instance.not.found")
            .params(instanceId)
            .build());
  }

  /**
   * 校验流程实例为 RUNNING 状态。
   *
   * @param instance 参数说明
   */
  private void validateInstanceRunning(FlowInstanceVO instance) {
    if (!FlowInstanceStatus.RUNNING.name().equals(instance.getFlowStatus())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.instance.status.invalid")
          .params(instance.getFlowStatus())
          .build();
    }
  }

  /**
   * 校验下一节点待办全部为 PENDING（未被 CLAIMED/COMPLETED）。
   *
   * @param instanceId 参数说明
   */
  private void validateNextTasksAllPending(String instanceId) {
    List<FlowRunTaskVO> pendingTasks = taskRepository.findPendingByInstance(instanceId);
    boolean anyProcessed = pendingTasks.stream()
        .anyMatch(t -> FlowTaskStatus.CLAIMED.name().equals(t.getTaskStatus())
            || FlowTaskStatus.COMPLETED.name().equals(t.getTaskStatus()));
    if (anyProcessed) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.retract.next.tasks.processed")
          .build();
    }
  }

  /**
   * 重新生成取回后的 PENDING 任务（复用历史任务的元数据）。
   *
   * @param hisTask 参数说明
   * @param instance 参数说明
   * @param comment 参数说明
   * @return 返回值说明
   */
  private FlowRunTaskVO recreateRetractTask(FlowHisTaskVO hisTask, FlowInstanceVO instance, String comment) {
    FlowRunTaskDTO dto = new FlowRunTaskDTO();
    dto.setInstanceId(instance.getId());
    dto.setFlowCode(instance.getFlowCode());
    dto.setDefinitionId(instance.getDefinitionId());
    dto.setNodeCode(hisTask.getNodeCode());
    dto.setNodeName(hisTask.getNodeName());
    dto.setNodeType(hisTask.getNodeType());
    dto.setBusinessType(instance.getBusinessType());
    dto.setBusinessId(instance.getBusinessId());
    dto.setBusinessNo(instance.getBusinessNo());
    dto.setFlowName(instance.getFlowName());
    dto.setTitle(instance.getTitle());
    dto.setPermissionFlag(null);
    dto.setPerformType(hisTask.getPerformType());
    dto.setApproveCount(hisTask.getApproveCount() == null ? 1 : hisTask.getApproveCount());
    dto.setApproveFinished(0);
    dto.setTaskStatus(FlowTaskStatus.PENDING.name());
    dto.setAssigneeType(hisTask.getAssigneeType());
    dto.setAssigneeId(hisTask.getAssigneeId());
    dto.setAssigneeName(hisTask.getAssigneeName());
    dto.setTenantId(instance.getTenantId());
    dto.setProviderTraceId(instance.getProviderTraceId());
    dto.setComment(comment);
    return taskRepository.save(dto);
  }

  /**
   * 标记历史任务为 RETRACTED 状态。
   *
   * @param hisTaskId 参数说明
   * @param comment 参数说明
   */
  private void markHisTaskRetracted(String hisTaskId, String comment) {
    FlowHisTaskVO update = new FlowHisTaskVO();
    update.setId(hisTaskId);
    update.setTaskStatus("RETRACTED");
    update.setComment("已取回" + (StringUtils.hasText(comment) ? "：" + comment : ""));
    hisTaskRepository.save(update);
  }

  // ============================== 私有辅助 ==============================

  /**
   * 解析 assigneeId 中的真实用户 ID。
   *
   * @param assigneeId 参数说明
   * @return 返回值说明
   */
  private String parseAssignorId(String assigneeId) {
    if (assigneeId == null || !assigneeId.matches("\\d+")) {
      return null;
    }
    return assigneeId;
  }

  /**
   * GAP-P2-9: 判断目标节点是否开启自由跳转白名单。
   *
   * @param node 参数说明
   * @return 返回值说明
   */
  private boolean isFreeJumpEnabled(FlowNodeVO node) {
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

  /** 合并流程变量：实例已有变量 + dto 增量。 */
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

  /** 解析节点 ext JSON 配置（委托给 FlowNodeExt 统一实现）。 */
  private Map<String, Object> parseExtConfig(String ext) {
    return FlowNodeExt.parseSafe(ext);
  }
}
