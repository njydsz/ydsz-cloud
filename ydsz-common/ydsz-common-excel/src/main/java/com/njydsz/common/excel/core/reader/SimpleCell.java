package com.njydsz.common.excel.core.reader;

import java.time.LocalDateTime;
import java.util.Date;

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

import com.njydsz.common.excel.core.reader.sax.ExcelCellType;

/**
 * 轻量级单元格实现 - 用于 SAX 模式解析。
 *
 * <p>在 SAX 模式下不需要完整的 POI Cell 对象，但 TypeConvertStrategy 的 convert 方法接收 Cell 参数。
 * 此类提供轻量级的实现，只包含类型转换所需的最小功能。
 *
 * <h3>设计说明</h3>
 *
 * <p>此类仅用于 SAX 模式解析，不依赖 POI 的内部实现（编译期因实现 {@link Cell} 接口而引用 POI 类型）。
 * 类型标识已切换为本地 {@link ExcelCellType}，运行时 {@link #getExcelCellType()} 返回本地枚举。
 *
 * <h3>双轨访问</h3>
 *
 * <ul>
 *   <li>{@link #getExcelCellType()} — 本地 POI-free 枚举（推荐）</li>
 *   <li>{@link #getCellType()} — POI 接口约定桥接，返回等价的 POI {@link CellType}</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @version 26.09.25
 */
public final class SimpleCell implements Cell {

  /** 单元格值 */
  private final String value;

  /** 单元格本地类型（POI-free） */
  private final ExcelCellType excelCellType;

  /**
   * 数值型日期单元格的转换结果（深度完善·方案 B）。
   *
   * <p>fast 路径识别到日期样式（styles.xml numFmt 判定）后，将 Excel 序列值按
   * 1900/1904 窗口转换为 {@link LocalDateTime} 装载于此；{@link #getDateCellValue()} 与
   * {@link #getLocalDateTimeCellValue()} 据此返回真实日期。非日期单元格为 null。
   */
  private final LocalDateTime dateValue;

  /**
   * 创建轻量级单元格。
   *
   * @param value 单元格值
   * @param excelCellType 单元格类型（本地枚举）
   */
  public SimpleCell(String value, ExcelCellType excelCellType) {
    this(value, excelCellType, null);
  }

  /**
   * 创建轻量级单元格（含日期值）。
   *
   * @param value 单元格原始值（Excel 序列值文本）
   * @param excelCellType 单元格类型（本地枚举）
   * @param dateValue 日期转换结果（LocalDateTime）；非日期单元格传 null
   */
  public SimpleCell(String value, ExcelCellType excelCellType, LocalDateTime dateValue) {
    this.value = value;
    this.excelCellType = excelCellType;
    this.dateValue = dateValue;
  }

  /**
   * 创建数值型日期单元格。
   *
   * @param rawValue Excel 序列值文本
   * @param dateValue 按 1900/1904 窗口转换后的日期（LocalDateTime）
   * @return 装载日期值的轻量单元格
   */
  public static SimpleCell forDate(String rawValue, LocalDateTime dateValue) {
    return new SimpleCell(rawValue, ExcelCellType.NUMERIC, dateValue);
  }

  /**
   * 是否为日期格式的数值单元格（fast 路径样式判定结果）。
   *
   * @return true 表示日期格式化单元格
   */
  public boolean isDateFormatted() {
    return dateValue != null;
  }

  /**
   * 获取本地 POI-free 单元格类型。
   *
   * @return ExcelCellType 枚举常量
   */
  public ExcelCellType getExcelCellType() {
    return excelCellType;
  }

  /**
   * 将本地 ExcelCellType 映射为 POI {@link CellType}（向后兼容桥接）。
   *
   * @param type 本地 ExcelCellType
   * @return 等价的 POI CellType 常量；未知类型返回 {@link CellType#_NONE}
   */
  public static CellType mapToPoiCellType(ExcelCellType type) {
    if (type == null) {
      return CellType._NONE;
    }
    switch (type) {
      case STRING:
        return CellType.STRING;
      case NUMERIC:
        return CellType.NUMERIC;
      case BOOLEAN:
        return CellType.BOOLEAN;
      case ERROR:
        return CellType.ERROR;
      case FORMULA:
        return CellType.FORMULA;
      case BLANK:
        return CellType.BLANK;
      default:
        return CellType._NONE;
    }
  }

  /**
   * 将 POI {@link CellType} 反向映射为本地 {@link ExcelCellType}。
   *
   * @param poiType POI CellType 常量
   * @return 等价的本地 ExcelCellType；未知类型返回 {@link ExcelCellType#BLANK}
   */
  public static ExcelCellType mapToPoiCellTypeReverse(CellType poiType) {
    if (poiType == null) {
      return ExcelCellType.BLANK;
    }
    switch (poiType) {
      case STRING:
        return ExcelCellType.STRING;
      case NUMERIC:
        return ExcelCellType.NUMERIC;
      case BOOLEAN:
        return ExcelCellType.BOOLEAN;
      case ERROR:
        return ExcelCellType.ERROR;
      case FORMULA:
        return ExcelCellType.FORMULA;
      case BLANK:
        return ExcelCellType.BLANK;
      default:
        return ExcelCellType.BLANK;
    }
  }

  // =================== POI Cell 接口实现（向后兼容） ===================

  @Override
  public CellType getCellType() {
    return mapToPoiCellType(excelCellType);
  }

  @Override
  public CellType getCachedFormulaResultType() {
    return getCellType();
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

  /**
   * 获取单元格日期值（Date 类型，桥接旧版 API 调用方）。
   *
   * <p>内部已改用 LocalDateTime 存储，此方法为兼容旧版调用方而保留。
   * 新代码推荐使用 {@link #getLocalDateTimeCellValue()} 获取 LocalDateTime 值。
   *
   * @return 对应的 Date 实例，非日期单元格返回 null
   */
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

  /**
   * 轻量单元格的类型不可变（由构造时确定），此方法为空实现。
   *
   * <p>POI 5.x 已弃用 {@code setCellType}，此处仅作接口桥接的空实现（实现 Cell 接口契约）。
   */
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
  public void setCellFormula(String formula) {}

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
