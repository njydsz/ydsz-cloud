package com.njydsz.common.excel.core.style;

/**
 * WriteStyleHandler 类
 *
 * @author ydsz-team

 * @version 26.09.01
 */
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
 * <p>负责创建和管理 Excel 单元格样式，支持表头样式和数据样式的独立配置。 采用样式缓存机制，避免相同配置的样式重复创建，提升性能。
 *
 * <h3>核心功能</h3>
 *
 * <ul>
 *   <li>表头样式创建 - 支持加粗、字体颜色、背景色、对齐方式等
 *   <li>数据样式创建 - 支持数据行的各种样式配置
 *   <li>样式缓存 - 相同配置的样式不会重复创建
 *   <li>颜色解析 - 支持预定义颜色名称到POI颜色索引的转换
 * </ul>
 *
 * <h3>设计模式</h3>
 *
 * <ul>
 *   <li>享元模式 - 通过缓存复用相同样式
 *   <li>构建器模式 - StyleKey 使用 Builder 模式构建缓存键
 * </ul>
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * WriteStyleHandler handler = new WriteStyleHandler(workbook);
 *
 * // 获取表头样式
 * CellStyle headStyle = handler.getHeadStyle(annotation);
 *
 * // 获取数据样式
 * CellStyle dataStyle = handler.getDataStyle(annotation);
 *
 * // 应用样式
 * cell.setCellStyle(headStyle);
 * }</pre>
 *
 * @see CellStyle
 * @see Font
 * @see ExcelStyle
 * @author ydsz-team
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

  /**
   * 构造函数
   *
   * @param workbook Apache POI 工作簿对象
   */
  public WriteStyleHandler(Workbook workbook) {
    this.workbook = workbook;
    this.headStyleCache = new HashMap<>(16);