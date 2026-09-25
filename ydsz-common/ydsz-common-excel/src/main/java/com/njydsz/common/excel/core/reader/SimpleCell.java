package com.njydsz.common.excel.core.reader;

import java.time.LocalDateTime;
import java.util.Date;

import com.njydsz.common.excel.core.reader.sax.ExcelCellType;

/**
 * 轻量级单元格实现 — 用于 SuperFast 读取路径，零 POI 依赖。
 *
 * <p>在 SAX 流式解析中不需要完整 POI Cell 对象，此实现仅保留类型转换所需的最小状态
 * （原始字符串值 + 本地 ExcelCellType + 日期值）。实现 {@link ICell} 接口供 {@link
 * ColumnMetadata.TypeConvertStrategy} 和 {@link RowParser} 消费。
 *
 * <h3>设计说明</h3>
 *
 * <ul>
 *   <li>类型标识已切换为本地 {@link ExcelCellType}，不再引用 POI {@code CellType}
 *   <li>日期值在构造时由 {@link
 *       com.njydsz.common.excel.core.reader.sax.ExcelDateConverter} 预转换后装载——
 *       fast 路径无需在每次类型转换时重复判定样式 + 序列转日期
 *   <li>所有方法均为纯读、无状态副作用
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see ICell
 * @see ColumnMetadata
 */
public final class SimpleCell implements ICell {

  /** 单元格原始字符串值 */
  private final String value;

  /** 单元格本地类型（POI-free） */
  private final ExcelCellType excelCellType;

  /**
   * 数值型日期单元格的转换结果（fast 路径样式判定的日期值）。
   *
   * <p>fast 路径识别到日期样式（styles.xml numFmt 判定）后，将 Excel 序列值按
   * 1900/1904 窗口转换为 {@link LocalDateTime} 装载于此；{@link #getDateCellValue()} 与
   * {@link #getLocalDateTimeCellValue()} 据此返回真实日期。非日期单元格为 null。
   */
  private final LocalDateTime dateValue;

  /**
   * 创建轻量级单元格。
   *
   * @param value 单元格值（已解析的字符串，SST 索引需提前转为实际字符串）
   * @param excelCellType 单元格本地类型
   */
  public SimpleCell(String value, ExcelCellType excelCellType) {
    this(value, excelCellType, null);
  }

  /**
   * 创建轻量级单元格（含预转换日期值）。
   *
   * @param value 单元格原始值字符串（Excel 序列值文本）
   * @param excelCellType 单元格本地类型
   * @param dateValue 日期转换结果（LocalDateTime）；非日期单元格传 null
   */
  public SimpleCell(String value, ExcelCellType excelCellType, LocalDateTime dateValue) {
    this.value = value != null ? value : "";
    this.excelCellType = excelCellType != null ? excelCellType : ExcelCellType.BLANK;
    this.dateValue = dateValue;
  }

  /**
   * 创建数值型日期单元格（fast 路径日期样式判定后调用）。
   *
   * @param rawValue Excel 序列值文本
   * @param dateValue 按 1900/1904 窗口转换后的日期（LocalDateTime）
   * @return 装载日期值的轻量单元格
   */
  public static SimpleCell forDate(String rawValue, LocalDateTime dateValue) {
    return new SimpleCell(rawValue, ExcelCellType.NUMERIC, dateValue);
  }

  @Override
  public ExcelCellType getExcelCellType() {
    return excelCellType;
  }

  @Override
  public String getStringCellValue() {
    return getValue();
  }

  @Override
  public double getNumericCellValue() {
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      return 0.0;
    }
  }

  @Override
  public boolean getBooleanCellValue() {
    return Boolean.parseBoolean(value);
  }

  @Override
  public Date getDateCellValue() {
    return dateValue == null
        ? null
        : Date.from(dateValue.atZone(java.time.ZoneId.systemDefault()).toInstant());
  }

  @Override
  public LocalDateTime getLocalDateTimeCellValue() {
    return dateValue;
  }

  @Override
  public boolean isDateFormatted() {
    return dateValue != null;
  }

  @Override
  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return "SimpleCell{type=" + excelCellType + ", value='" + value + "'"
        + (dateValue != null ? ", date=" + dateValue : "") + "}";
  }
}
