package com.njydsz.workflow.server.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeParseException;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.workflow.domain.exception.WorkflowException;
import com.njydsz.workflow.domain.exception.WorkflowExceptionCode;

/**
 * 时间范围校验器。
 *
 * <p>统一校验 analytics / report 接口的时间范围参数，防止超大范围 COUNT 查询导致数据库压力。
 *
 * <p><b>校验规则：</b>
 *
 * <ul>
 *   <li>startDate 和 endDate 均为可选，都不传时使用默认范围（近 30 天）</li>
 *   <li>startDate 不可晚于 endDate</li>
 *   <li>endDate 不可晚于今天（不含未来日期）</li>
 *   <li>range 不可超过 {@link #MAX_RANGE_DAYS} 天（默认 365 天）</li>
 *   <li>date 格式为 ISO-8601（yyyy-MM-dd），不容忍其他格式</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.23
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DateRangeValidator {

  /** 默认查询天数（近 N 天） */
  public static final int DEFAULT_RANGE_DAYS = 30;

  /** 最大查询天数（防 COUNT 超时） */
  public static final int MAX_RANGE_DAYS = 365;

  /** ISO-8601 日期格式 */
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

  /**
   * 校验并规范化时间范围。
   *
   * <p>若 startDate / endDate 均为 null，则默认返回 [今天 - DEFAULT_RANGE_DAYS, 今天]。
   *
   * @param startDateStr 开始日期字符串（ISO-8601 yyyy-MM-dd），可为 null
   * @param endDateStr 结束日期字符串（ISO-8601 yyyy-MM-dd），可为 null
   * @return 规范化后的日期数组 [startDate, endDate]，均非 null
   * @throws IllegalArgumentException 格式错误或范围非法
   */
  public static LocalDate[] validateAndNormalize(String startDateStr, String endDateStr) {
    LocalDate today = LocalDate.now();
    LocalDate startDate = null;
    LocalDate endDate = null;

    // 解析 startDate
    if (startDateStr != null && !startDateStr.isBlank()) {
      startDate = parseDate(startDateStr, "startDate");
    }

    // 解析 endDate
    if (endDateStr != null && !endDateStr.isBlank()) {
      endDate = parseDate(endDateStr, "endDate");
    }

    // 默认值处理
    if (startDate == null && endDate == null) {
      endDate = today;
      startDate = today.minusDays(DEFAULT_RANGE_DAYS);
    } else if (startDate == null) {
      startDate = endDate.minusDays(DEFAULT_RANGE_DAYS);
    } else if (endDate == null) {
      endDate = startDate.plusDays(DEFAULT_RANGE_DAYS);
      if (endDate.isAfter(today)) {
        endDate = today;
      }
    }

    // 校验 startDate <= endDate
    if (startDate.isAfter(endDate)) {
      throw new WorkflowException(WorkflowExceptionCode.FLOW_STATE_INVALID,
          "开始日期不能晚于结束日期: startDate=" + startDate + " endDate=" + endDate);
    }

    // 校验 endDate <= today
    if (endDate.isAfter(today)) {
      throw new WorkflowException(WorkflowExceptionCode.FLOW_STATE_INVALID,
          "结束日期不能晚于今天: endDate=" + endDate);
    }

    // 校验范围不超过 MAX_RANGE_DAYS
    long rangeDays = ChronoUnit.DAYS.between(startDate, endDate);
    if (rangeDays > MAX_RANGE_DAYS) {
      throw new WorkflowException(WorkflowExceptionCode.FLOW_STATE_INVALID,
          "查询时间范围不能超过 " + MAX_RANGE_DAYS + " 天（当前 " + rangeDays + " 天）");
    }

    return new LocalDate[] {startDate, endDate};
  }

  /**
   * 解析单个日期字符串。
   *
   * @param dateStr 日期字符串
   * @param paramName 参数名称（用于错误消息）
   * @return 解析后的 LocalDate
   * @throws IllegalArgumentException 格式错误
   */
  private static LocalDate parseDate(String dateStr, String paramName) {
    try {
      return LocalDate.parse(dateStr.trim(), DATE_FORMAT);
    } catch (DateTimeParseException e) {
      throw new WorkflowException(WorkflowExceptionCode.FLOW_PARSING_ERROR,
          paramName + " 格式不合法（期望 yyyy-MM-dd）: " + dateStr);
    }
  }
}
