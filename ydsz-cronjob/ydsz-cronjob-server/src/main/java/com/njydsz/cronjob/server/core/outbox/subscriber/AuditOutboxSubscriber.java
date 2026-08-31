package com.njydsz.cronjob.server.core.outbox.subscriber;

import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.njydsz.common.audit.core.AuditWriter;
import com.njydsz.common.audit.domain.AuditLog;
import com.njydsz.cronjob.domain.vo.OutboxEventVO;

/**
 * 审计事件订阅者（P0-2：Outbox 模式）。
 *
 * <p>消费 Outbox 事件中 topic={@code audit} 的事件，记录操作审计。
 *
 * <p><b>P1-F9：落库实现</b>——原实现仅 log.info 占位；现通过 {@link AuditWriter}（common-audit 的
 * {@code JdbcAuditStorage} 等实现）写入 {@code ydsz_job_audit_log}，记录任务生命周期操作
 * （创建/更新/暂停/恢复/触发/删除等）。容器中无 AuditWriter Bean 时（未引入 common-audit）静默降级为日志记录。
 *
 * @author ydsz-team
 * @since 1.0.0
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

  /** 审计类型：业务操作审计 */
  private static final int AUDIT_TYPE_BUSINESS = 1;

  /** 审计操作动作：未知/通用（action 枚举由 common-audit 定义，此处用通用值） */
  private static final int AUDIT_ACTION_DEFAULT = 1;

  /** 审计结果状态：成功 */
  private static final int AUDIT_STATUS_SUCCESS = 1;

  private static final String TOPIC = "audit";

  /** 审计写入器（common-audit 可选注入，未引入模块时为 null → 降级日志记录） */
  private final ObjectProvider<AuditWriter> auditWriterProvider;

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
   * 将审计事件写入 ydsz_job_audit_log（AuditWriter 存在时），否则降级为日志记录。
   *
   * @param event Outbox 审计事件
   */
  private void writeAudit(OutboxEventVO event) {
    AuditWriter writer = auditWriterProvider.getIfAvailable();
    if (writer == null) {
      // 未引入 ydsz-common-audit：降级为日志记录，保留事件全貌
      log.info(
          "[AuditSubscriber] 审计事件: eventKey={} eventType={} topic={} payload={}",
          event.getEventKey(),
          event.getEventType(),
          event.getTopic(),
          truncate(event.getPayload(), MAX_PAYLOAD_LOG_LENGTH));
      return;
    }
    AuditLog auditLog = new AuditLog();
    auditLog.setAuditType(AUDIT_TYPE_BUSINESS);
    auditLog.setAction(AUDIT_ACTION_DEFAULT);
    auditLog.setStatus(AUDIT_STATUS_SUCCESS);
    auditLog.setModule(AUDIT_MODULE);
    auditLog.setBusinessNo(event.getEventKey());
    auditLog.setContent(truncate(event.getPayload(), MAX_PAYLOAD_STORE_LENGTH));
    auditLog.setOperationTime(java.time.LocalDateTime.now());
    writer.write(auditLog);
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
