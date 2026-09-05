package com.njydsz.generator.domain.tool;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.njydsz.common.util.date.DateUtils;

/**
 * Velocity 日期工具对象（模板中通过 {@code $dateTool} 调用）。
 *
 * <p>提供日期格式化辅助方法，用于代码生成模板中的日期输出。
 * 复用 {@link com.njydsz.common.util.date.DateUtils} 中预定义的线程安全共享格式化器，
 * 避免每次调用都创建新的 {@link DateTimeFormatter} 实例。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
public class VelocityDateTool {

  /**
   * 获取当前日期时间字符串（默认格式 yyyy-MM-dd HH:mm:ss）。
   *
   * @return 当前日期时间字符串
   */
  public String now() {
    return DateUtils.now();
  }

  /**
   * 获取当前日期字符串（格式 yyyy-MM-dd）。
   *
   * @return 当前日期字符串
   */
  public String today() {
    return DateUtils.today();
  }

  /**
   * 格式化当前日期时间。
   *
   * @param pattern 日期时间格式
   * @return 格式化后的字符串
   */
  public String format(String pattern) {
    return DateUtils.formatNow(pattern);
  }

  /**
   * 将 LocalDateTime 格式化为字符串。
   *
   * @param dateTime 日期时间对象
   * @param pattern  格式
   * @return 格式化后的字符串
   */
  public String format(LocalDateTime dateTime, String pattern) {
    if (dateTime == null) {
      return "";
    }
    String p = pattern != null ? pattern : DateUtils.DEFAULT_DATE_TIME_PATTERN;
    return dateTime.format(DateTimeFormatter.ofPattern(p));
  }

  /**
   * 获取当前年份。
   *
   * @return 当前年份（如 2026）
   */
  public int year() {
    return LocalDate.now().getYear();
  }

  /**
   * 获取当前月份。
   *
   * @return 当前月份（1-12）
   */
  public int month() {
    return LocalDate.now().getMonthValue();
  }

  /**
   * 获取当前日。
   *
   * @return 当前日（1-31）
   */
  public int day() {
    return LocalDate.now().getDayOfMonth();
  }

  /**
   * 获取简写日期（yyyyMMdd）。
   *
   * @return 简写日期字符串
   */
  public String compactDate() {
    return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
  }

  /**
   * 获取时间戳（毫秒）。
   *
   * @return 当前时间戳
   */
  public long timestamp() {
    return System.currentTimeMillis();
  }
}
