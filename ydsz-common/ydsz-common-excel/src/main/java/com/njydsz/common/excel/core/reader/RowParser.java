package com.njydsz.common.excel.core.reader;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.converter.ConverterChain;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.context.AnalysisContext;
import com.njydsz.common.excel.core.metadata.ReadMetadata;
import com.njydsz.common.excel.support.asm.ASMFieldAccessor.FieldSetter;
import com.njydsz.common.excel.support.cache.ReflectCache;

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
 * @version 26.09.01
 * @see ExcelReader
 * @since 26.09.01
 */
public class RowParser {

  private static final Logger LOG = LoggerFactory.getLogger(RowParser.class);

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

  /**
   * 构建检查列索引数组 - 用于快速判断行是否为空
   *
   * @param columns 列元数据数组
   * @return 需要检查的列索引数组
   */
  public int[] buildCheckColumnIndices(ColumnMetadata[] columns) {
    if (columns == null || columns.length == 0) {
      return new int[0];
    }
    int[] indices = new int[columns.length];
    for (int i = 0; i < columns.length; i++) {
      indices[i] = columns[i].columnIndex;
    }
    return indices;
  }

  /**
   * 快速判断行是否为空
   *
   * @param row POI行对象
   * @param checkColumnIndices 需要检查的列索引
   * @return 如果所有指定列都为空则返回true
   */
  public boolean isRowEmptyFast(Row row, int[] checkColumnIndices) {
    if (checkColumnIndices == null || checkColumnIndices.length == 0) {
      return false;
    }
    for (int colIndex : checkColumnIndices) {
      Cell cell = row.getCell(colIndex);
      if (cell != null && cell.getCellType() != CellType.BLANK) {
        return false;
      }
    }
    return true;
  }

  /**
   * 解析单行数据
   *
   * @param row POI行对象
   * @param headers 表头列表
   * @param fieldMap 列索引到字段的映射
   * @param columns 列元数据数组
   * @return 解析后的实体对象或Map
   */
  public Object parseRow(Row row, List<String> headers, Map<Integer, Field> fieldMap, ColumnMetadata[] columns) {
    if (metadata.getClazz() == null) {
      return parseAsMap(row, headers, columns);
    }
    return parseAsBean(row, fieldMap, columns);
  }

  /**
   * 以Map形式解析行数据
   */
  private Map<String, Object> parseAsMap(Row row, List<String> headers, ColumnMetadata[] columns) {
    Map<String, Object> result = new LinkedHashMap<>(columns.length);
    for (ColumnMetadata col : columns) {
      Cell cell = row.getCell(col.columnIndex);
      Object value = convertCellValue(cell, col);
      String key = (col.columnIndex < headers.size()) ? headers.get(col.columnIndex) : "col_" + col.columnIndex;
      result.put(key, value);
    }
    return result;
  }

  /**
   * 以Java Bean形式解析行数据
   */
  private Object parseAsBean(Row row, Map<Integer, Field> fieldMap, ColumnMetadata[] columns) {
    try {
      Object instance = createInstance(metadata.getClazz());
      for (ColumnMetadata col : columns) {
        Cell cell = row.getCell(col.columnIndex);
        Object value = convertCellValue(cell, col);
        if (value != null) {
          setFieldValue(instance, col, value);
        }
      }
      return instance;
    } catch (Exception e) {
      LOG.warn("解析行数据失败: {}", e.getMessage());
      return null;
    }
  }

  /**
   * 转换单元格值
   */
  private Object convertCellValue(Cell cell, ColumnMetadata col) {
    if (cell == null) {
      return null;
    }
    if (col.convertStrategy != null) {
      return col.convertStrategy.convert(cell, null);
    }
    return converterChain.convert(cell, col.targetType, null);
  }

  /**
   * 设置字段值
   */
  private void setFieldValue(Object instance, ColumnMetadata col, Object value) {
    if (col.setter != null) {
      col.setter.set(instance, value);
    } else {
      Field field = resolveField(instance.getClass(), col.columnIndex);
      if (field != null) {
        try {
          field.setAccessible(true);
          field.set(instance, value);
        } catch (IllegalAccessException e) {
          LOG.debug("无法设置字段值: {}", e.getMessage());
        }
      }
    }
  }

  /**
   * 解析Field对象
   */
  private Field resolveField(Class<?> clazz, int columnIndex) {
    try {
      Field[] fields = clazz.getDeclaredFields();
      if (columnIndex < fields.length) {
        return fields[columnIndex];
      }
    } catch (Exception e) {
      LOG.debug("无法解析字段: {}", e.getMessage());
    }
    return null;
  }

  /**
   * 创建实例
   */
  private Object createInstance(Class<?> clazz) throws Exception {
    return ReflectCache.getBeanConstructor(clazz).newInstance();
  }
}
