package com.njydsz.common.excel.core.reader;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.njydsz.common.excel.annotation.ExcelIgnore;
import com.njydsz.common.excel.annotation.ExcelProperty;
import com.njydsz.common.excel.core.config.ExcelConfig;
import com.njydsz.common.excel.core.metadata.ReadMetadata;
import com.njydsz.common.excel.support.mh.MHFieldAccessor;
import com.njydsz.common.excel.support.mh.MHFieldAccessor.FieldSetter;
import com.njydsz.common.excel.support.cache.ReflectCache;

/**
 * 表头分析器 — 负责解析表头并建立列与字段的映射关系（零 POI 依赖）。
 *
 * <p>从 ExcelReader 中提取的职责：
 *
 * <ul>
 *   <li>解析类元数据，建立列索引与字段的映射
 *   <li>解析无类型映射模式下的表头
 *   <li>表头名称匹配（支持自动 trim）
 * </ul>
 *
 * <p>自 v26.10.01 起，{@code analyzeClassMetadataFromNames(Map, Map)} 是唯一的活跃调用路径，
 * 接收由 SuperFast 引擎流式收集的表头列名（已含 SST 解析），无 POI API 依赖。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @version 26.10.01
 * @see ExcelReader
 */
public class HeaderAnalyzer {

  /** 读取配置元数据 */
  private final ReadMetadata metadata;

  /**
   * 构造表头分析器。
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
   * 基于表头列名映射分析类元数据（fast 读取路径，零 POI 依赖）。
   *
   * <p>由 {@code SuperFastExcelReader} 在首个数据单元格到达时惰性调用（通过 {@code
   * metadataFactory} 回调），此时表头行已完整收集。映射规则：
   *
   * <ol>
   *   <li>被 @ExcelIgnore 标记的字段忽略</li>
   *   <li>优先使用 @ExcelProperty 的 index 指定列索引</li>
   *   <li>其次使用 @ExcelProperty 的 value 作为列名</li>
   *   <li>最后使用字段名作为列名</li>
   *   <li>列名匹配采用精确匹配策略（automaticTrim 时先 trim）</li>
   * </ol>
   *
   * <p>表头列名由 fast 引擎流式收集（含 {@link
   * com.njydsz.common.excel.core.reader.sax.SharedStringsReader} 解析），列名已是最终字符串。
   *
   * @param headerNames 0-based 列索引 → 表头列名（由 fast 引擎流式收集）
   * @param fieldMap 列索引到字段的映射（出参）
   * @return 列元数据数组
   */
  public ColumnMetadata[] analyzeClassMetadataFromNames(
      Map<Integer, String> headerNames, Map<Integer, Field> fieldMap) {
    Class<?> clazz = metadata.getClazz();
    if (clazz == null) {
      return new ColumnMetadata[0];
    }
    Field[] fields = ReflectCache.getCachedFields(clazz);

    int maxCol = 0;
    for (int col : headerNames.keySet()) {
      if (col + 1 > maxCol) {
        maxCol = col + 1;
      }
    }

    Set<String> excludeFields = metadata.getExcludeColumnFiledNames();
    Set<String> includeFields = metadata.getIncludeColumnFiledNames();

    Map<Integer, String> dateFormats = new java.util.HashMap<>(16);
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
    boolean isAutomaticTrim = getExcelConfig().getIsAutomaticTrim();
    for (Map.Entry<Integer, Field> entry : fieldMap.entrySet()) {
      int col = entry.getKey();
      Field field = entry.getValue();
      FieldSetter setter = ReflectCache.getFieldSetter(clazz, field);
      // P0-VarHandle：预创建 VarHandle Setter 以加速读取路径字段赋值
      java.lang.invoke.VarHandle vh = MHFieldAccessor.getVarHandleSetter(clazz, field);
      Class<?> targetType = field.getType();
      String dateFormat = dateFormats.get(col);
      columnMetadataArray[idx++] =
          new ColumnMetadata(col, setter, vh, targetType, dateFormat, isAutomaticTrim);
    }

    return columnMetadataArray;
  }

  /**
   * 表头名称匹配。
   *
   * <p>支持精确匹配和自动 trim 后的匹配。当 ExcelConfig 配置了 automaticTrim 时，会先 trim 再比较。
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
    if (getExcelConfig().getIsAutomaticTrim()) {
      return header.trim().equals(fieldName.trim());
    }
    return false;
  }
}
