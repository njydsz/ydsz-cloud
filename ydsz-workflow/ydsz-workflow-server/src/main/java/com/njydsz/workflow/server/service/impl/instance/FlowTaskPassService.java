package com.njydsz.workflow.server.service.impl.instance;

import java.time.LocalDateTime;
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
import com.njydsz.workflow.domain.dto.FlowTaskOperateDTO;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.infra.entity.FlowNodeDO;
import com.njydsz.workflow.infra.entity.FlowRunTaskDO;
import com.njydsz.workflow.domain.enums.FlowNodeType;
import com.njydsz.workflow.domain.enums.FlowPerformType;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.repository.FlowNodeRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.server.engine.impl.DefaultFlowAdvancer;
import com.njydsz.workflow.server.form.FlowFormEngineService;
import com.njydsz.workflow.server.form.FlowFormSchema;
import com.njydsz.workflow.server.metrics.FlowMetrics;
import com.njydsz.workflow.server.service.FlowAttachmentService;
import com.njydsz.workflow.server.service.FlowFormFieldPermService;
import com.njydsz.workflow.server.service.FlowInstanceService;
import com.njydsz.workflow.server.service.FlowTodoCountPushService;
import com.njydsz.workflow.server.service.impl.CountersignStrategy;
import com.njydsz.workflow.server.service.impl.CountersignStrategyFactory;

/**
 * 流程任务通过服务实现。
 *
 * <p>封装任务「同意/通过」操作：状态机校验、会签策略评估（顺序/或签/票决）、
 *
 * <p>下一节点路由、自动跳过规则、并行网关合流。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskPassService {

  /** 运行时任务仓储，查询/更新任务状态 */
  private final FlowRunTaskRepository taskRepository;

  /** 流程实例仓储，查询实例状态和流程变量 */
  private final FlowInstanceRepository instanceRepository;

  /** 流程节点仓储，查询节点配置 */
  private final FlowNodeRepository nodeRepository;

  /** MapStruct 转换器，用于 VO ↔ DO 转换 */
  private final WorkflowConverter converter;

  /** 流程推进引擎，会签完成后推进到下一节点 */
  private final DefaultFlowAdvancer advancer;

  /** 流程实例服务，更新实例状态和变量 */
  private final FlowInstanceService instanceService;

  /** 跨子 Service 共享的任务校验/审计/事件辅助 */
  private final FlowTaskSupport support;

  /** 任务事件通知服务，推送任务通过通知 */
  private final FlowTaskNotificationService notificationService;

  /** 委派代理审计服务，记录代理人审批操作 */
  private final FlowTaskAuditService auditService;

  /** 会签策略工厂，根据 performType 选择会签策略 */
  private final CountersignStrategyFactory strategyFactory;

  /** 表单字段权限服务，校验表单字段读写权限 */
  private final FlowFormFieldPermService formFieldPermService;

  /** P0-3: 表单引擎服务 */
  private final FlowFormEngineService formEngineService;

  /** P1-6: 审批附件服务 */
  private final FlowAttachmentService attachmentService;

  /** P1-7: 待办数 WebSocket 推送服务 */
  @Lazy private final FlowTodoCountPushService todoCountPushService;

  /** P2-3: Prometheus 指标（可能为 null：测试环境） */
  private final FlowMetrics flowMetrics;

  /**
   * 通过任务。
   *
   * @param dto 操作参数（taskId/userId/comment/variables/attachments）
   */
  @Transactional(rollbackFor = Exception.class)
  public void pass(FlowTaskOperateDTO dto) {
    FlowRunTaskDO task = support.getTaskOrThrow(dto.getTaskId());
    if (FlowTaskStatus.valueOf(task.getTaskStatus()).isFinished()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.msg_7f4098fb")
          .params(task.getTaskStatus())
          .build();
    }
    Map<String, Object> variables =
        dto.getVariables() == null ? Collections.emptyMap() : dto.getVariables();
    FlowInstanceVO instance = instanceRepository.findById(task.getInstanceId()).orElse(null);
    Map<String, Object> mergedVars = mergeVariables(instance, variables);

    // P0-2: 表单字段权限校验
    validateFormFieldPerms(task, dto.getVariables(), instance);

    // P1-10: 委派回归 — 被委派人通过后任务回到原办理人
    if (FlowTaskStatus.DELEGATED.name().equals(task.getTaskStatus())
        && task.getAssignorId() != null) {
      handleDelegateReturn(task, dto);
      return;
    }

    FlowPerformType performType =
        FlowPerformType.valueOf(
            task.getPerformType() == null ? FlowPerformType.OR.name() : task.getPerformType());

    // 标记当前用户已处理（ydsz_flow_user）
    if (dto.getUserId() != null) {
      taskRepository.markProcessed(
          task.getId(), String.valueOf(dto.getUserId()), dto.getComment(), LocalDateTime.now());
    }

    // P1-6: 保存审批附件
    attachmentService.saveBatch(
        task.getInstanceId(),
        task.getId(),
        task.getNodeCode(),
        "TASK",
        dto.getUserId(),
        dto.getUserName(),
        dto.getAttachments(),
        task.getTenantId(),
        task.getProviderTraceId());

    // 策略模式处理会签
    CountersignStrategy strategy = strategyFactory.getStrategy(performType);
    strategy.preCheck(task, dto);
    strategy.onUserPassed(task, dto);

    // P2-38: 触发个人完成事件（会签中单个办理人完成审批，无论会签是否全部完成）
    firePersonalCompletedEvent(task, dto, mergedVars);

    boolean shouldAdvance = strategy.shouldAdvance(task);
    if (shouldAdvance) {
      strategy.onAdvance(task, dto);
      advanceProcess(instance, task, mergedVars, performType, dto);
    } else {
      support.audit(
          task,
          performType.name() + "_PASS",
          dto.getUserId(),
          null,
          dto.getComment(),
          dto.getCommentType());
      log.info(
          "[Flow] {} 部分通过: taskId={} finished={}/{}",
          performType,
          task.getId(),
          task.getApproveFinished(),
          task.getApproveCount());
    }

    // P1-7: WebSocket 推送任务完成
    if (todoCountPushService != null) {
      todoCountPushService.pushTaskCompleted(task, dto.getUserId());
    }
    // P2-3: Prometheus 指标
    if (flowMetrics != null) {
      flowMetrics.incTask(task.getFlowCode(), task.getNodeCode(), "passed");
      flowMetrics.recordTaskDuration(converter.entityToVO(task), "PASSED");
    }
  }

  /** 委派回归处理：被委派人通过后任务回到原办理人 */
  private void handleDelegateReturn(FlowRunTaskDO task, FlowTaskOperateDTO dto) {
    auditService.logDelegateOperation(task, "DELEGATE_RETURN");
    task.setAssigneeId(String.valueOf(task.getAssignorId()));
    task.setAssigneeName(task.getAssignorName());
    task.setAssignorId(null);
    task.setAssignorName(null);
    task.setTaskStatus(FlowTaskStatus.CLAIMED.name());
    taskRepository.update(converter.entityToVO(task));
    support.audit(
        task, "DELEGATE_RETURN", dto.getUserId(), null, dto.getComment(), dto.getCommentType());
    log.info("[Flow] 委派回归: taskId={} → 原办理人={}", task.getId(), task.getAssigneeId());
  }

  /** 表单字段权限校验 + P0-3 表单 Schema 校验 */
  private void validateFormFieldPerms(
      FlowRunTaskDO task, Map<String, Object> variables, FlowInstanceVO instance) {
    FlowNodeDO formNode = nodeRepository.findByCode(task.getDefinitionId(), task.getNodeCode()).map(converter::entityToDO).orElse(null);
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

  /** 流程推进 */
  private void advanceProcess(
      FlowInstanceVO instance,
      FlowRunTaskDO task,
      Map<String, Object> vars,
      FlowPerformType performType,
      FlowTaskOperateDTO dto) {
    List<FlowNodeDO> nextNodes = advancer.advance(instance, task.getNodeCode(), "PASS", null, vars);
    instanceService.generateTasksForNodes(task.getInstanceId(), nextNodes, vars);
    updateInstanceNode(instance, nextNodes);
    notificationService.fireTaskCompleted(task.getId(), "PASS", vars);
    support.audit(
        task,
        performType.name() + "_PASS_ALL",
        dto.getUserId(),
        null,
        dto.getComment(),
        dto.getCommentType());
    log.info("[Flow] {} 全部通过: taskId={} next={}", performType, task.getId(), nextNodes.size());
  }

  /** 更新实例当前节点 */
  private void updateInstanceNode(FlowInstanceVO instance, List<FlowNodeDO> nextNodes) {
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
      FlowRunTaskDO task, FlowTaskOperateDTO dto, Map<String, Object> variables) {
    try {
      String nodeExt = nodeRepository
          .findByCode(task.getDefinitionId(), task.getNodeCode())
          .map(n -> n.getExt())
          .orElse(null);
      int finished = task.getApproveFinished() == null ? 1 : task.getApproveFinished();
      int count = task.getApproveCount() == null ? 1 : task.getApproveCount();
      notificationService.fireTaskPersonalCompleted(task, dto.getUserId(), "PASS", finished, count,
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
}
