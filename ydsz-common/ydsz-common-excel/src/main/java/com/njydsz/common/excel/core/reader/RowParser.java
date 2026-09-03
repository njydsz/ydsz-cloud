package com.njydsz.common.excel.core.reader;

import java.util.HashMap;
import java.util.Map;

import com.njydsz.common.excel.converter.ConverterChain;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.context.AnalysisContext;
import com.njydsz.common.excel.core.metadata.ReadMetadata;
import com.njydsz.common.excel.support.asm.ASMFieldAccessor.FieldSetter;

/**
 * 行解析器 - 负责解析Excel数据行
 *
 * <p>从ExcelReader中提取的职责：
 *
 * <ul>
 *   <li>解析单行数据，映射为实体对象或Map
 *   <li>高性能空行检查
 *   <li>构建检查列索引数组
 *   <li>单元格值类型转换（通过ConverterChain SPI）
 * </ul>
 *
 * @author ydsz-team
 *
 * @version 26.09.01
 * @see ExcelReader
 * @since 26.09.01
 */
public class RowParser {

  private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RowParser.class);

  /** 读取配置元数据 */
  private final ReadMetadata metadata;

  /** 分析上下文 */
  private final AnalysisContext context;

  /** 列索引对应的ASM Setter访问器缓存 */
  private final Map<Integer, FieldSetter> fieldSetterCache;

  /** 列索引对应的目标类型缓存 */
  private final Map<Integer, Class<?>> targetTypeCache;

  /** 转换器链 - 通过SPI机制管理类型转换 */
  private final ConverterChain converterChain;

  private ExcelConfig getExcelConfig() {
    return metadata.getExcelConfig() != null ? metadata.getExcelConfig() : ExcelConfig.defaults();
  }

  /**
   * 构造行解析器
   *
   * @param metadata 读取配置元数据
   * @param context 分析上下文
   */
  public RowParser(ReadMetadata metadata, AnalysisContext context) {
    this.metadata = metadata;
    this.context = context;
    this.fieldSetterCache = new HashMap<>(16);
    this.targetTypeCache = new HashMap<>(16);
    this.converterChain = new ConverterChain();
  }
}
