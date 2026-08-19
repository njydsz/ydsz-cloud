package com.njydsz.cronjob.infra.entity.job;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 任务告警规则实体（ydsz_job_alert_rule 表，P5 告警 + 监控）。
 *
 * <p>定义告警触发条件、级别、通知通道与去重策略。规则可绑定到具体任务 （{@link #jobId} 非空），也可作为全局规则（{@link #jobId} 为 NULL）应用于所有任务。
 *
 * <h3>告警类型</h3>
 *
 * <ul>
 *   <li>{@code FAIL}：任务执行失败即告警
 *   <li>{@code TIMEOUT}：任务执行超时即告警
 *   <li>{@code SLOW}：任务执行慢（耗时 &gt;= threshold 毫秒）
 *   <li>{@code FAIL_RATE}：时间窗口内失败率 &gt;= threshold（百分比 0-100）
 *   <li>{@code DURATION_P95}：时间窗口内 P95 耗时 &gt;= threshold（毫秒）
 * </ul>
 *
 * <h3>去重策略</h3>
 *
 * <ul>
 *   <li>{@link #cooldownMinutes}：冷却窗口，同一规则在冷却期内不重复告警
 *   <li>{@link #lastAlertAt}：上次告警时间，用于冷却判断
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_job_alert_rule")
public class JobAlertRule extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

  /** 规则名称 */
  private String ruleName;

  /** 关联任务 ID（NULL 表示全局规则） */
  private String jobId;

  /** 任务 KEY 冗余（NULL 表示全局规则） */
  private String jobKey;

  /** 告警类型: FAIL / TIMEOUT / SLOW / FAIL_RATE / DURATION_P95 */
  private String alertType;

  /** 告警级别: INFO / WARN / ERROR / CRITICAL */
  private String alertLevel;

  /** 阈值（按 alertType 解释：FAIL_RATE 百分比 0-100 / SLOW+DURATION_P95 毫秒） */
  private Long threshold;

  /** 统计时间窗口（分钟），仅 FAIL_RATE / DURATION_P95 生效 */
  private Integer timeWindowMinutes;

  /** 通知通道（JSON 数组: ["EMAIL","DINGTALK","WECOM","WEBHOOK"]） */
  private String channels;

  /** 接收人（JSON 数组: 邮箱/手机号/userId 列表） */
  private String receivers;

  /** 冷却时间（分钟），同一规则在冷却期内不重复告警 */
  private Integer cooldownMinutes;

  /** 是否启用: 0 禁用 / 1 启用 */
  private Integer enabled;

  /** 规则来源: MANUAL 手动创建(默认) / SLA 由SLA规则自动生成 */
  private String sourceType;

  /** 最后告警时间（用于冷却判断） */
  private LocalDateTime lastAlertAt;
}
