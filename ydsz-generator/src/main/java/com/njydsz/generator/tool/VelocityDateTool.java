package com.njydsz.generator.tool;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Velocity 模板日期工具类。
 *
 * <p>提供日期格式化辅助方法，在 Velocity 模板中通过 {@code $date} 访问。
 *
 * <p>可用方法：
 * <ul>
 *   <li>{@link #now()} — 当前日期时间（yyyy-MM-dd HH:mm:ss）</li>
 *   <li>{@link #today()} — 当前日期（yyyy-MM-dd）</li>
 *   <li>{@link #format(LocalDateTime, String)} — 自定义格式化</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.04
 */
public final class VelocityDateTool {

  /** 标准日期时间格式。 */
  private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  /** 标准日期格式。 */
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  /** 默认构造方法私有。 */
  private VelocityDateTool() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 获取当前日期时间字符串。
   *
   * @return 格式为 yyyy-MM-dd HH:mm:ss 的字符串
   */
  public static String now() {
    return LocalDateTime.now().format(DATETIME_FMT);
  }

  /**
   * 获取当前日期字符串。
   *
   * @return 格式为 yyyy-MM-dd 的字符串
   */
  public static String today() {
    return LocalDate.now().format(DATE_FMT);
  }

  /**
   * 按指定格式格式化日期时间。
   *
   * @param dateTime 日期时间对象
   * @param pattern  格式模式（如 yyyy/MM/dd）
   * @return 格式化字符串，null 输入返回空字符串
   */
  public static String format(final LocalDateTime dateTime, final String pattern) {
    if (dateTime == null || pattern == null) {
      return "";
    }
    return dateTime.format(DateTimeFormatter.ofPattern(pattern));
  }
}
