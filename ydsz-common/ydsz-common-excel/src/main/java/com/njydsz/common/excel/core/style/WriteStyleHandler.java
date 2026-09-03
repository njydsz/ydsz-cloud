package com.njydsz.common.excel.core.style;

import java.util.HashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;

import com.njydsz.common.excel.annotation.ExcelStyle;

/**
 * Excel写入样式处理器 - 单元格样式创建与缓存
 *
 * <p>负责创建和管理 Excel 单元格样式，支持表头样式和数据样式的独立配置。
 * 采用样式缓存机制，避免相同配置的样式重复创建，提升性能。
 *
 * @author ydsz-team
 * @version 26.09.01
 * @since 26.09.01
 */
public class WriteStyleHandler {

  /** 工作簿引用，用于创建样式和字体 */
  private final Workbook workbook;

  /** 表头样式缓存，避免重复创建相同样式 */
  private final Map<String, CellStyle> headStyleCache;

  /** 数据样式缓存，避免重复创建相同样式 */
  private final Map<String, CellStyle> dataStyleCache;

  /** 颜色名称到索引的缓存 */
  private final Map<String, Short> colorIndexCache;

  /** 默认表头样式（无注解时使用） */
  private CellStyle defaultHeadStyle;

  /** 默认数据样式（无注解时使用） */
  private CellStyle defaultDataStyle;

  /**
   * 构造函数
   *
   * @param workbook Apache POI 工作簿对象
   */
  public WriteStyleHandler(Workbook workbook) {
    this.workbook = workbook;
    this.headStyleCache = new HashMap<>(16);
    this.dataStyleCache = new HashMap<>(16);
    this.colorIndexCache = new HashMap<>(16);
  }

  /**
   * 获取表头样式，优先从缓存读取，不存在则创建并缓存
   *
   * @param styleAnnotation 样式注解
   * @return 表头单元格样式
   */
  public CellStyle getHeadStyle(ExcelStyle styleAnnotation) {
    if (styleAnnotation == null) {
      if (defaultHeadStyle == null) {
        defaultHeadStyle = createDefaultHeadStyle();
      }
      return defaultHeadStyle;
    }
    String key = buildHeadCacheKey(styleAnnotation);
    return headStyleCache.computeIfAbsent(key, k -> createHeadStyle(styleAnnotation));
  }

  /**
   * 获取数据样式，优先从缓存读取，不存在则创建并缓存
   *
   * @param styleAnnotation 样式注解，可为 null
   * @return 数据单元格样式
   */
  public CellStyle getDataStyle(ExcelStyle styleAnnotation) {
    if (styleAnnotation == null) {
      if (defaultDataStyle == null) {
        defaultDataStyle = createDefaultDataStyle();
      }
      return defaultDataStyle;
    }
    String key = buildDataCacheKey(styleAnnotation);
    return dataStyleCache.computeIfAbsent(key, k -> createDataStyle(styleAnnotation));
  }

  /**
   * 构建表头缓存键
   */
  private String buildHeadCacheKey(ExcelStyle s) {
    return String.format("headBold=%s,headFontColor=%s,headBgColor=%s,headSize=%s,headAlign=%s",
        s.headBold(), s.headFontColor(), s.headBackgroundColor(), s.headFontSize(), s.headHorizontalAlignment());
  }

  /**
   * 构建数据缓存键
   */
  private String buildDataCacheKey(ExcelStyle s) {
    return String.format("dataBold=%s,dataFontColor=%s,dataBgColor=%s,dataSize=%s,dataHAlign=%s,dataVAlign=%s,wrap=%s,border=%s",
        s.dataBold(), s.dataFontColor(), s.dataBackgroundColor(), s.dataFontSize(),
        s.dataHorizontalAlignment(), s.dataVerticalAlignment(), s.wrapText(), s.border());
  }

  /**
   * 创建默认表头样式
   */
  private CellStyle createDefaultHeadStyle() {
    CellStyle style = workbook.createCellStyle();
    Font font = workbook.createFont();
    font.setBold(true);
    font.setFontHeightInPoints((short) 11);
    font.setColor(IndexedColors.BLACK.getIndex());
    style.setFont(font);
    style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    style.setAlignment(HorizontalAlignment.CENTER);
    style.setVerticalAlignment(VerticalAlignment.CENTER);
    return style;
  }

  /**
   * 创建默认数据样式
   */
  private CellStyle createDefaultDataStyle() {
    CellStyle style = workbook.createCellStyle();
    Font font = workbook.createFont();
    font.setFontHeightInPoints((short) 10);
    style.setFont(font);
    style.setAlignment(HorizontalAlignment.LEFT);
    style.setVerticalAlignment(VerticalAlignment.CENTER);
    return style;
  }

  /**
   * 根据注解创建表头样式
   */
  private CellStyle createHeadStyle(ExcelStyle s) {
    CellStyle style = workbook.createCellStyle();
    Font font = workbook.createFont();
    font.setBold(s.headBold());
    font.setFontHeightInPoints(s.headFontSize());
    Short fontColor = resolveColor(s.headFontColor());
    if (fontColor != null) {
      font.setColor(fontColor);
    }
    style.setFont(font);

    Short bgColor = resolveColor(s.headBackgroundColor());
    if (bgColor != null) {
      style.setFillForegroundColor(bgColor);
      style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    }

    style.setAlignment(parseHorizontalAlignment(s.headHorizontalAlignment()));
    style.setVerticalAlignment(VerticalAlignment.CENTER);
    applyBorder(style, s);
    return style;
  }

  /**
   * 根据注解创建数据样式
   */
  private CellStyle createDataStyle(ExcelStyle s) {
    CellStyle style = workbook.createCellStyle();
    Font font = workbook.createFont();
    font.setBold(s.dataBold());
    font.setFontHeightInPoints(s.dataFontSize());
    Short fontColor = resolveColor(s.dataFontColor());
    if (fontColor != null) {
      font.setColor(fontColor);
    }
    style.setFont(font);

    Short bgColor = resolveColor(s.dataBackgroundColor());
    if (bgColor != null && !"NO_FILL".equalsIgnoreCase(s.dataBackgroundColor())) {
      style.setFillForegroundColor(bgColor);
      style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    }

    style.setAlignment(parseHorizontalAlignment(s.dataHorizontalAlignment()));
    style.setVerticalAlignment(parseVerticalAlignment(s.dataVerticalAlignment()));

    if (s.wrapText()) {
      style.setWrapText(true);
    }
    applyBorder(style, s);
    return style;
  }

  /**
   * 解析水平对齐方式
   */
  private HorizontalAlignment parseHorizontalAlignment(String align) {
    switch (align.toUpperCase()) {
      case "CENTER":
        return HorizontalAlignment.CENTER;
      case "RIGHT":
        return HorizontalAlignment.RIGHT;
      case "LEFT":
      default:
        return HorizontalAlignment.LEFT;
    }
  }

  /**
   * 解析垂直对齐方式
   */
  private VerticalAlignment parseVerticalAlignment(String align) {
    switch (align.toUpperCase()) {
      case "TOP":
        return VerticalAlignment.TOP;
      case "BOTTOM":
        return VerticalAlignment.BOTTOM;
      case "CENTER":
      default:
        return VerticalAlignment.CENTER;
    }
  }

  /**
   * 应用边框样式
   */
  private void applyBorder(CellStyle style, ExcelStyle s) {
    if (!s.border()) {
      return;
    }
    BorderStyle border = parseBorderStyle(s.borderStyle());
    style.setBorderTop(border);
    style.setBorderBottom(border);
    style.setBorderLeft(border);
    style.setBorderRight(border);

    Short topColor = resolveColor(s.borderTopColor());
    if (topColor != null) style.setTopBorderColor(topColor);
    Short bottomColor = resolveColor(s.borderBottomColor());
    if (bottomColor != null) style.setBottomBorderColor(bottomColor);
    Short leftColor = resolveColor(s.borderLeftColor());
    if (leftColor != null) style.setLeftBorderColor(leftColor);
    Short rightColor = resolveColor(s.borderRightColor());
    if (rightColor != null) style.setRightBorderColor(rightColor);
  }

  /**
   * 解析边框样式
   */
  private BorderStyle parseBorderStyle(String borderStyle) {
    switch (borderStyle.toUpperCase()) {
      case "MEDIUM":
        return BorderStyle.MEDIUM;
      case "THICK":
        return BorderStyle.THICK;
      case "DASHED":
        return BorderStyle.DASHED;
      case "DOTTED":
        return BorderStyle.DOTTED;
      case "THIN":
      default:
        return BorderStyle.THIN;
    }
  }

  /**
   * 解析颜色名称到POI颜色索引
   *
   * @param colorName 颜色名称
   * @return POI颜色索引，解析失败返回 null
   */
  private Short resolveColor(String colorName) {
    if (colorName == null || colorName.isEmpty()) {
      return null;
    }
    return colorIndexCache.computeIfAbsent(colorName, this::lookupColor);
  }

  /**
   * 查找颜色名称对应的索引
   */
  private Short lookupColor(String colorName) {
    try {
      IndexedColors indexedColor = IndexedColors.valueOf(colorName.toUpperCase());
      return indexedColor.getIndex();
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
