package com.njydsz.common.excel.core.writer;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.poi.ss.usermodel.Cell;

import com.njydsz.common.excel.core.config.ExcelConfig;

/**
 * 值格式化器 - 负责单元格值设置和日期格式化
 *
 * <p>处理所有类型值到单元格的写入，包括字符串、数字、布尔、日期等类型。 使用DateTimeFormatter缓存避免重复创建格式化器。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ExcelWriter
 */
public class ValueFormatter {

  /** DateTimeFormatter缓存 - 线程安全，无需ThreadLocal */
  private static final Map<String, DateTimeFormatter> DATETIME_FORMATTER_CACHE =
      new ConcurrentHashMap<>();

  /** 默认日期格式 */
  private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

  /** 是否自动去除字符串首尾空格 */
  private final boolean automaticTrim;

  /** Excel 全局配置 */
  private final ExcelConfig excelConfig;

  /**
   * 构造方法
   *
   * @param automaticTrim 是否自动去除字符串首尾空格
   */
  public ValueFormatter(boolean automaticTrim) {
    this(automaticTrim, ExcelConfig.defaults());
  }

  /**
   * 构造方法
   *
   * @param automaticTrim 是否自动去除字符串首尾空格
   * @param excelConfig Excel 全局配置
   */
  public ValueFormatter(boolean automaticTrim, ExcelConfig excelConfig) {
    this.automaticTrim = automaticTrim;
    this.excelConfig = excelConfig != null ? excelConfig : ExcelConfig.defaults();
  }

  /**
   * 高性能单元格值设置
   *
   * <p>使用Java 21模式匹配和直接赋值，减少方法调用层次和对象创建。 对常见类型进行内联优化，避免多余的类型转换。
   *
   * @param cell 单元格对象
   * @param value 值对象
   * @param dateFormat 日期格式(日期类型时使用)
   */
  public void setCellValueFast(Cell cell, Object value, String dateFormat) {
    if (value == null) {
      cell.setBlank();
      return;
    }

    if (value instanceof String s) {
      String processedValue = automaticTrim ? s.trim() : s;
      if (excelConfig.isFormulaInjectionProtection()) {
        // P1 修复：XLSX 路径用空格前缀（撇号在 XLSX 单元格中会字面显示），分路径策略见 FormulaInjectionGuard
        processedValue = excelConfig.sanitizeForXlsx(processedValue);
      }
      cell.setCellValue(processedValue);
    } else if (value instanceof Number n) {
      cell.setCellValue(n.doubleValue());
    } else if (value instanceof Boolean b) {
      cell.setCellValue(b);
    } else if (value instanceof Date d) {
      cell.setCellValue(formatDate(d, dateFormat));
    } else if (value instanceof Calendar c) {
      cell.setCellValue(c);
    } else if (value instanceof LocalDateTime ldt) {
      cell.setCellValue(formatLocalDateTime(ldt, dateFormat));
    } else if (value instanceof LocalDate ld) {
      cell.setCellValue(formatLocalDate(ld, dateFormat));
    } else if (value instanceof LocalTime lt) {
      cell.setCellValue(formatLocalTime(lt));
    } else if (value instanceof YearMonth ym) {
      cell.setCellValue(formatYearMonth(ym));
    } else if (value instanceof Timestamp ts) {
      cell.setCellValue(formatTimestamp(ts, dateFormat));
    } else if (isSqlDate(value)) {
      cell.setCellValue(formatSqlDate(value, dateFormat));
    } else {
      String strValue = value.toString();
      if (excelConfig.isFormulaInjectionProtection()) {
        strValue = excelConfig.sanitizeForXlsx(strValue);
      }
      cell.setCellValue(strValue);
    }
  }

  /**
   * 检测值是否为 Date 类型
   *
   * <p>使用类名检测避免 FQN 引用（Java 无法同时 import java.util.Date 和 Date）
   *
   * @param value 待检测的值
   * @return 如果是 Date 返回 true
   */
  private static boolean isSqlDate(Object value) {
    return value != null && Date.class.isAssignableFrom(value.getClass()) && !Date.class.equals(value.getClass());
  }

  /**
   * 格式化Date
   *
   * @param date 要格式化的日期
   * @param pattern 日期格式 pattern
   * @return 格式化后的字符串
   */
  public String formatDate(Date date, String pattern) {
    String fmt = pattern != null ? pattern : DEFAULT_DATE_FORMAT;
    DateTimeFormatter formatter = getDateTimeFormatter(fmt);
    return date.toInstant().atZone(ZoneId.systemDefault()).format(formatter);
  }

  /**
   * 格式化 LocalDateTime
   *
   * @param ldt LocalDateTime 对象
   * @param pattern 日期格式 pattern
   * @return 格式化后的字符串
   */
  public String formatLocalDateTime(LocalDateTime ldt, String pattern) {
    String fmt = pattern != null ? pattern : excelConfig.getDefaultDateFormat();
    DateTimeFormatter formatter = getDateTimeFormatter(fmt);
    return ldt.format(formatter);
  }

  /**
   * 格式化 LocalDate
   *
   * @param ld LocalDate 对象
   * @param pattern 日期格式 pattern
   * @return 格式化后的字符串
   */
  public String formatLocalDate(LocalDate ld, String pattern) {
    String fmt = pattern != null ? pattern : "yyyy-MM-dd";
    DateTimeFormatter formatter = getDateTimeFormatter(fmt);
    return ld.format(formatter);
  }

  /**
   * 格式化 LocalTime
   *
   * @param lt LocalTime 对象
   * @return 格式化后的字符串
   */
  public String formatLocalTime(LocalTime lt) {
    DateTimeFormatter formatter = getDateTimeFormatter("HH:mm:ss");
    return lt.format(formatter);
  }

  /**
   * 格式化 YearMonth
   *
   * @param ym YearMonth 对象
   * @return 格式化后的字符串
   */
  public String formatYearMonth(YearMonth ym) {
    DateTimeFormatter formatter = getDateTimeFormatter("yyyy-MM");
    return ym.format(formatter);
  }

  /**
   * 格式化 Timestamp
   *
   * @param ts Timestamp 对象
   * @param pattern 日期格式 pattern
   * @return 格式化后的字符串
   */
  public String formatTimestamp(Timestamp ts, String pattern) {
    String fmt = pattern != null ? pattern : DEFAULT_DATE_FORMAT;
    DateTimeFormatter formatter = getDateTimeFormatter(fmt);
    return ts.toInstant().atZone(ZoneId.systemDefault()).format(formatter);
  }

  /**
   * 格式化 SQL Date。
   *
   * @param sqlDateObj SQL 日期对象
   * @param pattern 模式
   * @return 格式化后的字符串
   */
  public String formatSqlDate(Object sqlDateObj, String pattern) {
    String fmt = pattern != null ? pattern : "yyyy-MM-dd";
    DateTimeFormatter formatter = getDateTimeFormatter(fmt);
    // Date extends java.util.Date, use toInstant() for formatting
    return ((Date) sqlDateObj)
        .toInstant()
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(formatter);
  }

  /**
   * 获取DateTimeFormatter(带缓存)
   *
   * @param pattern 日期格式
   * @return DateTimeFormatter实例
   */
  public static DateTimeFormatter getDateTimeFormatter(String pattern) {
    return DATETIME_FORMATTER_CACHE.computeIfAbsent(pattern, DateTimeFormatter::ofPattern);
  }

  /** 清空日期格式化缓存 */
  public static void clearDateFormatCache() {
    DATETIME_FORMATTER_CACHE.clear();
  }

  /**
   * 获取默认日期格式
   *
   * @return 默认日期格式字符串
   */
  public static String getDefaultDateFormat() {
    return DEFAULT_DATE_FORMAT;
  }
}
