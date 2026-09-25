package com.njydsz.common.excel.core.reader.sax;

/**
 * Excel 单元格类型枚举 —— POI-free 本地化实现。
 *
 * <p>与 {@code org.apache.poi.ss.usermodel.CellType} 值集对齐（{@code BLANK / BOOLEAN /
 * ERROR / FORMULA / NUMERIC / STRING}），使 SuperFast 读写引擎完全脱离 POI 编译时依赖。
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public enum ExcelCellType {

  /** 空白单元格 */
  BLANK,

  /** 布尔类型 */
  BOOLEAN,

  /** 公式错误类型 */
  ERROR,

  /** 公式类型 */
  FORMULA,

  /** 数值类型 */
  NUMERIC,

  /** 字符串类型 */
  STRING
}
