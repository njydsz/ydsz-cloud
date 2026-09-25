package com.njydsz.cronjob.server.core.outbox.subscriber;

import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.njydsz.common.audit.core.AuditRecorder;
import com.njydsz.common.audit.domain.AuditLog;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.event.consumer.OutboxSubscriber;
import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.common.util.id.SnowflakeIdGenerator;

/**
 * 审计事件订阅者（YDIZ-EVENT-002 OutboxSubscriber SPI 实现）。
 *
 * <p>消费 Outbox 事件中 topic={@code audit} 的事件，通过 {@link AuditRecorder} 异步写入审计日志。
 *
 * <p>由 {@link com.njydsz.common.event.consumer.OutboxSubscriberDispatcher} 统一分发。
 *
 * <p><b>降级策略：</b>容器中若无 {@link AuditRecorder} Bean（未引入 common-audit 时），
 * 降级为日志记录（保留事件全貌，不丢失关键审计信息）。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @since 26.09.24 实现 OutboxSubscriber SPI（YDIZ-EVENT-002），移除 @EventListener 手动过滤
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditOutboxSubscriber implements OutboxSubscriber {

  private static final Logger LOG = LoggerFactory.getLogger(AuditOutboxSubscriber.class);

  /** 日志 payload 截断长度 */
  private static final int MAX_PAYLOAD_LOG_LENGTH = 200;

  /** 落库 payload 截断长度（避免审计表膨胀） */
  private static final int MAX_PAYLOAD_STORE_LENGTH = 1000;

  /** 审计模块标识（写入 ydsz_job_audit_log.module） */
  private static final String AUDIT_MODULE = "cronjob";

  /** 审计类型：任务调度操作审计（业务操作） */
  private static final int AUDIT_TYPE_BUSINESS = 1;

  /** 审计动作：新增（JOB_CREATED） */
  private static final int ACTION_CREATE = 1;

  /** 审计动作：修改（JOB_UPDATED / JOB_RESUMED） */
  private static final int ACTION_UPDATE = 2;

  /** 审计动作：删除（JOB_DELETED） */
  private static final int ACTION_DELETE = 3;

  /** 审计动作：禁用（JOB_PAUSED） */
  private static final int ACTION_DISABLE = 14;

  /** 审计动作：其他（兜底） */
  private static final int ACTION_OTHER = 99;

  /** 审计状态：成功 */
  private static final int STATUS_SUCCESS = 1;

  /** 审计状态：失败 */
  private static final int STATUS_FAILURE = 0;

  private final ObjectProvider<AuditRecorder> auditRecorderProvider;
  private final ObjectProvider<SnowflakeIdGenerator> snowflakeIdGeneratorProvider;

  @Override
  public String getTopic() {
    return "audit";
  }

  /**
   * 处理 audit 事件，写入审计日志。
   *
   * @param message Outbox 消息
   */
  @Override
  public void onMessage(OutboxMessage message) {
    try {
      writeAudit(message);
      LOG.info("[AuditSubscriber] 审计事件处理完成: eventKey={} eventType={} topic={}",
          message.getId(), message.getEventType(), message.getTopic());
    } catch (RuntimeException e) {
      LOG.error("[AuditSubscriber] 审计记录异常: eventKey={} reason={}", message.getId(), e.getMessage(), e);
      throw e;
    }
  }

  /**
   * 将审计事件写入 ydsz_job_audit_log（通过 AuditRecorder 异步），否则降级为日志记录。
   *
   * @param message Outbox 审计消息
   */
  private void writeAudit(OutboxMessage message) {
    AuditRecorder recorder = auditRecorderProvider.getIfAvailable();
    SnowflakeIdGenerator idGenerator = snowflakeIdGeneratorProvider.getIfAvailable();

    if (recorder == null || idGenerator == null) {
      LOG.info("[AuditSubscriber] 审计组件不可用，降级为日志记录: eventKey={} eventType={} topic={} payload={} recorderAvailable={} idGenAvailable={}",
          message.getId(), message.getEventType(), message.getTopic(),
          truncate(message.getPayload(), MAX_PAYLOAD_LOG_LENGTH), recorder != null, idGenerator != null);
      return;
    }

    String eventKey = OutboxEventKeyExtractor.extractEventKey(message.getExtInfo());

    AuditLog auditLog = new AuditLog();
    auditLog.setId(String.valueOf(idGenerator.nextId()));
    auditLog.setAuditType(AUDIT_TYPE_BUSINESS);
    auditLog.setAction(resolveActionCode(message.getEventType()));
    auditLog.setStatus(resolveStatus(message.getPayload()));
    auditLog.setModule(AUDIT_MODULE);
    auditLog.setBusinessNo(eventKey);
    auditLog.setContent(truncate(message.getPayload(), MAX_PAYLOAD_STORE_LENGTH));
    auditLog.setOperationTime(LocalDateTime.now());
    auditLog.setCreatedAt(LocalDateTime.now());
    auditLog.setOperatorId(RequestContext.getUserId());
    auditLog.setTenantId(RequestContext.getTenantId());

    String traceId = RequestContext.getTraceId();
    if (traceId != null && !traceId.isEmpty()) {
      auditLog.setTraceId(traceId);
    }

    recorder.record(auditLog);
  }

  /**
   * 根据 Outbox 事件类型解析审计动作编码。
   *
   * @param eventType 事件类型
   * @return 审计动作编码
   */
  private int resolveActionCode(String eventType) {
    if (eventType == null || eventType.isEmpty()) {
      return ACTION_OTHER;
    }
    return switch (eventType) {
      case "JOB_CREATED" -> ACTION_CREATE;
      case "JOB_UPDATED", "JOB_RESUMED" -> ACTION_UPDATE;
      case "JOB_DELETED" -> ACTION_DELETE;
      case "JOB_PAUSED" -> ACTION_DISABLE;
      default -> ACTION_OTHER;
    };
  }

  /**
   * 根据 payload 解析操作结果状态（成功/失败）。
   *
   * @param payload 事件 payload
   * @return STATUS_SUCCESS 或 STATUS_FAILURE
   */
  private int resolveStatus(String payload) {
    if (payload == null) {
      return STATUS_SUCCESS;
    }
    String upper = payload.toUpperCase();
    return (upper.contains("FAILED") || upper.contains("ERROR")) ? STATUS_FAILURE : STATUS_SUCCESS;
  }

  private String truncate(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    return value.length() > maxLength ? value.substring(0, maxLength) + "..." : value;
  }
}
