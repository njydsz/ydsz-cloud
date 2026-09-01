package com.njydsz.common.excel.core.reader;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;

import org.apache.poi.ss.formula.FormulaParseException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.ss.util.CellRangeAddress;

/**
 * 轻量级单元格实现 - 用于 SAX 模式解析
 *
 * <p>在 SAX 模式下不需要完整的 POI Cell 对象，但 TypeConvertStrategy 的 convert 方法接收 Cell 参数。此类提供轻量级的实现，
 * 只包含类型转换所需的最小功能。
 *
 * <h3>设计说明</h3>
 *
 * <p>此类仅用于 SAX 模式解析，不依赖 POI 的内部实现。 只实现了类型转换相关的方法，其他方法返回 null 或默认值。
 *
 * @author ydsz-team

 * @version 1.0.0
 * @since 1.0.0
 */
public final class SimpleCell implements Cell {

  /** 单元格值 */
  private final String value;

  /** 单元格类型 */
  private final CellType cellType;

  /**
   * 数值型日期单元格的转换结果（深度完善·方案 B）。
   *
   * <p>fast 路径识别到日期样式（styles.xml numFmt 判定）后，将 Excel 序列值按
   * 1900/1904 窗口转换为 {@link Date} 装载于此；{@link #getDateCellValue()} 与
   * {@link #getLocalDateTimeCellValue()} 据此返回真实日期。非日期单元格为 null。
   */
  private final Date dateValue;

  /**
   * 创建轻量级单元格
   *
   * @param value 单元格值
   * @param cellType 单元格类型
   */
  public SimpleCell(String value, CellType cellType) {
    this(value, cellType, null);
  }

  /**
   * 创建轻量级单元格（含日期值）
   *
   * @param value 单元格原始值（Excel 序列值文本）
   * @param cellType 单元格类型
   * @param dateValue 日期转换结果；非日期单元格传 null
   */
  public SimpleCell(String value, CellType cellType, Date dateValue) {
    this.value = value;
    this.cellType = cellType;
    this.dateValue = dateValue;
  }

  /**
   * 创建数值型日期单元格。
   *
   * @param rawValue Excel 序列值文本
   * @param dateValue 按 1900/1904 窗口转换后的日期
   * @return 装载日期值的轻量单元格
   */
  public static SimpleCell forDate(String rawValue, Date dateValue) {
    return new SimpleCell(rawValue, CellType.NUMERIC, dateValue);
  }

  /**
   * 是否为日期格式的数值单元格（fast 路径样式判定结果）。
   *
   * @return 是返回 true
   */
  public boolean isDateFormatted() {
    return dateValue != null;
  }

  @Override
  public CellType getCellType() {
    return cellType;
  }

  @Override
  public CellType getCachedFormulaResultType() {
    return cellType;
  }

  @Override
  public String getStringCellValue() {
    return value;
  }

  @Override
  public RichTextString getRichStringCellValue() {
    return null;
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
    return dateValue;
  }

  @Override
  public LocalDateTime getLocalDateTimeCellValue() {
    return dateValue == null
        ? null
        : LocalDateTime.ofInstant(dateValue.toInstant(), ZoneId.systemDefault());
  }

  @Override
  public void setCellType(CellType cellType) {}

  @Override
  public void setCellValue(String value) {}

  @Override
  public void setCellValue(double value) {}

  @Override
  public void setCellValue(RichTextString value) {}

  @Override
  public void setCellValue(Date value) {}

  @Override
  public void setCellValue(LocalDateTime value) {}

  @Override
  public void setCellFormula(String formula) throws IllegalStateException, FormulaParseException {}

  @Override
  public String getCellFormula() {
    return null;
  }

  @Override
  public void setBlank() {}

  @Override
  public byte getErrorCellValue() {
    return 0;
  }

  @Override
  public void setCellErrorValue(byte error) {}

  @Override
  public int getColumnIndex() {
    return 0;
  }

  @Override
  public int getRowIndex() {
    return 0;
  }

  @Override
  public Sheet getSheet() {
    return null;
  }

  @Override
  public Row getRow() {
    return null;
  }

  @Override
  public CellStyle getCellStyle() {
    return null;
  }

  @Override
  public void setCellStyle(CellStyle style) {}

  @Override
  public CellAddress getAddress() {
    return null;
  }

  @Override
  public void setAsActiveCell() {}

  @Override
  public Comment getCellComment() {
    return null;
  }

  @Override
  public void setCellComment(Comment comment) {}

  @Override
  public void removeCellComment() {}

  @Override
  public Hyperlink getHyperlink() {
    return null;
  }

  @Override
  public void setHyperlink(Hyperlink link) {}

  @Override
  public void removeHyperlink() {}

  @Override
  public CellRangeAddress getArrayFormulaRange() {
    return null;
  }

  @Override
  public boolean isPartOfArrayFormulaGroup() {
    return false;
  }

  @Override
  public void removeFormula() {}

  @Override
  public void setCellValue(boolean value) {}

  @Override
  public void setCellValue(Calendar value) {}
}
