package com.njydsz.common.excel.core.reader;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;

import com.njydsz.common.excel.annotation.ExcelIgnore;
import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.metadata.ReadMetadata;
import com.njydsz.common.excel.support.asm.ASMFieldAccessor.FieldSetter;
import com.njydsz.common.excel.support.cache.ReflectCache;

/**
 * 表头分析器 - 负责解析表头行并建立列与字段的映射关系
 *
 * <p>从ExcelReader中提取的职责：
 *
 * <ul>
 *   <li>解析类元数据，建立列索引与字段的映射
 *   <li>解析无类型映射模式下的表头
 *   <li>表头名称匹配（支持自动trim）
 * </ul>
 *
 * @author ydsz-team

 * @version 26.09.01
 * @see ExcelReader
 * @since 26.09.01
 */
public class HeaderAnalyzer {

  /** 读取配置元数据 */
  private final ReadMetadata metadata;

  /**
   * 构造表头分析器
   *
   * @param metadata 读取配置元数据
   */
  public HeaderAnalyzer(ReadMetadata metadata) {
    this.metadata = metadata;
  }

  private ExcelConfig getExcelConfig() {
    return metadata.getExcelConfig() != null ? metadata.getExcelConfig() : ExcelConfig.defaults();
  }

  /**
   * 分析类元数据,建立列与字段的映射
   *
   * <p>映射规则(按优先级):
   *
   * <ol>
   *   <li>被@ExcelIgnore标记的字段会被忽略
   *   <li>优先使用index指定列索引
   *   <li>其次使用@ExcelProperty的value作为列名
   *   <li>最后使用字段名作为列名
   *   <li>列名匹配采用精确匹配策略
   * </ol>
   *
   * @param headRow 表头行
   * @param headers 表头名称列表(出参)
   * @param fieldMap 列索引到字段的映射(出参)
   * @return 列元数据数组
   */
  public ColumnMetadata[] analyzeClassMetadata(
      Row headRow, List<String> headers, Map<Integer, Field> fieldMap) {
    Class<?> clazz = metadata.getClazz();
    Field[] fields = ReflectCache.getCachedFields(clazz);

    int maxCol = headRow.getLastCellNum();
    for (int col = 0; col < maxCol; col++) {
      Cell cell = headRow.getCell(col);
      String headerName = getCellValueAsString(cell);
      headers.add(headerName);
    }

    Set<String> excludeFields = metadata.getExcludeColumnFiledNames();
    Set<String> includeFields = metadata.getIncludeColumnFiledNames();

    Map<Integer, String> dateFormats = new HashMap<>();
    int columnCount = 0;
    for (Field field : fields) {
      if (field.isAnnotationPresent(ExcelIgnore.class)) {
        continue;
      }

      ExcelProperty annotation = field.getAnnotation(ExcelProperty.class);
      if (annotation == null) {
        continue;
      }

      String fieldName = !annotation.value().isEmpty() ? annotation.value() : field.getName();

      if (excludeFields != null && excludeFields.contains(fieldName)) {
        continue;
      }
      if (includeFields != null && !includeFields.isEmpty() && !includeFields.contains(fieldName)) {
        continue;
      }

      int fieldIndex = annotation.index();

      int targetCol;
      if (fieldIndex >= 0) {
        targetCol = fieldIndex;
      } else {
        targetCol = -1;
        for (int col = 0; col < maxCol; col++) {
          if (headerNameEquals(headers.get(col), fieldName)) {
            targetCol = col;
            break;
          }
        }
      }

      if (targetCol >= 0 && targetCol < maxCol) {
        field.setAccessible(true);
        fieldMap.put(targetCol, field);

        String dateFormat =
            !annotation.dateFormat().isEmpty()
                ? annotation.dateFormat()
                : getExcelConfig().getDefaultDateFormat();
        dateFormats.put(targetCol, dateFormat);
        columnCount++;
      }
    }

    ColumnMetadata[] columnMetadataArray = new ColumnMetadata[columnCount];
    int idx = 0;
    boolean automaticTrim = getExcelConfig().isAutomaticTrim();
    for (Map.Entry<Integer, Field> entry : fieldMap.entrySet()) {
      int col = entry.getKey();
      Field field = entry.getValue();
      FieldSetter setter = ReflectCache.getFieldSetter(clazz, field);
      Class<?> targetType = field.getType();
      String dateFormat = dateFormats.get(col);
      columnMetadataArray[idx++] =
          new ColumnMetadata(col, setter, targetType, dateFormat, automaticTrim);
    }

    return columnMetadataArray;
  }

  /**
   * 基于表头列名映射分析类元数据（fast 读取路径，无 POI Row 依赖）
   *
   * <p>P0-2 修复配套方法。映射规则与 {@link #analyzeClassMetadata(Row, List, Map)} 完全一致：
   *
   * <ol>
   *   <li>被@ExcelIgnore标记的字段会被忽略
   *   <li>优先使用index指定列索引
   *   <li>其次使用@ExcelProperty的value作为列名
   *   <li>最后使用字段名作为列名
   *   <li>列名匹配采用精确匹配策略（automaticTrim 时先 trim）
   * </ol>
   *
   * @param headerNames 0-based 列索引 → 表头列名（由 fast 引擎流式收集，含 SST 解析）
   * @param fieldMap 列索引到字段的映射(出参)
   * @return 列元数据数组
   */
  public ColumnMetadata[] analyzeClassMetadataFromNames(
      Map<Integer, String> headerNames, Map<Integer, Field> fieldMap) {
    Class<?> clazz = metadata.getClazz();
    Field[] fields = ReflectCache.getCachedFields(clazz);

    int maxCol = 0;
    for (int col : headerNames.keySet()) {
      if (col + 1 > maxCol) {
        maxCol = col + 1;
      }
    }

    Set<String> excludeFields = metadata.getExcludeColumnFiledNames();
    Set<String> includeFields = metadata.getIncludeColumnFiledNames();

    Map<Integer, String> dateFormats = new HashMap<>();
    int columnCount = 0;
    for (Field field : fields) {
      if (field.isAnnotationPresent(ExcelIgnore.class)) {
        continue;
      }

      ExcelProperty annotation = field.getAnnotation(ExcelProperty.class);
      if (annotation == null) {
        continue;
      }

      String fieldName = !annotation.value().isEmpty() ? annotation.value() : field.getName();

      if (excludeFields != null && excludeFields.contains(fieldName)) {
        continue;
      }
      if (includeFields != null && !includeFields.isEmpty() && !includeFields.contains(fieldName)) {
        continue;
      }

      int fieldIndex = annotation.index();

      int targetCol;
      if (fieldIndex >= 0) {
        targetCol = fieldIndex;
      } else {
        targetCol = -1;
        for (int col = 0; col < maxCol; col++) {
          if (headerNameEquals(headerNames.get(col), fieldName)) {
            targetCol = col;
            break;
          }
        }
      }

      if (targetCol >= 0 && targetCol < maxCol) {
        field.setAccessible(true);
        fieldMap.put(targetCol, field);

        String dateFormat =
            !annotation.dateFormat().isEmpty()
                ? annotation.dateFormat()
                : getExcelConfig().getDefaultDateFormat();
        dateFormats.put(targetCol, dateFormat);
        columnCount++;
      }
    }

    ColumnMetadata[] columnMetadataArray = new ColumnMetadata[columnCount];
    int idx = 0;
    boolean automaticTrim = getExcelConfig().isAutomaticTrim();
    for (Map.Entry<Integer, Field> entry : fieldMap.entrySet()) {
      int col = entry.getKey();
      Field field = entry.getValue();
      FieldSetter setter = ReflectCache.getFieldSetter(clazz, field);
      Class<?> targetType = field.getType();
      String dateFormat = dateFormats.get(col);
      columnMetadataArray[idx++] =
          new ColumnMetadata(col, setter, targetType, dateFormat, automaticTrim);
    }

    return columnMetadataArray;
  }

  /**
   * 分析表头(无类型映射模式)
   *
   * <p>当未指定Class时,直接读取表头名称作为Map的key。 每列的名称取自表头单元格的字符串值。
   *
   * @param headRow 表头行
   * @param headers 表头名称列表(出参)
   */
  public void analyzeHeaders(Row headRow, List<String> headers) {
    int maxCol = headRow.getLastCellNum();
    for (int col = 0; col < maxCol; col++) {
      Cell cell = headRow.getCell(col);
      String headerName = getCellValueAsString(cell);
      headers.add(headerName);
    }
  }

  /**
   * 表头名称匹配
   *
   * <p>支持精确匹配和自动trim后的匹配。 当ExcelConfig配置了automaticTrim时，会先trim再比较。
   *
   * @param header 表头名称
   * @param fieldName 字段名称
   * @return 是否匹配
   */
  private boolean headerNameEquals(String header, String fieldName) {
    if (header == null || fieldName == null) {
      return false;
    }
    if (header.equals(fieldName)) {
      return true;
    }
    if (getExcelConfig().isAutomaticTrim()) {
      return header.trim().equals(fieldName.trim());
    }
    return false;
  }

  /**
   * 获取单元格字符串值
   *
   * <p>用于表头解析,需要获取单元格的原始字符串表示。
   *
   * @param cell 单元格对象
   * @return 单元格的字符串值
   */
  private String getCellValueAsString(Cell cell) {
    if (cell == null) {
      return "";
    }

    switch (cell.getCellType()) {
      case STRING:
        return cell.getStringCellValue();
      case NUMERIC:
        if (DateUtil.isCellDateFormatted(cell)) {
          return cell.getDateCellValue().toString();
        }
        return String.valueOf(cell.getNumericCellValue());
      case BOOLEAN:
        return String.valueOf(cell.getBooleanCellValue());
      case FORMULA:
        return cell.getCellFormula();
      default:
        return "";
    }
  }
}
