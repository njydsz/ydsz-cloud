package com.njydsz.userinfo.domain.query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.userinfo.domain.alert.SecurityAlert;

/**
 * 安全告警分页查询参数。
 *
 * <p>用于安全告警分页查询接口，封装过滤条件与分页参数。 所有过滤条件均为可选，未设置则不作为筛选条件。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SecurityAlertPageQuery extends PageQuery {

  /** 告警状态过滤（可为 null 表示不过滤） */
  private SecurityAlert.AlertStatus alertStatus;

  /** 风险等级过滤（可为 null 表示不过滤） */
  private SecurityAlert.RiskLevel riskLevel;

  /** 起始日期过滤（可为 null，自动转换为当天 00:00:00） */
  private LocalDate start;

  /** 结束日期过滤（可为 null，自动转换为当天 23:59:59.999） */
  private LocalDate end;

  /** 起始时间过滤（可为 null，优先于 {@link #start} 使用） */
  private LocalDateTime startTime;

  /** 结束时间过滤（可为 null，优先于 {@link #end} 使用） */
  private LocalDateTime endTime;

  /**
   * 获取生效的起始时间：显式 startTime 优先，否则由 start 日期转换。
   *
   * @return 起始时间；未设置时返回 null
   */
  public LocalDateTime effectiveStartTime() {
    if (startTime != null) {
      return startTime;
    }
    return start != null ? start.atStartOfDay() : null;
  }

  /**
   * 获取生效的结束时间：显式 endTime 优先，否则由 end 日期转换。
   *
   * @return 结束时间；未设置时返回 null
   */
  public LocalDateTime effectiveEndTime() {
    if (endTime != null) {
      return endTime;
    }
    return end != null ? end.atTime(LocalTime.MAX) : null;
  }
}
