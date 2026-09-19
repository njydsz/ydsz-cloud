package com.njydsz.common.audit.event;

import lombok.Builder;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 数据导出审计事件
 *
 * <p>在数据导出操作完成时发布，由 {@link AuditEventListener} 异步消费并落库到 {@code
 * sys_audit_log}（action = {@link com.njydsz.common.audit.enums.AuditAction#EXPORT}），
 * 用于安全审计与合规留痕。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * applicationEventPublisher.publishEvent(
 *     DataExportAuditEvent.builder()
 *         .userId(currentUser.getId())
 *         .username(currentUser.getUsername())
 *         .exportModule("用户管理")
 *         .bizType("user")
 *         .rowCount(1500)
 *         .traceId(traceId)
 *         .clientIp(clientIp)
 *         .tenantId(tenantId)
 *         .exportedAt(System.currentTimeMillis())
 *         .build());
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
public class DataExportAuditEvent extends ApplicationEvent {

  private static final long serialVersionUID = 1L;

  /** 用户 ID */
  private final String userId;

  /** 用户名 */
  private final String username;

  /** 导出模块（如"用户管理"、"订单管理"等） */
  private final String exportModule;

  /** 业务类型 */
  private final String bizType;

  /** 导出行数 */
  private final Integer rowCount;

  /** 链路追踪 ID */
  private final String traceId;

  /** 客户端 IP */
  private final String clientIp;

  /** 租户 ID */
  private final String tenantId;

  /** 导出时间（毫秒时间戳） */
  private final Long exportedAt;

  @Builder
  public DataExportAuditEvent(
      Object source,
      String userId,
      String username,
      String exportModule,
      String bizType,
      Integer rowCount,
      String traceId,
      String clientIp,
      String tenantId,
      Long exportedAt) {
    super(source == null ? new Object() : source);
    this.userId = userId;
    this.username = username;
    this.exportModule = exportModule;
    this.bizType = bizType;
    this.rowCount = rowCount;
    this.traceId = traceId;
    this.clientIp = clientIp;
    this.tenantId = tenantId;
    this.exportedAt = exportedAt;
  }
}
