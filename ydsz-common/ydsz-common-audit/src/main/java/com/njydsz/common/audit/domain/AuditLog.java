package com.njydsz.common.audit.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审计日志实体
 *
 * <p>封装完整的审计轨迹信息，包括操作人、行为、状态、上下文、参数、结果等。 该实体在审计切面、记录器、存储层之间传递，字段名直接映射数据库列。
 *
 * <p><b>字段使用约束：</b>
 *
 * <ul>
 *   <li>{@link #requestParams} 和 {@link #responseResult} 默认仅记录截断/脱敏后的数据， 避免日志膨胀和敏感信息泄露
 *   <li>{@link #operatorId} 从 {@code RequestContext} 透传，避免在审计上下文中重复存储
 *   <li>{@link #appKey} 为应用标识（合并原 appId/appCode/appName），从配置中心读取
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 审计记录唯一标识（雪花算法生成） */
  private String id;

  /** 审计类型（{@link com.njydsz.common.audit.enums.AuditType} 编码） */
  private Integer auditType;

  /** 操作行为（{@link com.njydsz.common.audit.enums.AuditAction} 编码） */
  private Integer action;

  /** 审计状态（{@link com.njydsz.common.audit.enums.AuditStatus} 编码） */
  private Integer status;

  /** 模块名称（如用户管理、订单管理等） */
  private String module;

  /** 操作内容描述（经 SpEL 解析后的最终文本） */
  private String content;

  /** 业务流水号（关联业务单据，便于端到端追踪） */
  private String businessNo;

  /** 操作人 ID */
  private String operatorId;

  /** 操作人姓名 */
  private String operatorName;

  /** 操作时间（业务方法执行时刻） */
  private LocalDateTime operationTime;

  /** 请求 IP 地址 */
  private String ipAddress;

  /** 请求参数（已脱敏/截断） */
  private String requestParams;

  /** 响应结果（已脱敏/截断；默认不记录） */
  private String responseResult;

  /** 错误信息（业务方法抛异常时记录） */
  private String errorMessage;

  /** 执行耗时（毫秒） */
  private Long costTime;

  /** 应用标识（合并原 appId/appCode/appName，从配置中心读取） */
  private String appKey;

  /** 租户 ID（多租户场景下用于数据隔离） */
  private String tenantId;

  /** 链路追踪 ID（独立列，支持索引查询） */
  private String traceId;

  /** 创建时间（审计日志落库时刻） */
  private LocalDateTime createdAt;

  @Override
  public String toString() {
    return "AuditLog{"
        + "id='"
        + id
        + '\''
        + ", auditType="
        + auditType
        + ", action="
        + action
        + ", status="
        + status
        + ", module='"
        + module
        + '\''
        + ", content='"
        + content
        + '\''
        + ", operatorName='"
        + operatorName
        + '\''
        + ", operationTime="
        + operationTime
        + ", costTime="
        + costTime
        + "ms"
        + ", tenantId='"
        + tenantId
        + '\''
        + ", traceId='"
        + traceId
        + '\''
        + '}';
  }
}
