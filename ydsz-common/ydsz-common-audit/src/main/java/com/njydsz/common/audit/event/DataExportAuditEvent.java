package com.njydsz.common.audit.event;

import lombok.Builder;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 数据导出审计事件
 *
 * <p>在数据导出操作完成时发布，由 {@code DataExportAuditListener} 异步消费并落库到 {@code
 * ydsz_data_export_audit}，用于安全审计与合规留痕。
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

  /** 导出模块 */
  private final String exportModule;

  /** 导出动作 */
  private final String exportAction;

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

  /** 导出时间（毫秒） */
  private final Long exportedAt;

  @Builder
  public DataExportAuditEvent(
      Object source,
      String userId,
      String username,
      String exportModule,
      String exportAction,
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
    this.exportAction = exportAction;
    this.bizType = bizType;
    this.rowCount = rowCount;
    this.traceId = traceId;
    this.clientIp = clientIp;
    this.tenantId = tenantId;
    this.exportedAt = exportedAt;
  }
}
