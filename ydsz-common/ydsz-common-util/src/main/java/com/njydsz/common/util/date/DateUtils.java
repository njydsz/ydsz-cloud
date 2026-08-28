package com.njydsz.common.util.date;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import com.njydsz.common.util.api.Experimental;

/**
 * 日期时间工具类（基于 java.time API）
 *
 * <p>提供 Java 8+ 日期时间 API 的高频操作方法，线程安全。 涵盖：日期边界（日开始/结束/月开始/结束）、日期间差值计算、 工作日计算、格式化与解析、常见判断（闰年/周末/今天）。
 *
 * <p><b>线程安全：</b>所有方法均使用 {@link java.time.format.DateTimeFormatter}， 它是线程安全的（与 {@link
 * java.text.SimpleDateFormat} 不同）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Experimental("零采用；节假日日历与国际化场景待验证")
public final class DateUtils {

  /** 默认日期时间格式：yyyy-MM-dd HH:mm:ss */
  public static final String DEFAULT_DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

  /** 默认日期格式：yyyy-MM-dd */
  public static final String DEFAULT_DATE_PATTERN = "yyyy-MM-dd";

  /** 默认日期时间格式化器（yyyy-MM-dd HH:mm:ss），线程安全可共享 */
  private static final DateTimeFormatter DEFAULT_DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_PATTERN);

  /** 默认日期格式化器（yyyy-MM-dd），线程安全可共享 */
  private static final DateTimeFormatter DEFAULT_DATE_FORMATTER =
      DateTimeFormatter.ofPattern(DEFAULT_DATE_PATTERN);

  /** 一天的开始小时 */
  private static final int START_HOUR = 0;

  /** 一天的结束小时 */
  private static final int END_HOUR = 23;

  /** 一天的结束分钟 */
  private static final int END_MINUTE = 59;

  /** 一天的结束秒 */
  private static final int END_SECOND = 59;

  /** 一天的最大纳秒值（23:59:59.999999999） */
  private static final int END_NANO = 999_999_999;

  private DateUtils() {
    throw new UnsupportedOperationException(
        "DateUtils is a utility class and cannot be instantiated");
  }

  // ==================== 闰年判断 ====================

  /**
   * 判断指定年份是否为闰年。
   *
   * @param year 年份
   * @return 是否为闰年
   */
  public static boolean isLeapYear(int year) {
    return Year.of(year).isLeap();
  }

  // ==================== 日期边界 ====================

  /**
   * 获取某天的开始时刻（00:00:00.000000000）。
   *
   * @param date 日期，不能为 null
   * @return 当天 00:00:00 的 LocalDateTime
   * @throws NullPointerException 如果 date 为 null
   */
  public static LocalDateTime getStartOfDay(LocalDate date) {
    Objects.requireNonNull(date, "date must not be null");
    return date.atTime(START_HOUR, START_HOUR, START_HOUR);
  }

  /**
   * 获取某天的结束时刻（23:59:59.999999999）。
   *
   * @param date 日期，不能为 null
   * @return 当天 23:59:59.999999999 的 LocalDateTime
   * @throws NullPointerException 如果 date 为 null
   */
  public static LocalDateTime getEndOfDay(LocalDate date) {
    Objects.requireNonNull(date, "date must not be null");
    return date.atTime(END_HOUR, END_MINUTE, END_SECOND).plusNanos(END_NANO);
  }

  /**
   * 获取某天所在月的 1 号 00:00:00。
   *
   * @param date 日期，不能为 null
   * @return 当月 1 号 00:00:00 的 LocalDateTime
   * @throws NullPointerException 如果 date 为 null
   */
  public static LocalDateTime getStartOfMonth(LocalDate date) {
    Objects.requireNonNull(date, "date must not be null");
    LocalDate firstDay = date.withDayOfMonth(1);
    return getStartOfDay(firstDay);
  }

  /**
   * 获取某天所在月的最后一天 23:59:59.999999999。
   *
   * @param date 日期，不能为 null
   * @return 当月最后一天的 23:59:59.999999999 的 LocalDateTime
   * @throws NullPointerException 如果 date 为 null
   */
  public static LocalDateTime getEndOfMonth(LocalDate date) {
    Objects.requireNonNull(date, "date must not be null");
    LocalDate lastDay = date.withDayOfMonth(date.lengthOfMonth());
    return getEndOfDay(lastDay);
  }

  // ==================== 日期差值计算 ====================

  /**
   * 计算两个日期之间的天数差（end - start），使用 {@link ChronoUnit#DAYS}。
   *
   * <p>结果可能为负数（end 在 start 之前）。
   *
   * @param start 起始日期，不能为 null
   * @param end 结束日期，不能为 null
   * @return 天数差
   * @throws NullPointerException 如果任一参数为 null
   */
  public static long daysBetween(LocalDate start, LocalDate end) {
    Objects.requireNonNull(start, "start must not be null");
    Objects.requireNonNull(end, "end must not be null");
    return ChronoUnit.DAYS.between(start, end);
  }

  /**
   * 计算两个日期之间相差的完整月数。
   *
   * <p>结果可能为负数（end 在 start 之前）。
   *
   * @param start 起始日期，不能为 null
   * @param end 结束日期，不能为 null
   * @return 完整月数
   * @throws NullPointerException 如果任一参数为 null
   */
  public static long monthsBetween(LocalDate start, LocalDate end) {
    Objects.requireNonNull(start, "start must not be null");
    Objects.requireNonNull(end, "end must not be null");
    return ChronoUnit.MONTHS.between(start, end);
  }

  // ==================== 日期判断 ====================

  /**
   * 判断两个日期是否为同一天。
   *
   * @param date1 第一个日期
   * @param date2 第二个日期
   * @return 是否同一天
   */
  public static boolean isSameDay(LocalDate date1, LocalDate date2) {
    if (date1 == null || date2 == null) {
      return false;
    }
    return date1.isEqual(date2);
  }

  /**
   * 判断指定日期是否是今天。
   *
   * @param date 日期，不能为 null
   * @return 是否是今天
   * @throws NullPointerException 如果 date 为 null
   */
  public static boolean isToday(LocalDate date) {
    Objects.requireNonNull(date, "date must not be null");
    return date.isEqual(LocalDate.now());
  }

  /**
   * 判断指定日期是否是周末（周六或周日）。
   *
   * @param date 日期，不能为 null
   * @return 是否是周末
   * @throws NullPointerException 如果 date 为 null
   */
  public static boolean isWeekend(LocalDate date) {
    Objects.requireNonNull(date, "date must not be null");
    DayOfWeek dayOfWeek = date.getDayOfWeek();
    return DayOfWeek.SATURDAY.equals(dayOfWeek) || DayOfWeek.SUNDAY.equals(dayOfWeek);
  }

  // ==================== 年龄计算 ====================

  /**
   * 根据出生日期计算周岁。
   *
   * <p>使用 {@link Period#between} 计算完整年数，未过生日不计入。
   *
   * @param birthDate 出生日期，不能为 null
   * @return 周岁年龄，最小为 0
   * @throws NullPointerException 如果 birthDate 为 null
   * @throws DateTimeException 如果出生日期在未来
   */
  public static int getAge(LocalDate birthDate) {
    Objects.requireNonNull(birthDate, "birthDate must not be null");
    LocalDate today = LocalDate.now();
    if (birthDate.isAfter(today)) {
      throw new DateTimeException("birthDate must not be in the future");
    }
    return Period.between(birthDate, today).getYears();
  }

  // ==================== 格式化与解析 ====================

  /**
   * 按指定模式解析字符串为 {@link LocalDate}。
   *
   * @param text 待解析文本，不能为 null
   * @param pattern 日期格式模式（如 yyyy-MM-dd），不能为 null
   * @return 解析后的 LocalDate
   * @throws NullPointerException 如果任一参数为 null
   * @throws DateTimeParseException 如果文本无法按指定模式解析
   */
  public static LocalDate parseLocalDate(String text, String pattern) {
    Objects.requireNonNull(text, "text must not be null");
    Objects.requireNonNull(pattern, "pattern must not be null");
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
    return LocalDate.parse(text, formatter);
  }

  /**
   * 按指定模式格式化 {@link LocalDate} 为字符串。
   *
   * @param date 日期，不能为 null
   * @param pattern 日期格式模式（如 yyyy-MM-dd），不能为 null
   * @return 格式化后的字符串
   * @throws NullPointerException 如果任一参数为 null
   */
  public static String formatLocalDate(LocalDate date, String pattern) {
    Objects.requireNonNull(date, "date must not be null");
    Objects.requireNonNull(pattern, "pattern must not be null");
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
    return date.format(formatter);
  }

  /**
   * 按 ISO 格式（yyyy-MM-ddTHH:mm:ss）解析字符串为 {@link LocalDateTime}。
   *
   * @param text 待解析文本，不能为 null
   * @return 解析后的 LocalDateTime
   * @throws NullPointerException 如果 text 为 null
   * @throws DateTimeParseException 如果文本格式不符合 ISO 标准
   */
  public static LocalDateTime parseLocalDateTime(String text) {
    Objects.requireNonNull(text, "text must not be null");
    return LocalDateTime.parse(text);
  }

  /**
   * 按 ISO 格式（yyyy-MM-ddTHH:mm:ss）格式化 {@link LocalDateTime} 为字符串。
   *
   * @param dateTime 日期时间，不能为 null
   * @return 格式化后的字符串（ISO 格式）
   * @throws NullPointerException 如果 dateTime 为 null
   */
  public static String formatLocalDateTime(LocalDateTime dateTime) {
    Objects.requireNonNull(dateTime, "dateTime must not be null");
    return dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
  }

  // ==================== 工作日计算 ====================

  /**
   * 从指定日期增加指定个工作日（跳过周六、周日）。
   *
   * <p>如果 days 为负数，则向过去推算工作日。
   *
   * @param date 起始日期，不能为 null
   * @param days 要增加的工作日数（可为负数）
   * @return 增加工作日后的日期
   * @throws NullPointerException 如果 date 为 null
   */
  public static LocalDate addBusinessDays(LocalDate date, int days) {
    Objects.requireNonNull(date, "date must not be null");
    if (days == 0) {
      return date;
    }
    LocalDate result = date;
    int step = days > 0 ? 1 : -1;
    int remaining = Math.abs(days);
    while (remaining > 0) {
      result = result.plusDays(step);
      if (!isWeekend(result)) {
        remaining--;
      }
    }
    return result;
  }
}
