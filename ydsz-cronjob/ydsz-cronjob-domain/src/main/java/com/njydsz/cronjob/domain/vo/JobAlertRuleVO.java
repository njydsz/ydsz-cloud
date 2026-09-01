package com.njydsz.cronjob.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * JobAlertRule 视图对象。
 *
 * <p>用于 Controller 层返回告警规则数据，对应实体 {@link com.njydsz.cronjob.domain.entity.job.JobAlertRule}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class JobAlertRuleVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 主键 ID */
  private String id;

  /** 规则名称 */
  private String ruleName;

  /** 关联任务 ID（NULL 表示全局规则） */
  private String jobId;

  /** 任务 KEY（冗余，全局规则为 NULL） */
  private String jobKey;

  /** 告警类型: FAIL / TIMEOUT / SLOW / FAIL_RATE / DURATION_P95 */
  private String alertType;

  /** 告警级别: INFO / WARN / ERROR / CRITICAL */
  private String alertLevel;

  /** 阈值（FAIL_RATE 百分比 0-100 / SLOW+DURATION_P95 毫秒数；FAIL/TIMEOUT 可空） */
  private Long threshold;

  /** 统计时间窗口（分钟），仅 FAIL_RATE / DURATION_P95 必填 */
  private Integer timeWindowMinutes;

  /** 通知通道（JSON 数组: ["EMAIL","DINGTALK"]） */
  private String channels;

  /** 接收人（JSON 数组: 邮箱/手机号/userId 列表） */
  private String receivers;

  /** 冷却时间（分钟），同一规则在冷却期内不重复告警（默认 10） */
  private Integer cooldownMinutes;

  /** 是否启用: 0 禁用 / 1 启用 */
  private Integer enabled;

  /** 来源类型（如 SLA，用于区分规则业务来源） */
  private String sourceType;

  /** 租户 ID（P0-FIX 补回：与实体一致，AlertScanner 全局规则告警上下文使用） */
  private String tenantId;

  /** 最后告警时间（冷却窗口判定起点） */
  private LocalDateTime lastAlertAt;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
