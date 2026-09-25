package com.njydsz.common.excel.core.util;

import java.lang.reflect.Field;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.njydsz.common.excel.annotation.ExcelIgnore;
import com.njydsz.common.excel.annotation.ExcelProperty;

/**
 * 表头列映射器 — 将 Excel 实际表头行与 {@link ExcelProperty} 注解进行匹配，输出字段索引到列索引的映射。
 *
 * <p>解决以下竞品对标场景：
 *
 * <ul>
 *   <li><b>国际化</b>：中英文系统导出的表头可能为 "Name" / "姓名"，需通过 {@link MatchMode#I18N} 匹配</li>
 *   <li><b>大小写</b>：表头 "NAME" / "name" 与字段 {@code value = "Name"} 兼容</li>
 *   <li><b>全半角</b>：全角括号 "（备注）" 与半角 "(备注)" 兼容</li>
 *   <li><b>空白</b>：表头前后空格、中间多余空格不影响匹配</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * // 动态读取路径 — ReadListener 回调中获得表头行（第 0 行）
 * List<String> headers = readRowAsList(ReaderRow row);
 * // 建立字段-列索引映射
 * int[] mapping = HeaderColumnMapper.map(User.class, headers, MatchMode.LENIENT);
 * // mapping[fieldCol] = excelCol (-1 表示未匹配)
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.25
 */
public final class HeaderColumnMapper {

  private HeaderColumnMapper() {}

  /**
   * 表头匹配模式。
   *
   * <p>匹配强度从弱到强：{@link #EXACT} &lt; {@link #CASE_INSENSITIVE} &lt; {@link #WHITESPACE_NORMALIZED} &lt;
   * {@link #LENIENT} &lt; {@link #I18N}。
   */
  public enum MatchMode {
    /** 精确匹配（trim 后 equals） */
    EXACT,
    /** 忽略大小写匹配 */
    CASE_INSENSITIVE,
    /** 空白字符归一化（去除头尾空格、合并中间空白、全半角归一化） */
    WHITESPACE_NORMALIZED,
    /** 容错模式（{@link #WHITESPACE_NORMALIZED} + 忽略标点符号后的纯文本匹配） */
    LENIENT,
    /** 国际化模式（通过 MessageSource 翻译 value，再走 {@link #LENIENT} 匹配，需外部传入翻译后表头） */
    I18N
  }

  /**
   * 将表头行（Excel 列名称列表）与目标类的注解字段进行匹配。
   *
   * @param clazz 目标类（字段需带 @ExcelProperty）
   * @param headers Excel 实际表头（列索引 → 表头文本）
   * @param mode 匹配模式
   * @return 映射数组，{@code result[fieldCol] = excelCol}，未匹配字段为 -1
   */
  public static int[] map(Class<?> clazz, List<String> headers, MatchMode mode) {
    return map(clazz, headers, mode, null);
  }

  /**
   * 将表头行（Excel 列名称列表）与目标类的注解字段进行匹配（带上下文翻译表）。
   *
   * <p>当 mode 为 {@link MatchMode#I18N} 时，translator 不可为 null，用于将 Excel 中的实际表头翻译为可比较的基准语言，
   * 或反向将注解 value 翻译为 Excel 系统中的语言后再与实际表头做 LENIENT 匹配。
   *
   * @param clazz 目标类（字段需带 @ExcelProperty）
   * @param headers Excel 实际表头（列索引 → 表头文本）
   * @param mode 匹配模式
   * @param translator 国际化翻译函数（原始表头 → 基准语言表头），仅 I18N 模式使用
   * @return 映射数组，{@code result[fieldCol] = excelCol}，未匹配字段为 -1
   */
  public static int[] map(Class<?> clazz, List<String> headers, MatchMode mode,
      java.util.function.Function<String, String> translator) {
    if (clazz == null || headers == null || headers.isEmpty() || mode == null) {
      return new int[0];
    }

    List<Field> fields = ColumnOrderResolver.resolveOrderedFields(clazz);
    int[] mapping = new int[fields.size()];
    for (int i = 0; i < mapping.length; i++) {
      mapping[i] = -1;
    }

    // 构建 header 归一化索引（一个 header 只能被匹配一次）
    Map<Integer, String> normalizedHeaders = new HashMap<>(headers.size());
    for (int col = 0; col < headers.size(); col++) {
      String h = headers.get(col);
      if (h != null) {
        normalizedHeaders.put(col, normalize(h, mode));
      }
    }
    boolean[] headerMatched = new boolean[headers.size()];

    for (int fieldCol = 0; fieldCol < fields.size(); fieldCol++) {
      Field field = fields.get(fieldCol);
      ExcelProperty prop = field.getAnnotation(ExcelProperty.class);
      if (prop == null) {
        continue;
      }

      String annotationValue = prop.value().isEmpty() ? field.getName() : prop.value();
      String normalizedAnnotation = normalize(annotationValue, mode);

      // 第一轮：直接匹配 normalized annotation vs normalized header
      for (Map.Entry<Integer, String> entry : normalizedHeaders.entrySet()) {
        int excelCol = entry.getKey();
        if (headerMatched[excelCol]) {
          continue;
        }
        if (match(normalizedAnnotation, entry.getValue(), mode)) {
          mapping[fieldCol] = excelCol;
          headerMatched[excelCol] = true;
          break;
        }
      }

      // I18N 第二轮：通过 translator 翻译成其他语言再试
      if (mapping[fieldCol] == -1 && mode == MatchMode.I18N && translator != null) {
        for (Map.Entry<Integer, String> entry : normalizedHeaders.entrySet()) {
          int excelCol = entry.getKey();
          if (headerMatched[excelCol]) {
            continue;
          }
          String translated = translator.apply(headers.get(excelCol));
          if (translated != null && match(normalizedAnnotation,
              normalize(translated, MatchMode.LENIENT), MatchMode.LENIENT)) {
            mapping[fieldCol] = excelCol;
            headerMatched[excelCol] = true;
            break;
          }
        }
      }
    }

    return mapping;
  }

  /**
   * 将表头行按字段顺序排列为按 Excel 列顺序输出的数组。
   *
   * <p>返回数组长度为 {@code headers.size()}，未匹配的列填充 {@code null}。
   *
   * @param mapping {@link #map(Class, List, MatchMode)} 的输出
   * @param headers 原始表头行
   * @return 按 Excel 列顺序排列的字段名数组（与 Excel 列一一对应）
   */
  public static String[] toColumnOrderArray(int[] mapping, List<String> headers) {
    String[] ordered = new String[headers.size()];
    for (int fieldCol = 0; fieldCol < mapping.length; fieldCol++) {
      int excelCol = mapping[fieldCol];
      if (excelCol >= 0 && excelCol < headers.size()) {
        ordered[excelCol] = headers.get(excelCol);
      }
    }
    return ordered;
  }

  // ==================== 工具方法 ====================

  private static boolean match(String annotation, String header, MatchMode mode) {
    if (annotation == null || header == null) {
      return false;
    }
    switch (mode) {
      case EXACT:
        return annotation.equals(header);
      case CASE_INSENSITIVE:
        return annotation.equalsIgnoreCase(header);
      case WHITESPACE_NORMALIZED:
      case I18N:
        // I18N 模式中传入的是已 LENIENT-normalized 的字符串，按内容相等比较
        return annotation.equals(header);
      case LENIENT:
        return annotation.equals(header);
      default:
        return annotation.equals(header);
    }
  }

  private static String normalize(String text, MatchMode mode) {
    if (text == null) {
      return "";
    }
    switch (mode) {
      case EXACT:
        return text.trim();
      case CASE_INSENSITIVE:
        return text.trim().toLowerCase();
      case WHITESPACE_NORMALIZED:
        return collapseWhitespace(fullWidthToHalfWidth(text.trim())).toLowerCase();
      case LENIENT:
        return stripPunctuation(collapseWhitespace(fullWidthToHalfWidth(text.trim()))).toLowerCase();
      case I18N:
        return stripPunctuation(collapseWhitespace(fullWidthToHalfWidth(text.trim()))).toLowerCase();
      default:
        return text.trim();
    }
  }

  /** 全角 → 半角转换（仅转换 ASCII 范围内的全角字符） */
  private static String fullWidthToHalfWidth(String text) {
    StringBuilder sb = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c >= 0xFF01 && c <= 0xFF5E) {
        // 全角 ASCII 区段 → 半角
        sb.append((char) (c - 0xFEE0));
      } else if (c == 0x3000) {
        // 全角空格 → 半角空格
        sb.append(' ');
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /** 合并连续空白字符为单空格 */
  private static String collapseWhitespace(String text) {
    return text.replaceAll("\\s+", " ");
  }

  /** 去除常见标点符号（中英文括号、冒号、连字符等），用于 LENIENT 模式的纯文本匹配 */
  private static String stripPunctuation(String text) {
    return text.replaceAll("[\\p{Punct}（）【】《》〈〉·・、。，；：？！\\-—_/\\\\|]", "");
  }
}
