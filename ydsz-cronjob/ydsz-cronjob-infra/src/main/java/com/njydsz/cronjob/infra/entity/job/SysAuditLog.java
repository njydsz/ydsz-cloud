package com.njydsz.cronjob.infra.entity.job;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 操作审计日志实体（P1-14 操作审计视图）。
 *
 * <p>对应 <code>ydsz_job_audit_log</code> 表（由 ydsz-common-audit 模块管理），
 * 本实体仅用于查询，不执行写入操作。写入由 common-audit 模块的 {@code AuditWriter} 完成。
 *
 * <p>cronjob 模块通过 {@code module = 'cronjob'} 过滤只查看与任务调度相关的审计记录。
 *
 * <p><b>注意</b>：本实体不继承 {@code MpBaseIdEntity}，因为 ydsz_job_audit_log 表可能不存在于
 * 所有部署环境（取决于是否引入 common-audit 模块），且本模块不管理该表的 DDL。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Getter
@Setter
@TableName("ydsz_job_audit_log")
public class SysAuditLog {

  @Serial private static final long serialVersionUID = 1L;

  /** 审计记录唯一标识（雪花算法生成） */
  private String id;

  /** 审计类型编码 */
  private Integer auditType;

  /** 操作行为编码 */
  private Integer action;

  /** 审计状态编码 */
  private Integer status;

  /** 模块名称（cronjob 模块为 'cronjob'） */
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

  /** 请求参数（已脱敏/截断） */
  private String requestParams;

  /** 响应结果（已脱敏/截断） */
  private String responseResult;

  /** 错误信息 */
  private String errorMessage;

  /** 执行耗时（毫秒） */
  private Long costTime;

  /** 应用标识 */
  private String appKey;

  /** 租户 ID */
  private String tenantId;

  /** 链路追踪 ID */
  private String traceId;

  /** 创建时间 */
  private LocalDateTime createdAt;
}
