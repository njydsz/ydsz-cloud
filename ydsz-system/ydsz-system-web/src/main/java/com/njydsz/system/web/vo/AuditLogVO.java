package com.njydsz.system.web.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 审计日志视图对象（管理后台）
 *
 * <p>用于替代 {@link com.njydsz.common.audit.domain.AuditLog} 实体直接暴露给前端，
 * 仅包含审计列表查询所需的展示字段，避免泄露 requestParams / responseResult /
 * diffBeforeSnapshot / diffAfterSnapshot 等敏感或大字段。
 *
 * <p>数据来源：由 {@link com.njydsz.common.audit.domain.AuditLog} 经 {@code toVO}/{@code toVOList} 转换而来。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class AuditLogVO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 审计记录唯一标识（雪花算法生成） */
  private String id;

  /** 审计类型编码 */
  private Integer auditType;

  /** 操作行为编码 */
  private Integer action;

  /** 审计状态编码 */
  private Integer status;

  /** 模块名称 */
  private String module;

  /** 操作内容描述 */
  private String content;

  /** 业务流水号 */
  private String businessNo;

  /** 操作人 ID */
  private String operatorId;

  /** 操作人姓名 */
  private String operatorName;

  /** 操作时间 */
  private LocalDateTime operationTime;

  /** 请求 IP 地址 */
  private String ipAddress;

  /** 执行耗时（毫秒） */
  private Long costTime;

  /** 链路追踪 ID */
  private String traceId;

  /** 应用标识 */
  private String appKey;

  /** 租户 ID */
  private String tenantId;
}
