package com.njydsz.common.excel.core.reader;

import java.time.LocalDateTime;
import java.util.Date;

import com.njydsz.common.excel.core.reader.sax.ExcelCellType;

/**
 * 读取器内部单元格抽象接口 — 零 POI 依赖。
 *
 * <p>替代 Apache POI {@code org.apache.poi.ss.usermodel.Cell} 作为 {@link
 * ColumnMetadata.TypeConvertStrategy} 的入参类型，使整个 reader 包在编译期不再绑定 POI。
 * 实现类 {@link SimpleCell} 包含类型转换所需的最小状态（原始值 + 类型 + 日期值），
 * 不含 POI 内部表示的任何语义。
 *
 * <p>本接口仅覆盖读取路径实际用到的方法；单元格写入、样式、公式、超链接、注释等写操作
 * 语义不属于读取器契约。
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see SimpleCell
 * @see ColumnMetadata
 */
public interface ICell {

  /**
   * 获取单元格本地类型（POI-free 枚举）。
   *
   * @return 单元格类型枚举，不会为 {@code null}（空单元格返回 {@link ExcelCellType#BLANK}）
   */
  ExcelCellType getExcelCellType();

  /**
   * 以字符串形式获取单元格值。
   *
   * <p>对于共享字符串（SST）单元格，应返回已解析的原生字符串，不是 SST 索引。
   * 数值/日期单元格返回原始序列值字符串（调用方按需转换）。
   *
   * @return 单元格字符串值；不会为 {@code null}（空返回空字符串）
   */
  String getStringCellValue();

  /**
   * 以数值形式获取单元格值。
   *
   * <p>仅当 {@link #getExcelCellType()} 为 {@link ExcelCellType#NUMERIC} 时有意义。
   * 非数值单元格行为未定义（通常返回 0.0 或 NaN）。
   *
   * @return 单元格数值
   */
  double getNumericCellValue();

  /**
   * 以布尔形式获取单元格值。
   *
   * <p>仅当 {@link #getExcelCellType()} 为 {@link ExcelCellType#BOOLEAN} 时有意义。
   *
   * @return 单元格布尔值
   */
  boolean getBooleanCellValue();

  /**
   * 获取单元格日期值（旧版 java.util.Date 兼容）。
   *
   * <p>非日期单元格返回 {@code null}。新代码推荐使用 {@link #getLocalDateTimeCellValue()}。
   *
   * @return 日期值；非日期单元格返回 {@code null}
   */
  Date getDateCellValue();

  /**
   * 获取单元格日期值（java.time.LocalDateTime）。
   *
   * <p>仅当单元格被判定为日期格式时（fast 路径经 styles.xml numFmt 判定后预转换）返回非 null。
   *
   * @return LocalDateTime 值；非日期单元格返回 {@code null}
   */
  LocalDateTime getLocalDateTimeCellValue();

  /**
   * 是否为日期格式的数值单元格（fast 路径样式判定结果）。
   *
   * <p>{@code true} 表示此单元格的数值应按日期语义转换（{@link #getLocalDateTimeCellValue()}
   * 返回有效值），而非纯数字。
   *
   * @return 日期格式化单元格时返回 {@code true}
   */
  boolean isDateFormatted();

  /**
   * 获取单元格值（字符串形式，与 {@link #getStringCellValue()} 等价）。
   *
   * <p>提供统一的值访问入口，便于 {@link ColumnMetadata.TypeConvertStrategy} 统一处理。
   *
   * @return 单元格原始值字符串
   */
  String getValue();
}
