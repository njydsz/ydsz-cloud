package com.njydsz.workflow.server.service.impl.instance;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import com.njydsz.workflow.domain.enums.FlowTaskStatus;
import com.njydsz.workflow.infra.mapper.FlowRunTaskMapper;
import com.njydsz.workflow.server.metrics.FlowMetrics;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 流程任务超时服务实现。
 *
 * <p>扫描超时未办任务，根据 SLA 配置自动触发：跳过、转办、催办、终止，
 *
 * <p>由独立 Scheduler 周期调度（{@code @Scheduled}，默认 1 分钟）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowTaskTimeoutService {

  private final FlowRunTaskMapper taskMapper;
  private final FlowTaskSupport support;

  /** P2-3: Prometheus 指标（可能为 null：测试环境） */
  private final FlowMetrics flowMetrics;

  /**
   * 标记任务为 TIMEOUT 状态。
   *
   * <p>任务状态必须为 PENDING/CLAIMED，否则抛 BAD_REQUEST。完成后写审计日志、 触发 onTaskTimeout 事件、累计指标。
   */
  @Transactional(rollbackFor = Exception.class)
  public void timeoutTask(String taskId, String reason) {
    FlowRunTask task = support.getTaskOrThrow(taskId);
    String status = task.getTaskStatus();
    if (!FlowTaskStatus.PENDING.name().equals(status)
        && !FlowTaskStatus.CLAIMED.name().equals(status)) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .key("error.workflow.msg_ecc09732")
          .params(status)
          .build();
    }
    LocalDateTime now = LocalDateTime.now();
    Long durationMs =
        task.getCreatedAt() == null ? null : Duration.between(task.getCreatedAt(), now).toMillis();
    taskMapper.completeTask(task.getId(), FlowTaskStatus.TIMEOUT.name(), reason, now, durationMs);
    task.setTaskStatus(FlowTaskStatus.TIMEOUT.name());
    task.setComment(reason);
    task.setFinishAt(now);
    task.setDurationMs(durationMs);
    support.audit(task, "TIMEOUT", null, null, reason);
    log.info("[Flow] 任务超时: taskId={} reason={}", taskId, reason);
    if (flowMetrics != null) {
      flowMetrics.incTaskAutoHandled(task.getFlowCode(), task.getNodeCode(), "TIMEOUT");
    }
    // P2-36: 触发 onTaskTimeout 事件
    support.fireEvent(l -> l.onTaskTimeout(task.getId(), task.getInstanceId()), task.getId());
    // P2-35: 发布 Spring 异步事件
    support.publishWorkflowEvent("TASK_TIMEOUT", task.getInstanceId(), task.getId());
  }

  /**
   * P2-1: 任务级挂起 — 将 PENDING/CLAIMED 任务临时挂起为 SUSPENDED。
   *
   * <p>仅修改任务状态，不推进流程、不取消其它任务。挂起期间不计超时 （JobScanner 应跳过 SUSPENDED）。激活后回到 PENDING，需重新签收。
   */
  @Transactional(rollbackFor = Exception.class)
  public void suspendTask(String taskId, String operatorId, String reason) {
    FlowRunTask task = support.getTaskOrThrow(taskId);
    String status = task.getTaskStatus();
    if (!FlowTaskStatus.PENDING.name().equals(status)
        && !FlowTaskStatus.CLAIMED.name().equals(status)) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .key("error.workflow.msg_d0e1f2a3")
          .params(status)
          .build();
    }
    task.setTaskStatus(FlowTaskStatus.SUSPENDED.name());
    task.setComment(reason);
    taskMapper.updateById(task);
    support.audit(task, "SUSPEND", operatorId, null, reason);
    log.info("[Flow] 任务挂起: taskId={} operator={} reason={}", taskId, operatorId, reason);
  }

  /**
   * P2-1: 任务级激活 — 将 SUSPENDED 任务恢复为 PENDING。
   *
   * <p>激活后清空签收人（assigneeId/assigneeName），需重新签收。
   */
  @Transactional(rollbackFor = Exception.class)
  public void activateTask(String taskId, String operatorId) {
    FlowRunTask task = support.getTaskOrThrow(taskId);
    String status = task.getTaskStatus();
    if (!FlowTaskStatus.SUSPENDED.name().equals(status)) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .key("error.workflow.msg_e1f2a3b4")
          .params(status)
          .build();
    }
    task.setTaskStatus(FlowTaskStatus.PENDING.name());
    task.setAssigneeId(null);
    task.setAssigneeName(null);
    task.setClaimAt(null);
    taskMapper.updateById(task);
    support.audit(task, "ACTIVATE", operatorId, null, null);
    log.info("[Flow] 任务激活: taskId={} operator={}", taskId, operatorId);
  }

  /** 取消某实例的全部 PENDING 任务（终止/驳回终态时使用） */
  public void cancelByInstance(String instanceId, String taskStatus) {
    taskMapper.cancelByInstance(instanceId, taskStatus);
  }
}
