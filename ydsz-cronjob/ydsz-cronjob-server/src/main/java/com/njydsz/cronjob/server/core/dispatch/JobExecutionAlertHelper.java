package com.njydsz.cronjob.server.core.dispatch;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;

import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.api.DomainEventTypes;
import com.njydsz.common.event.publish.DomainEventPublisher;
import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.cronjob.domain.vo.JobLogVO;
import com.njydsz.cronjob.domain.vo.JobVO;
import com.njydsz.cronjob.server.core.TaskCompletedEvent;
import com.njydsz.cronjob.server.core.alert.AlertContext;
import com.njydsz.cronjob.server.core.alert.AlertTrigger;
import com.njydsz.cronjob.server.core.alert.AlertType;

import lombok.extern.slf4j.Slf4j;

/**
 * 任务执行告警与事件辅助类。
 *
 * <p>封装告警触发、WebHook 事件推送、领域事件发布等逻辑， 遵循云顶编码规范，将 {@link DefaultTaskDispatcher} 中的告警与事件职责独立出来，
 * 降低主类复杂度，提升代码可维护性。
 *
 * <h3>职责范围</h3>
 *
 * <ul>
 *   <li>任务失败/慢任务告警触发
 *   <li>WebHook 事件推送（TASK_STARTED/TASK_SUCCESS/TASK_FAILED/TASK_TIMEOUT）
 *   <li>任务完成事件发布（触发后继依赖任务）
 *   <li>任务失败 Outbox 事件发布（跨模块可靠投递）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class JobExecutionAlertHelper {

  private final ObjectProvider<AlertTrigger> alertTriggerProvider;
  private final ObjectProvider<WebhookEventDispatcher> webhookEventDispatcherProvider;
  private final org.springframework.context.ApplicationEventPublisher eventPublisher;
  private final ObjectProvider<DomainEventPublisher> eventPublisherProvider;

  /**
   * 构造告警与事件辅助类。
   *
   * @param alertTriggerProvider 告警触发器提供者
   * @param webhookEventDispatcherProvider WebHook 事件分发器提供者
   * @param eventPublisher Spring 事件发布器
   * @param eventPublisherProvider 领域事件发布器提供者
   */
  public JobExecutionAlertHelper(
      ObjectProvider<AlertTrigger> alertTriggerProvider,
      ObjectProvider<WebhookEventDispatcher> webhookEventDispatcherProvider,
      org.springframework.context.ApplicationEventPublisher eventPublisher,
      ObjectProvider<DomainEventPublisher> eventPublisherProvider) {
    this.alertTriggerProvider = alertTriggerProvider;
    this.webhookEventDispatcherProvider = webhookEventDispatcherProvider;
    this.eventPublisher = eventPublisher;
    this.eventPublisherProvider = eventPublisherProvider;
  }

  /**
   * 触发告警。
   *
   * <p>根据任务执行结果触发相应告警：
   *
   * <ul>
   *   <li>失败时触发 {@link AlertType#FAIL} 告警
   *   <li>成功时触发 {@link AlertType#SLOW} 告警（triggerValue=耗时毫秒，由规则阈值判定是否实际告警）
   * </ul>
   *
   * <p>使用 try-catch 包裹，确保告警触发失败不影响主流程。
   *
   * @param job 任务定义
   * @param success 是否执行成功
   * @param log0 任务日志（含耗时信息）
   */
  public void triggerAlerts(JobVO job, boolean success, JobLogVO log0) {
    AlertTrigger alertTrigger = alertTriggerProvider.getIfAvailable();
    if (alertTrigger == null) {
      return;
    }
    try {
      String triggerValue = log0.getDurationMs() != null ? String.valueOf(log0.getDurationMs()) : null;
      AlertContext context =
          AlertContext.of(
              success ? AlertType.SLOW : AlertType.FAIL,
              job.getId(),
              job.getJobKey(),
              job.getJobName(),
              log0.getId(),
              triggerValue,
              log0.getErrorMessage(),
              log0.getTraceId(),
              job.getTenantId());
      alertTrigger.trigger(context);
    } catch (Exception e) {
      log.warn("[Dispatcher] 触发告警失败(不影响主流程): key={} reason={}", job.getJobKey(), e.getMessage());
    }
  }

  /**
   * 推送 WebHook 事件通知。
   *
   * <p>使用 try-catch 包裹，确保事件推送失败不影响主流程。
   *
   * @param eventType 事件类型: TASK_STARTED / TASK_SUCCESS / TASK_FAILED / TASK_TIMEOUT
   * @param job 任务定义
   * @param log0 任务日志
   */
  public void dispatchWebhookEvent(String eventType, JobVO job, JobLogVO log0) {
    WebhookEventDispatcher dispatcher = webhookEventDispatcherProvider.getIfAvailable();
    if (dispatcher == null) {
      return;
    }
    try {
      Map<String, Object> payload = new HashMap<>();
      payload.put("jobKey", job.getJobKey());
      payload.put("jobName", job.getJobName());
      payload.put("logId", log0.getId());
      payload.put("status", log0.getStatus());
      payload.put("duration", log0.getDurationMs());
      payload.put("triggerType", log0.getTriggerType());
      if (log0.getErrorMessage() != null) {
        payload.put("errorMessage", log0.getErrorMessage());
      }
      dispatcher.dispatchEvent(eventType, job.getJobKey(), payload);
    } catch (Exception e) {
      log.warn(
          "[Dispatcher] WebHook 事件推送失败(不影响主流程): eventType={} key={} reason={}",
          eventType,
          job.getJobKey(),
          e.getMessage());
    }
  }

  /**
   * 发布任务完成事件，触发后继依赖任务。
   *
   * <p>使用 try-catch 包裹，确保事件发布失败不影响主流程。
   *
   * @param job 任务定义
   * @param success 是否执行成功
   * @param logId 执行日志 ID
   */
  public void publishTaskCompleted(JobVO job, boolean success, String logId) {
    try {
      TaskCompletedEvent event = new TaskCompletedEvent(job.getId(), job.getJobKey(), success, logId);
      eventPublisher.publishEvent(event);
    } catch (Exception e) {
      log.warn(
          "[Dispatcher] 发布任务完成事件失败(不影响主流程): key={} reason={}",
          job.getJobKey(),
          e.getMessage());
    }
  }

  /**
   * 发布任务执行失败 Outbox 事件（跨模块可靠投递）。
   *
   * <p>消息中心订阅 {@link DomainEventTypes#JOB_EXECUTION_FAILED} 后据此发送告警通知。
   * DomainEventPublisher 为可选依赖，未配置时安全降级（仅 DEBUG 日志）。
   *
   * @param job 任务定义
   * @param log0 任务执行日志
   */
  public void publishJobFailureOutboxEvent(JobVO job, JobLogVO log0) {
    DomainEventPublisher publisher = eventPublisherProvider.getIfAvailable();
    if (publisher == null) {
      log.debug(
          "[Dispatcher] DomainEventPublisher 未配置，跳过 JOB_EXECUTION_FAILED 事件: jobKey={}",
          job.getJobKey());
      return;
    }
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("jobId", job.getId());
    metadata.put("jobKey", job.getJobKey());
    metadata.put("jobName", job.getJobName() != null ? job.getJobName() : "");
    metadata.put("logId", log0.getId());
    metadata.put("errorMessage", log0.getErrorMessage() != null ? log0.getErrorMessage() : "");
    metadata.put("triggerType", log0.getTriggerType() != null ? log0.getTriggerType() : "");
    metadata.put("durationMs", log0.getDurationMs());
    metadata.put("tenantId", job.getTenantId() != null ? job.getTenantId() : "");
    publisher.publish(
        DomainEvent.builder()
            .aggregateType("JobLog")
            .aggregateId(log0.getId())
            .eventType(DomainEventTypes.JOB_EXECUTION_FAILED)
            .metadata(metadata)
            .build());
  }
}
