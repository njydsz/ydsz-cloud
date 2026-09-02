package com.njydsz.cronjob.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * JobAlertLog 视图对象。
 *
 * <p>用于 Controller 层返回告警派发记录，对应实体 {@link com.njydsz.cronjob.domain.entity.job.JobAlertLog}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class JobAlertLogVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 主键 ID */
  private String id;

  /** 预警编码（cronjob 自动生成: CRONJOB-{timestamp}-{ruleId}） */
  private String alertCode;

  /** 触发源类型（cronjob 告警固定为 CRONJOB） */
  private String sourceType;

  /** 规则 ID（映射到 ydsz_job_alert_dispatch.rule_id） */
  private String ruleId;

  /** 规则名称（映射到 ydsz_job_alert_dispatch.title） */
  private String ruleName;

  /** 任务 ID（NULL 表示全局告警；映射到 source_id） */
  private String jobId;

  /** 任务 KEY（冗余） */
  private String jobKey;

  /** 告警类型: FAIL / TIMEOUT / SLOW / FAIL_RATE / DURATION_P95 */
  private String alertType;

  /** 告警级别: INFO / WARN / ERROR / CRITICAL */
  private String alertLevel;

  /** 触发时的实际值（如失败率 85.5、耗时 5000） */
  private String triggerValue;

  /** 规则阈值（冗余） */
  private Long threshold;

  /** 实际发送通道（逗号分隔: INAPP,EMAIL,DINGTALK） */
  private String channels;

  /** 告警状态: PENDING / SUCCESS / PARTIAL / FAILED / *_RECOVERY */
  private String alertStatus;

  /** 错误信息（部分通道失败时记录；映射到 fail_reason） */
  private String errorMessage;

  /** 链路追踪 ID（映射到 provider_trace_id） */
  private String traceId;

  /** 触发该告警的任务日志 ID（关联 ydsz_job_log.id） */
  private String triggerLogId;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
