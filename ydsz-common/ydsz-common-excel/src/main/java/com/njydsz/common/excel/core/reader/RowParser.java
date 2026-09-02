new HashMap<>(16)eader;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.excel.converter.ConvertContext;
import com.njydsz.common.excel.converter.ConverterChain;
import com.njydsz.common.excel.converter.ConverterRegistry;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.context.AnalysisContext;
import com.njydsz.common.excel.core.metadata.ReadMetadata;
import com.njydsz.common.excel.support.asm.ASMFieldAccessor.FieldSetter;
import com.njydsz.common.excel.support.asm.ASMFieldAccessor.ObjectInstantiator;
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
    this.fieldSetterCache = new HashMap<>();
    this.targetTypeCache = new HashMap<>(16);
    this.converterChain = ConverterRegistry.getDefaultChain();
  }

  /**
   * 解析单行数据
   *
   * <p>根据是否指定了Class决定返回值类型:
   *
   * <ul>
   *   <li>指定Class - 返回映射的实体对象
   *   <li>未指定Class - 返回Map&lt;String, Object&gt;
   * </ul>
   *
   * @param row Excel行对象
   * @param headers 表头列表
   * @param fieldMap 列索引与字段的映射
   * @param columnMetadataArray 列元数据数组
   * @return 解析后的数据对象
   */
  public Object parseRow(
      Row row,
      List<String> headers,
      Map<Integer, Field> fieldMap,
      ColumnMetadata[] columnMetadataArray) {
    if (metadata.getClazz() == null) {
      Map<String, Object> data = new LinkedHashMap<>(16);
      for (int col = 0; col < headers.size(); col++) {
        Cell cell = row.getCell(col);
        Object value = getCellValue(cell);
        data.put(headers.get(col), value);
      }
      return data;
    }

    try {
      ObjectInstantiator instantiator = ReflectCache.getInstantiator(metadata.getClazz());
      Object obj = instantiator.newInstance();

      if (columnMetadataArray != null) {
        for (ColumnMetadata colMeta : columnMetadataArray) {
          Cell cell = row.getCell(colMeta.columnIndex, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK);

          if (cell == null || cell.getCellType() == CellType.BLANK) {
            continue;
          }

          Object value = convertCellValueFast(cell, colMeta.targetType);
          colMeta.setter.set(obj, value);
        }
      } else {
        for (Map.Entry<Integer, Field> entry : fieldMap.entrySet()) {
          int col = entry.getKey();
          Field field = entry.getValue();
          Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK);

          if (cell == null || cell.getCellType() == CellType.BLANK) {
            continue;
          }

          Class<?> targetType = targetTypeCache.computeIfAbsent(col, k -> field.getType());
          Object value = convertCellValueFast(cell, targetType);
          FieldSetter setter =
              fieldSetterCache.computeIfAbsent(
                  col, k -> ReflectCache.getFieldSetter(metadata.getClazz(), field));
          setter.set(obj, value);
        }
      }

      return obj;
    } catch (Exception e) {
      LOG.error("创建对象实例异常", e);
      return null;
    }
  }

  /**
   * 高性能空行检查 - 仅检查有映射的列
   *
   * <p>相比原始的isEmptyRow方法，此方法只检查我们需要映射的列， 避免遍历整个Excel行中的所有单元格，显著提升性能。
   *
   * @param row 行对象
   * @param checkIndices 需要检查的列索引数组，null时检查整行
   * @return true表示空行，false表示非空行
   */
  public boolean isRowEmptyFast(Row row, int[] checkIndices) {
    if (row == null) {
      return true;
    }

    if (checkIndices != null && checkIndices.length > 0) {
      for (int colIndex : checkIndices) {
        Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
          continue;
        }
        if (cell.getCellType() == CellType.STRING) {
          String value = cell.getStringCellValue();
          if (value != null && !value.trim().isEmpty()) {
            return false;
          }
        } else {
          return false;
        }
      }
      return true;
    }

    short lastCellNum = row.getLastCellNum();
    if (lastCellNum <= 0) {
      return true;
    }

    for (int i = 0; i < lastCellNum; i++) {
      Cell cell = row.getCell(i);
      if (cell != null) {
        CellType cellType = cell.getCellType();
        if (cellType == CellType.STRING) {
          String value = cell.getStringCellValue();
          if (value != null && !value.trim().isEmpty()) {
            return false;
          }
        } else if (cellType != CellType.BLANK) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * 构建检查列索引数组
   *
   * <p>提取所有需要映射的列索引，用于快速空行检查。 避免检查无关的列，提升跳过空行场景下的性能。
   *
   * @param columnMetadataArray 列元数据数组
   * @return 需要检查的列索引数组
   */
  public int[] buildCheckColumnIndices(ColumnMetadata[] columnMetadataArray) {
    if (columnMetadataArray != null && columnMetadataArray.length > 0) {
      int[] indices = new int[columnMetadataArray.length];
      for (int i = 0; i < columnMetadataArray.length; i++) {
        indices[i] = columnMetadataArray[i].columnIndex;
      }
      return indices;
    }
    return null;
  }

  // ==================== 类型转换方法 ====================

  /**
   * 高性能类型转换 - 通过ConverterChain SPI委托
   *
   * <p>从Cell中提取原始值，然后委托给ConverterChain进行类型转换。 Cell值提取逻辑保留在RowParser中，类型转换逻辑由SPI转换器链处理。
   *
   * @param cell 单元格对象
   * @param targetType 目标类型
   * @return 转换后的值
   */
  private Object convertCellValueFast(Cell cell, Class<?> targetType) {
    if (cell == null) {
      return null;
    }

    CellType cellType = cell.getCellType();
    if (cellType == CellType.BLANK) {
      return null;
    }

    Object rawValue = extractRawValue(cell);
    if (rawValue == null) {
      return null;
    }

    ConvertContext convertContext = buildConvertContext();
    Object result = converterChain.convert(rawValue, targetType, convertContext);

    // 如果转换器链无法处理，回退到原始值
    if (result == null && targetType == String.class && rawValue instanceof String) {
      return rawValue;
    }

    return result;
  }

  /**
   * 从Cell中提取原始值
   *
   * <p>根据CellType提取对应的Java原始值：
   *
   * <ul>
   *   <li>STRING -> String
   *   <li>NUMERIC(日期) -> java.util.Date
   *   <li>NUMERIC(数值) -> Double
   *   <li>BOOLEAN -> Boolean
   *   <li>FORMULA -> 递归提取缓存结果
   *   <li>ERROR -> Byte(错误码)
   * </ul>
   *
   * @param cell 单元格对象
   * @return 提取的原始值
   */
  private Object extractRawValue(Cell cell) {
    CellType cellType = cell.getCellType();

    if (cellType == CellType.STRING) {
      return cell.getStringCellValue();
    }

    if (cellType == CellType.NUMERIC) {
      if (DateUtil.isCellDateFormatted(cell)) {
        return cell.getDateCellValue();
      }
      return cell.getNumericCellValue();
    }

    if (cellType == CellType.BOOLEAN) {
      return cell.getBooleanCellValue();
    }

    if (cellType == CellType.FORMULA) {
      return extractFormulaValue(cell);
    }

    if (cellType == CellType.ERROR) {
      return cell.getErrorCellValue();
    }

    return null;
  }

  /** 从公式单元格中提取缓存结果值 */
  private Object extractFormulaValue(Cell cell) {
    try {
      double numValue = cell.getNumericCellValue();
      return numValue;
    } catch (IllegalStateException e) {
      try {
        return cell.getStringCellValue();
      } catch (IllegalStateException e2) {
        return cell.getBooleanCellValue();
      }
    }
  }

  /** 构建转换上下文 */
  private ConvertContext buildConvertContext() {
    ExcelConfig config = getExcelConfig();
    return ConvertContext.builder()
        .rowIndex(context.getCurrentRow())
        .columnName(String.valueOf(context.getCurrentColumn()))
        .automaticTrim(config.isAutomaticTrim())
        .strictNumberConversion(config.isStrictNumberConversion())
        .use1904Windowing(config.isUse1904Windowing())
        .build();
  }

  /**
   * 获取单元格值(保持原始类型)
   *
   * @param cell 单元格对象
   * @return 单元格的值
   */
  private Object getCellValue(Cell cell) {
    if (cell == null) {
      return null;
    }

    CellType cellType = cell.getCellType();

    if (cellType == CellType.STRING) {
      String str = cell.getStringCellValue();
      if (getExcelConfig().isAutomaticTrim()) {
        str = str.trim();
      }
      return str;
    }

    if (cellType == CellType.NUMERIC) {
      if (DateUtil.isCellDateFormatted(cell)) {
        return cell.getDateCellValue();
      }
      double numValue = cell.getNumericCellValue();
      if (numValue == (long) numValue && Math.abs(numValue) <= 256) {
        return Long.valueOf((long) numValue);
      }
      return numValue;
    }

    if (cellType == CellType.BOOLEAN) {
      return cell.getBooleanCellValue();
    }

    if (cellType == CellType.FORMULA) {
      try {
        return cell.getNumericCellValue();
      } catch (IllegalStateException e) {
        try {
          return cell.getStringCellValue();
        } catch (IllegalStateException e2) {
          return cell.getBooleanCellValue();
        }
      }
    }

    if (cellType == CellType.BLANK) {
      return null;
    }

    if (cellType == CellType.ERROR) {
      return cell.getErrorCellValue();
    }

    return null;
  }
}
