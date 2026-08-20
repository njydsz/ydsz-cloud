package com.njydsz.workflow.server.listener;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.socket.push.RealtimePushTemplate;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.repository.FlowRunTaskRepository;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;
import com.njydsz.workflow.server.engine.FlowEventListener;
import com.njydsz.workflow.server.engine.FlowWorkflowEvent;
import com.njydsz.workflow.server.queue.FlowQueuePublisher;
import com.njydsz.workflow.server.service.FlowNotificationService;
import com.njydsz.workflow.server.service.FlowSubProcessService;

/**
 * 项目立项流程事件监听器（业务侧示例 + 站内信触发器）
 *
 * <p>P2-35: 异步监听 FlowWorkflowEvent，解耦主流程事务。
 *
 * <p>P0-1: 在关键生命周期埋点调用 FlowNotificationService，触发站内信触达。
 *
 * <p>P0-7: 立项状态联动由原 InitiationFeignClient 同步调用迁移至消息队列异步路径， 通过 {@link
 * FlowQueuePublisher#publish(String, Map)} 发布 INITIATION_STATUS_SYNC 事件到 {@code ydsz:flow:event}
 * 通道，由 project 模块订阅消费实现状态同步。
 *
 * <p>本监听器兼任两层职责：
 *
 * <ol>
 *   <li>业务流程联动（通过 MQ 发布立项状态变更事件，跨服务异步解耦）
 *   <li>通知触达（对标用友 BPM / 钉钉审批的实时通知能力）
 * </ol>
 *
 * <p><b>架构合规说明（v2.23 DDD 分层规范修复）：</b>通过 domain 层 Repository 接口访问数据，
 * 禁止 server 层直接注入 infra Mapper 或直接引用 infra.entity（符合 §34.2.3 / §34.2.1）。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component("projectInitiationFlowListener")
@RequiredArgsConstructor
public class ProjectInitiationFlowListener implements FlowEventListener {

  /** 立项业务键前缀（见 InitiationServiceImpl#startProcess: REMI_INIT_ + id） */
  private static final String INIT_BIZ_KEY_PREFIX = "REMI_INIT_";

  /** P0-7: 立项状态联动 MQ 事件类型 */
  private static final String EVENT_INITIATION_STATUS_SYNC = "INITIATION_STATUS_SYNC";

  /** P0-7: 立项状态联动动作枚举（与 project 模块消费方约定） */
  private static final String ACTION_MARK_PROCESSING = "markProcessing";

  private static final String ACTION_MARK_APPROVED = "markApproved";
  private static final String ACTION_MARK_REJECTED = "markRejected";

  private final FlowNotificationService notificationService;
  private final FlowInstanceRepository instanceRepository;
  private final FlowRunTaskRepository runTaskRepository;

  /** P1-3: 子流程服务（监听器作为子流程完成回调的入口） */
  private final FlowSubProcessService subProcessService;

  /** P2-7: 统一推送模板（直接推送，替代 Feign 中转 message 模块） */
  private final RealtimePushTemplate pushTemplate;

  /** P0-7: 工作流 MQ 发布者（发布立项状态联动事件） */
  private final FlowQueuePublisher queuePublisher;

  @Override
  public void onInstanceStart(String instanceId, Map<String, Object> variables) {
    log.info(
        "[FlowListener] 立项流程启动: instanceId={} vars={}",
        instanceId,
        variables == null ? Collections.emptySet() : variables.keySet());
    // P0-7: 流程启动 → 发布立项状态联动事件（markProcessing）到 MQ
    Optional<FlowInstanceVO> instanceOpt = instanceId == null ? Optional.empty() : instanceRepository.findById(instanceId);
    String initiationId = instanceOpt.map(this::resolveInitiationId).orElse(null);
    if (initiationId != null) {
      publishInitiationStatusSync(initiationId, ACTION_MARK_PROCESSING, instanceId, instanceOpt.orElse(null));
    }
  }

  @Override
  public void onTaskCreated(String taskId) {
    // P0-1: 给当前办理人发送待办通知
    if (taskId == null) {
      return;
    }
    Optional<FlowRunTaskVO> taskOpt = runTaskRepository.findById(taskId);
    if (taskOpt.isEmpty()) {
      return;
    }
    FlowRunTaskVO task = taskOpt.get();
    String assigneeId = task.getAssigneeId();
    if (assigneeId == null) {
      return;
    }
    String title = "您有一个新的审批待办";
    String content =
        String.format(
            "【%s】 %s - %s 待您审批",
            nullSafe(task.getFlowName()), nullSafe(task.getTitle()), nullSafe(task.getNodeName()));
    notificationService.notify("INAPP", assigneeId, title, content, "WORKFLOW_TASK", "INFO");
    // P1-7: 推送实时消息给当前办理人（IM / WebSocket 渠道）
    pushImNotification(assigneeId, title, content, taskId);
  }

  @Override
  public void onTaskCompleted(String taskId, String action, Map<String, Object> variables) {
    log.info("[FlowListener] 立项任务完成: taskId={} action={}", taskId, action);
    // 审批轨迹与驳回通知由 onInstanceCompleted / onInstanceRejected 统一处理，
    // 此处仅记录任务级审计日志，避免重复触达。
  }

  @Override
  public void onInstanceCompleted(String instanceId) {
    log.info("[FlowListener] 立项流程完成: instanceId={}", instanceId);
    // P0-1: 通知发起人流程已完成
    if (instanceId == null) {
      return;
    }
    Optional<FlowInstanceVO> instanceOpt = instanceRepository.findById(instanceId);
    if (instanceOpt.isEmpty() || instanceOpt.get().getInitiatorId() == null) {
      return;
    }
    FlowInstanceVO instance = instanceOpt.get();
    // P1-3: 子流程完成 → 回调父流程
    if (instance.getParentInstanceId() != null) {
      try {
        subProcessService.onSubProcessCompleted(instanceId);
      } catch (Exception e) {
        log.error(
            "[FlowListener] 子流程完成回调父流程失败: child={} parent={} err={}",
            instanceId,
            instance.getParentInstanceId(),
            e.getMessage(),
            e);
      }
    }
    notificationService.notify(
        "INAPP",
        instance.getInitiatorId(),
        "您的审批已通过",
        String.format(
            "【%s】 您发起的 %s 已审批通过", nullSafe(instance.getFlowName()), nullSafe(instance.getTitle())),
        "WORKFLOW_COMPLETED",
        "INFO");
    // P0-7: 流程通过 → 发布立项状态联动事件（markApproved）到 MQ
    String initiationId = resolveInitiationId(instance);
    if (initiationId != null) {
      publishInitiationStatusSync(initiationId, ACTION_MARK_APPROVED, instanceId, instance);
    }
  }

  @Override
  public void onInstanceRejected(String instanceId, String reason) {
    log.info("[FlowListener] 立项流程驳回: instanceId={} reason={}", instanceId, reason);
    // P0-1: 通知发起人流程已驳回
    if (instanceId == null) {
      return;
    }
    Optional<FlowInstanceVO> instanceOpt = instanceRepository.findById(instanceId);
    if (instanceOpt.isEmpty() || instanceOpt.get().getInitiatorId() == null) {
      return;
    }
    FlowInstanceVO instance = instanceOpt.get();
    // P1-3: 子流程驳回 → 同步父流程驳回
    if (instance.getParentInstanceId() != null) {
      try {
        subProcessService.onSubProcessTerminated(instanceId, reason, false);
      } catch (Exception e) {
        log.error(
            "[FlowListener] 子流程驳回同步父流程失败: child={} parent={} err={}",
            instanceId,
            instance.getParentInstanceId(),
            e.getMessage(),
            e);
      }
    }
    notificationService.notify(
        "INAPP",
        instance.getInitiatorId(),
        "您的审批被驳回",
        String.format(
            "【%s】 您发起的 %s 已被驳回%s",
            nullSafe(instance.getFlowName()),
            nullSafe(instance.getTitle()),
            reason == null || reason.isBlank() ? "" : "，原因：" + reason),
        "WORKFLOW_REJECTED",
        "WARN");
    // P0-7: 流程驳回 → 发布立项状态联动事件（markRejected）到 MQ
    String initiationId = resolveInitiationId(instance);
    if (initiationId != null) {
      publishInitiationStatusSync(initiationId, ACTION_MARK_REJECTED, instanceId, instance, reason);
    }
  }

  @Override
  public void onError(String instanceId, Throwable t) {
    log.error("[FlowListener][ALERT] 立项流程异常: instanceId={}", instanceId, t);
    // P0-7: 异常恢复 —— 重新发布 markProcessing 事件让消费方有机会恢复立项状态
    if (instanceId == null) {
      return;
    }
    Optional<FlowInstanceVO> instanceOpt = instanceRepository.findById(instanceId);
    String initiationId = instanceOpt.map(this::resolveInitiationId).orElse(null);
    if (initiationId != null) {
      publishInitiationStatusSync(initiationId, ACTION_MARK_PROCESSING, instanceId, instanceOpt.orElse(null));
    }
  }

  // ============================== P2-35: 异步事件监听 ==============================

  /**
   * P2-35: 异步监听 FlowWorkflowEvent，解耦主流程事务
   *
   * <p>通过 ApplicationEventPublisher 发布的事件在此异步处理， 不影响主流程事务提交与性能。
   *
   * @param event 工作流事件
   */
  @EventListener
  @Async("auditExecutor")
  public void onFlowWorkflowEvent(FlowWorkflowEvent event) {
    log.info(
        "[FlowListener] 异步事件: type={} instanceId={} taskId={}",
        event.getEventType(),
        event.getInstanceId(),
        event.getTaskId());
    // 事件分发由 onTaskUrged/onInstanceTerminated 等具体 default 方法处理；
    // 这里保留异步通道，便于后续扩展（IM 推送、监控埋点等）。
  }

  // ============================== P0-1: 关键事件通知触发 ==============================

  @Override
  public void onTaskUrged(String instanceId, String taskId) {
    // P0-1: 催办通知：实例级催办推送给所有当前待办办理人
    if (instanceId == null) {
      return;
    }
    List<FlowRunTaskVO> pending = runTaskRepository.findPendingByInstance(instanceId);
    List<String> receivers =
        pending.isEmpty()
            ? Collections.emptyList()
            : pending.stream()
                .map(FlowRunTaskVO::getAssigneeId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    Optional<FlowInstanceVO> instanceOpt = instanceRepository.findById(instanceId);
    String flowName = instanceOpt.map(FlowInstanceVO::getFlowName).orElse("");
    String title = "审批催办";
    String content = String.format("【%s】 您有一个待办任务被催办，请尽快处理", flowName);
    notificationService.notifyBatch("INAPP", receivers, title, content, "WORKFLOW_URGE", "URGENT");
  }

  @Override
  public void onInstanceTerminated(String instanceId, String reason) {
    // P0-1: 终止通知：通知发起人
    if (instanceId == null) {
      return;
    }
    Optional<FlowInstanceVO> instanceOpt = instanceRepository.findById(instanceId);
    if (instanceOpt.isEmpty() || instanceOpt.get().getInitiatorId() == null) {
      return;
    }
    FlowInstanceVO instance = instanceOpt.get();
    notificationService.notify(
        "INAPP",
        instance.getInitiatorId(),
        "您的流程已被终止",
        String.format(
            "【%s】 您发起的 %s 已被终止%s",
            nullSafe(instance.getFlowName()),
            nullSafe(instance.getTitle()),
            reason == null || reason.isBlank() ? "" : "，原因：" + reason),
        "WORKFLOW_TERMINATED",
        "WARN");
  }

  @Override
  public void onInstanceRecalled(String instanceId, String initiatorId) {
    // P0-1: 撤回通知：通知所有当前待办办理人
    if (instanceId == null) {
      return;
    }
    List<FlowRunTaskVO> pending = runTaskRepository.findPendingByInstance(instanceId);
    List<String> receivers =
        pending.isEmpty()
            ? Collections.emptyList()
            : pending.stream()
                .map(FlowRunTaskVO::getAssigneeId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    Optional<FlowInstanceVO> instanceOpt = instanceRepository.findById(instanceId);
    String flowName = instanceOpt.map(FlowInstanceVO::getFlowName).orElse("");
    String title = "审批已撤回";
    String content = String.format("【%s】 该流程已被发起人撤回", flowName);
    notificationService.notifyBatch(
        "INAPP", receivers, title, content, "WORKFLOW_RECALLED", "WARN");
  }

  @Override
  public void onTaskTransferred(String taskId, String fromUserId, String toUserId) {
    // P0-1: 转办通知：通知新办理人
    if (taskId == null || toUserId == null) {
      return;
    }
    Optional<FlowRunTaskVO> taskOpt = runTaskRepository.findById(taskId);
    if (taskOpt.isEmpty()) {
      return;
    }
    FlowRunTaskVO task = taskOpt.get();
    notificationService.notify(
        "INAPP",
        toUserId,
        "您有一个转办任务",
        String.format(
            "【%s】 %s - %s 已转办给您",
            nullSafe(task.getFlowName()), nullSafe(task.getTitle()), nullSafe(task.getNodeName())),
        "WORKFLOW_TRANSFERRED",
        "INFO");
  }

  @Override
  public void onTaskDelegated(String taskId, String fromUserId, String toUserId) {
    // P0-1: 委派通知：通知被委派人
    if (taskId == null || toUserId == null) {
      return;
    }
    Optional<FlowRunTaskVO> taskOpt = runTaskRepository.findById(taskId);
    if (taskOpt.isEmpty()) {
      return;
    }
    FlowRunTaskVO task = taskOpt.get();
    notificationService.notify(
        "INAPP",
        toUserId,
        "您有一个委派任务",
        String.format(
            "【%s】 %s - %s 已委派给您",
            nullSafe(task.getFlowName()), nullSafe(task.getTitle()), nullSafe(task.getNodeName())),
        "WORKFLOW_DELEGATED",
        "INFO");
  }

  @Override
  public void onTaskTimeout(String taskId, String instanceId) {
    // P0-1: 超时通知：通知当前办理人
    if (taskId == null) {
      return;
    }
    Optional<FlowRunTaskVO> taskOpt = runTaskRepository.findById(taskId);
    if (taskOpt.isEmpty()) {
      return;
    }
    FlowRunTaskVO task = taskOpt.get();
    String assigneeId = task.getAssigneeId();
    if (assigneeId == null) {
      return;
    }
    notificationService.notify(
        "INAPP",
        assigneeId,
        "审批任务已超时",
        String.format(
            "【%s】 %s - %s 已超时，请尽快处理",
            nullSafe(task.getFlowName()), nullSafe(task.getTitle()), nullSafe(task.getNodeName())),
        "WORKFLOW_TIMEOUT",
        "WARN");
  }

  // ============================== 工具方法 ==============================

  /**
   * 从流程实例的业务键解析立项 ID。
   *
   * <p>业务键格式为 {@code REMI_INIT_<initiationId>}（见 InitiationServiceImpl#startProcess），
   * 兼容直接以数字存储的业务键。
   *
   * @param instance 流程实例（可空）
   * @return 立项 ID，解析失败返回 null
   */
  private String resolveInitiationId(FlowInstanceVO instance) {
    if (instance == null) {
      return null;
    }
    String bizId = instance.getBusinessId();
    if (!StringUtils.hasText(bizId)) {
      return null;
    }
    String raw =
        bizId.startsWith(INIT_BIZ_KEY_PREFIX)
            ? bizId.substring(INIT_BIZ_KEY_PREFIX.length())
            : bizId;
    return raw.trim();
  }

  /**
   * P0-7: 发布立项状态联动事件到消息队列。
   *
   * <p>消息体字段约定（与 project 模块消费方契约对齐）：
   *
   * <ul>
   *   <li>{@code initiationId} - 立项 ID
   *   <li>{@code action} - 状态联动动作：markProcessing / markApproved / markRejected
   *   <li>{@code instanceId} - 流程实例 ID（便于消费方回查）
   *   <li>{@code tenantId} - 租户 ID（多租户场景下做隔离）
   *   <li>{@code traceId} - 链路追踪 ID（跨服务 trace 串联）
   *   <li>{@code reason} - 驳回原因（仅 markRejected 携带）
   * </ul>
   *
   * <p>MQ 发布失败不影响主流程，仅记录日志（消费方需保证幂等）。
   *
   * @param initiationId 立项 ID
   * @param action 状态联动动作
   * @param instanceId 流程实例 ID
   * @param instance 流程实例（用于提取 tenantId/traceId，可空）
   * @param reason 驳回原因（仅 markRejected 时传入，可空）
   */
  private void publishInitiationStatusSync(
      String initiationId,
      String action,
      String instanceId,
      FlowInstanceVO instance,
      String... reason) {
    if (queuePublisher == null || initiationId == null || action == null) {
      return;
    }
    try {
      Map<String, Object> data = new HashMap<>(8);
      data.put("initiationId", initiationId);
      data.put("action", action);
      data.put("instanceId", instanceId);
      if (instance != null) {
        data.put(
            "tenantId",
            instance.getTenantId() == null ? null : String.valueOf(instance.getTenantId()));
        String traceId = instance.getProviderTraceId();
        if (traceId == null || traceId.isBlank()) {
          traceId = RequestContext.getTraceId();
          if (traceId == null || traceId.isBlank()) {
            traceId = MDC.get("traceId");
            if (traceId == null) traceId = MDC.get("tid");
          }
        }
        if (traceId != null) {
          data.put("traceId", traceId);
        }
      }
      if (reason != null && reason.length > 0 && reason[0] != null) {
        data.put("reason", reason[0]);
      }
      queuePublisher.publish(EVENT_INITIATION_STATUS_SYNC, data);
      log.info(
          "[FlowListener] 立项状态联动事件已发布: action={} initiationId={} instanceId={}",
          action,
          initiationId,
          instanceId);
    } catch (Exception e) {
      log.warn(
          "[FlowListener] 立项状态联动事件发布失败: action={} initiationId={} err={}",
          action,
          initiationId,
          e.getMessage());
    }
  }

  /**
   * 推送实时消息给办理人（IM / WebSocket 渠道）。
   *
   * <p>P2-7: 直接调用 {@link RealtimePushTemplate} 推送，消除 Feign 中转开销。 消息推送为非关键路径，失败仅记录日志，不影响任务创建。
   *
   * @param assigneeId 办理人 ID
   * @param title 标题
   * @param content 内容
   * @param taskId 任务 ID
   */
  private void pushImNotification(String assigneeId, String title, String content, String taskId) {
    if (assigneeId == null) {
      return;
    }
    try {
      Map<String, Object> data = new HashMap<>();
      data.put("title", title);
      data.put("content", content);
      data.put("taskId", taskId);
      data.put("type", "WORKFLOW_TASK");
      pushTemplate.pushToUserWithOffline(assigneeId, "NOTIFICATION", data);
    } catch (Exception e) {
      log.warn(
          "[FlowListener] IM 推送失败: assigneeId={} taskId={}: {}",
          assigneeId,
          taskId,
          e.getMessage());
    }
  }

  private static String nullSafe(String s) {
    return s == null ? "" : s;
  }
}
