package com.njydsz.cronjob.server.core.outbox.subscriber;

import java.time.LocalDateTime;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.njydsz.common.audit.core.AuditRecorder;
import com.njydsz.common.audit.domain.AuditLog;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.cronjob.domain.vo.OutboxEventVO;

/**
 * 审计事件订阅者（P1-14：操作审计 — ydsz-common-audit 驱动模式）。
 *
 * <p>消费 Outbox 事件中 topic={@code audit} 的事件，通过 {@link AuditRecorder} 异步写入审计日志。
 *
 * <p><b>迁移说明（26.09.01-P1-F9）：</b>
 *
 * <ul>
 *   <li>原实现：直接调用 {@code AuditWriter.write()} 同步落库，无异步/批量/降级保障
 *   <li>现实现：委托 common-audit 的 {@link AuditRecorder}，享受异步队列 + 批量写入 + 磁盘兜底等能力
 *   <li>审计表：写入 {@code ydsz_job_audit_log}（由 {@code ydsz.audit.sharding-base-table-name} 配置）
 * </ul>
 *
 * <p><b>降级策略：</b>容器中若无 {@link AuditRecorder} Bean（未引入 common-audit 时），
 * 降级为日志记录（保留事件全貌，不丢失关键审计信息）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditOutboxSubscriber implements Consumer<OutboxEventVO> {

  /** 日志 payload 截断长度 */
  private static final int MAX_PAYLOAD_LOG_LENGTH = 200;

  /** 落库 payload 截断长度（避免审计表膨胀） */
  private static final int MAX_PAYLOAD_STORE_LENGTH = 1000;

  /** 审计模块标识（写入 ydsz_job_audit_log.module） */
  private static final String AUDIT_MODULE = "cronjob";

  /** 审计类型：任务调度操作审计（业务操作） */
  private static final int AUDIT_TYPE_BUSINESS = 1;

  /** TOPIC 过滤：仅处理 audit 事件 */
  private static final String TOPIC = "audit";

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

  /**
   * 审计记录器（common-audit 异步批量写入器）。
   *
   * <p>通过 {@link ObjectProvider} 延迟获取：若容器中未引入 common-audit 则为 null，降级为日志记录。
   */
  private final ObjectProvider<AuditRecorder> auditRecorderProvider;

  /**
   * 分布式 ID 生成器（用于审计记录主键）。
   *
   * <p>通过 {@link ObjectProvider} 延迟获取：若容器中未引入 common-util 则为 null，降级为日志记录。
   */
  private final ObjectProvider<SnowflakeIdGenerator> snowflakeIdGeneratorProvider;

  @Override
  public void accept(OutboxEventVO event) {
    if (!TOPIC.equals(event.getTopic())) {
      return;
    }
    try {
      writeAudit(event);
      log.info(
          "[AuditSubscriber] 审计事件处理完成: eventKey={} eventType={} topic={}",
          event.getEventKey(),
          event.getEventType(),
          event.getTopic());
    } catch (Exception e) {
      log.error(
          "[AuditSubscriber] 审计记录异常: eventKey={} reason={}", event.getEventKey(), e.getMessage(), e);
      throw e;
    }
  }

  /**
   * 将审计事件写入 ydsz_job_audit_log（通过 AuditRecorder 异步），否则降级为日志记录。
   *
   * @param event Outbox 审计事件
   */
  private void writeAudit(OutboxEventVO event) {
    AuditRecorder recorder = auditRecorderProvider.getIfAvailable();
    SnowflakeIdGenerator idGenerator = snowflakeIdGeneratorProvider.getIfAvailable();

    if (recorder == null || idGenerator == null) {
      // 未引入 ydsz-common-audit 或 SnowflakeIdGenerator 不可用：降级为日志记录，保留事件全貌
      log.info(
          "[AuditSubscriber] 审计组件不可用，降级为日志记录: eventKey={} eventType={} topic={} payload={} recorderAvailable={} idGenAvailable={}",
          event.getEventKey(),
          event.getEventType(),
          event.getTopic(),
          truncate(event.getPayload(), MAX_PAYLOAD_LOG_LENGTH),
          recorder != null,
          idGenerator != null);
      return;
    }

    AuditLog auditLog = new AuditLog();
    auditLog.setId(String.valueOf(idGenerator.nextId()));
    auditLog.setAuditType(AUDIT_TYPE_BUSINESS);
    auditLog.setAction(resolveActionCode(event.getEventType()));
    auditLog.setStatus(resolveStatus(event.getPayload()));
    auditLog.setModule(AUDIT_MODULE);
    auditLog.setBusinessNo(event.getEventKey());
    auditLog.setContent(truncate(event.getPayload(), MAX_PAYLOAD_STORE_LENGTH));
    auditLog.setOperationTime(LocalDateTime.now());
    auditLog.setCreatedAt(LocalDateTime.now());

    // 从 RequestContext 补充操作人与租户信息（Web/API 上下文中可能为空，仅作尽力而为填充）
    auditLog.setOperatorId(RequestContext.getUserId());
    auditLog.setTenantId(RequestContext.getTenantId());

    // 链路追踪 ID（通过 extra 透传）
    String traceId = RequestContext.getTraceId();
    if (traceId != null && !traceId.isEmpty()) {
      auditLog.setTraceId(traceId);
    }

    // 走 common-audit 异步通道：享受批量写入 + 队列背压 + 磁盘兜底
    recorder.record(auditLog);
  }

  /**
   * 根据 Outbox 事件类型解析审计动作编码（复用 common-audit 的语义枚举）。
   *
   * <p>映射关系（简化）：
   *
   * <ul>
   *   <li>JOB_CREATED → CREATE (1)
   *   <li>JOB_UPDATED / JOB_RESUMED → UPDATE (2)
   *   <li>JOB_DELETED → DELETE (3)
   *   <li>JOB_PAUSED → DISABLE (14)
   *   <li>JOB_TRIGGERED → OTHER (99)
   *   <li>其他 → OTHER (99)
   * </ul>
   *
   * @param eventType Outbox 事件类型
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
      case "JOB_TRIGGERED" -> ACTION_OTHER;
      default -> ACTION_OTHER;
    };
  }

  /**
   * 根据 payload 解析操作结果状态（成功/失败）。
   *
   * <p>简单启发式：payload 包含 "FAILED" 或 "ERROR" 视为失败，否则为成功。
   *
   * @param payload Outbox 事件 payload
   * @return STATUS_SUCCESS 或 STATUS_FAILURE
   */
  private int resolveStatus(String payload) {
    if (payload == null) {
      return STATUS_SUCCESS;
    }
    String upper = payload.toUpperCase();
    return (upper.contains("FAILED") || upper.contains("ERROR")) ? STATUS_FAILURE : STATUS_SUCCESS;
  }

  /**
   * 安全截断字符串，避免审计表/日志膨胀。
   *
   * @param value 原始值（可为 null）
   * @param maxLength 最大长度
   * @return 截断后的字符串；null 输入返回 null
   */
  private String truncate(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    return value.length() > maxLength ? value.substring(0, maxLength) + "..." : value;
  }
}
