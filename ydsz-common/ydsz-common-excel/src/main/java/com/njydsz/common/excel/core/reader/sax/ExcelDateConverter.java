package com.njydsz.common.excel.core.reader.sax;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Excel 序列日期转换器 —— 将 Excel OLE 自动日期序列值转换为 {@link LocalDateTime}。
 *
 * <p>替代 {@code org.apache.poi.ss.usermodel.DateUtil#getJavaDate}，消除 SuperFast 引擎对 POI 运行时的依赖。
 *
 * <h3>算法说明</h3>
 *
 * <ul>
 *   <li>1900 日期系统（Windows 默认）：第 0 天 = 1899-12-30，第 1 天 = 1900-01-01
 *   <li>1904 日期系统（Mac 可配置）：第 0 天 = 1904-01-01
 *   <li>Excel 1900 年闰年 bug：Excel 将 1900-02-29 视为有效日期（第 59 天）——
 *       实际日历中 1900 年不是闰天。序列值 >= 61（即 1900-03-01）时自动 -1 补偿
 * </ul>
 *
 * <h3>精度</h3>
 *
 * <ol>
 *   <li>毫秒四舍五入（进位制舍入），与 POI 行为对齐</li>
 *   <li>ZoneId.systemDefault() 转为本地时间，与 POI DateUtil.getJavaDate 的 Date 语义一致，
 *       由调用方按 ZoneId 转 LocalDateTime</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public final class ExcelDateConverter {

  private ExcelDateConverter() {}

  /** 一天的毫秒数 */
  private static final long DAY_MILLISECONDS = 86_400_000L;

  /**
   * 将 Excel OLE 序列日期值转换为 {@code java.util.Date}。
   *
   * @param date Excel 日期序列值（整数部分为天数，小数部分为当天时刻）
   * @param use1904Windowing {@code true} 使用 Mac 1904 日期系统
   * @return 对应的 Date 对象
   */
  public static java.util.Date getJavaDate(double date, boolean use1904Windowing) {
    return getCalendar(date, use1904Windowing).getTime();
  }

  /**
   * 将 Excel OLE 序列日期值转换为 {@code java.time.LocalDateTime}。
   *
   * <p>与 {@link #getJavaDate(double, boolean)} 等价，但直接生成本地时间，省去 Date 桥接。
   *
   * @param date Excel 日期序列值
   * @param use1904Windowing {@code true} 使用 Mac 1904 日期系统
   * @return 当前时区下对应的 LocalDateTime
   */
  public static LocalDateTime getLocalDateTime(double date, boolean use1904Windowing) {
    Calendar cal = getCalendar(date, use1904Windowing);
    return LocalDateTime.ofInstant(cal.toInstant(), ZoneId.systemDefault());
  }

  private static Calendar getCalendar(double date, boolean use1904Windowing) {
    int wholeDays = (int) Math.floor(date);
    long millisecondsInDay = Math.round((date - wholeDays) * DAY_MILLISECONDS);

    Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
    calendar.set(1899, Calendar.DECEMBER, 30, 0, 0, 0);
    calendar.set(Calendar.MILLISECOND, 0);

    // Excel 1900 闰年 bug：第 59 天不存在（1900-02-29），序列值 >= 60 需 -1 补偿
    if (!use1904Windowing && wholeDays >= 60) {
      wholeDays--;
    }

    calendar.add(Calendar.DATE, wholeDays);
    calendar.add(Calendar.MILLISECOND, (int) millisecondsInDay);

    return calendar;
  }
}
