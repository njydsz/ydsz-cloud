package com.njydsz.common.json.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 类型转换与值解析工具。
 *
 * <p>负责 JSON 值到 Java 目标类型的转换，以及原始 JSON 片段的快速解析。 该类是 {@link BuilderResolver} 和 {@link
 * CreatorResolver} 的底层支撑， 在反射赋值时将解析后的值转换为目标字段的正确类型。
 *
 * <h3>数值类型转换</h3>
 *
 * <p>JSON 解析器可能返回 Integer、Long 或 BigDecimal，但目标字段可能需要 Short 或 Float。 {@link #convertValue(Object,
 * Class)} 统一处理所有 Number 子类之间的转换。
 *
 * <h3>字符串反转义</h3>
 *
 * <p>{@link #unescapeString(String)} 实现完整的 JSON 字符串反转义， 支持 {@code \\"}, {@code \ }, {@code \\uXXXX}
 * 等所有标准转义序列。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see BuilderResolver
 * @see CreatorResolver
 */
@SuppressWarnings("deprecation")
final class TypeConverter {

  private static final Logger LOGGER = LoggerFactory.getLogger(TypeConverter.class);

  private TypeConverter() {
    throw new UnsupportedOperationException();
  }

  /**
   * 将解析后的 JSON 值转换为目标 Java 类型。
   *
   * <p>转换规则：
   *
   * <ul>
   *   <li>值已是目标类型实例 → 原样返回
   *   <li>值为 Number 且目标为数值类型 → 调用对应的 {@code xxxValue()} 转换
   *   <li>目标为 String → 调用 {@link String#valueOf(Object)}
   *   <li>其他 → 原样返回（可能导致 ClassCastException，由上层处理）
   * </ul>
   *
   * @param value 待转换的值
   * @param targetType 目标类型（可为原始类型）
   * @return 转换后的值，输入为 null 时返回 null
   */
  static Object convertValue(Object value, Class<?> targetType) {
    if (value == null) {
      return null;
    }
    if (targetType.isInstance(value)) {
      return value;
    }

    if (value instanceof Number) {
      Number num = (Number) value;
      if (targetType == int.class || targetType == Integer.class) {
        return num.intValue();
      } else if (targetType == long.class || targetType == Long.class) {
        return num.longValue();
      } else if (targetType == double.class || targetType == Double.class) {
        return num.doubleValue();
      } else if (targetType == float.class || targetType == Float.class) {
        return num.floatValue();
      } else if (targetType == short.class || targetType == Short.class) {
        return num.shortValue();
      } else if (targetType == byte.class || targetType == Byte.class) {
        return num.byteValue();
      }
    }

    if (targetType == String.class) {
      return String.valueOf(value);
    }

    // 枚举转换：String → Enum
    if (targetType.isEnum() && value instanceof String) {
      @SuppressWarnings({"rawtypes", "unchecked"})
      Class<? extends Enum> enumType = targetType.asSubclass(Enum.class);
      try {
        return Enum.valueOf(enumType, (String) value);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Unknown enum value for " + enumType.getName());
      }
    }

    return value;
  }

  /**
   * 从 JSON 字符串片段中解析出字符串值。
   *
   * <p>处理规则：
   *
   * <ul>
   *   <li>"null" → 返回 null
   *   <li>被双引号包围 → 去除引号并反转义
   *   <li>其他 → 原样返回
   * </ul>
   *
   * @param json JSON 字符串片段
   * @return 解析后的字符串值
   */
  static String parseStringValue(String json) {
    json = json.trim();
    if (json.equals("null")) {
      return null;
    }
    if (json.length() >= 2 && json.startsWith("\"") && json.endsWith("\"")) {
      String inner = json.substring(1, json.length() - 1);
      return unescapeString(inner);
    }
    return json;
  }

  /**
   * JSON 字符串反转义。
   *
   * <p>支持 RFC 8259 定义的所有转义序列： {@code \\"}, {@code \\\\}, {@code \\/}, {@code \\b}, {@code \\f},
   * {@code \\n}, {@code \\r}, {@code \\t}, {@code \\uXXXX}。
   *
   * <p>性能优化：先快速扫描是否包含反斜杠，无反斜杠时直接返回原字符串。
   *
   * @param str 待反转义的字符串（不含外层引号）
   * @return 反转义后的字符串
   */
  static String unescapeString(String str) {
    int len = str.length();
    boolean hasEscape = false;
    for (int i = 0; i < len; i++) {
      if (str.charAt(i) == '\\' && i + 1 < len) {
        hasEscape = true;
        break;
      }
    }
    if (!hasEscape) {
      return str;
    }

    StringBuilder sb = new StringBuilder(len);
    int i = 0;
    while (i < len) {
      char c = str.charAt(i);
      if (c == '\\' && i + 1 < len) {
        i++;
        char escaped = str.charAt(i);
        switch (escaped) {
          case '"':
            sb.append('"');
            break;
          case '\\':
            sb.append('\\');
            break;
          case '/':
            sb.append('/');
            break;
          case 'b':
            sb.append('\b');
            break;
          case 'f':
            sb.append('\f');
            break;
          case 'n':
            sb.append('\n');
            break;
          case 'r':
            sb.append('\r');
            break;
          case 't':
            sb.append('\t');
            break;
          case 'u':
            if (i + 4 < len) {
              String hex = str.substring(i + 1, i + 5);
              sb.append((char) Integer.parseInt(hex, 16));
              i += 4;
            } else {
              sb.append(escaped);
            }
            break;
          default:
            sb.append(escaped);
            break;
        }
        i++;
      } else {
        sb.append(c);
        i++;
      }
    }
    return sb.toString();
  }

  /**
   * 从 JSON 片段解析 int 值，解析失败返回 0。
   *
   * @param json JSON 字符串片段
   * @return 解析后的 int 值
   */
  static int parseIntValue(String json) {
    try {
      return Integer.parseInt(json.trim());
    } catch (NumberFormatException e) {
      LOGGER.warn("Failed to parse int value: \"{}\"", json, e);
      return 0;
    }
  }

  /**
   * 从 JSON 片段解析 long 值，解析失败返回 0L。
   *
   * @param json JSON 字符串片段
   * @return 解析后的 long 值
   */
  static long parseLongValue(String json) {
    try {
      return Long.parseLong(json.trim());
    } catch (NumberFormatException e) {
      LOGGER.warn("Failed to parse long value: \"{}\"", json, e);
      return 0L;
    }
  }

  /**
   * 从 JSON 片段解析 double 值，解析失败返回 0.0。
   *
   * @param json JSON 字符串片段
   * @return 解析后的 double 值
   */
  static double parseDoubleValue(String json) {
    try {
      return Double.parseDouble(json.trim());
    } catch (NumberFormatException e) {
      LOGGER.warn("Failed to parse double value: \"{}\"", json, e);
      return 0.0;
    }
  }

  /**
   * 从 JSON 片段解析 float 值，解析失败返回 0.0f。
   *
   * @param json JSON 字符串片段
   * @return 解析后的 float 值
   */
  static float parseFloatValue(String json) {
    try {
      return Float.parseFloat(json.trim());
    } catch (NumberFormatException e) {
      LOGGER.warn("Failed to parse float value: \"{}\"", json, e);
      return 0.0f;
    }
  }

  /**
   * 从 JSON 片段解析 boolean 值。
   *
   * <p>仅当字符串（忽略大小性和前后空白）等于 "true" 时返回 true，其他均返回 false。
   *
   * @param json JSON 字符串片段
   * @return 解析后的 boolean 值
   */
  static boolean parseBooleanValue(String json) {
    return "true".equalsIgnoreCase(json.trim());
  }
}
