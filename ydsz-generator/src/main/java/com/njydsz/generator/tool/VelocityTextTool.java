package com.njydsz.generator.tool;

/**
 * Velocity 模板文本工具类。
 *
 * <p>提供字符串处理辅助方法，在 Velocity 模板中通过 {@code $text} 访问。
 *
 * <p>可用方法：
 * <ul>
 *   <li>{@link #camelCase(String)} — 下划线命名转驼峰</li>
 *   <li>{@link #firstLower(String)} — 首字母小写</li>
 *   <li>{@link #firstUpper(String)} — 首字母大写</li>
 *   <li>{@link #removePrefix(String, String)} — 移除前缀</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.04
 */
public final class VelocityTextTool {

  /** 默认构造方法私有。 */
  private VelocityTextTool() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 将下划线命名转为驼峰命名。
   *
   * @param name 原始字符串（如 {@code user_name}）
   * @return 驼峰字符串（如 {@code userName}），null 输入返回 null
   */
  public static String camelCase(final String name) {
    if (name == null || name.isEmpty()) {
      return name;
    }
    if (!name.contains("_")) {
      return name;
    }
    final String[] parts = name.split("_");
    final StringBuilder builder = new StringBuilder(name.length());
    for (int idx = 0; idx < parts.length; idx++) {
      if (parts[idx].isEmpty()) {
        continue;
      }
      if (idx == 0) {
        builder.append(parts[idx].toLowerCase());
      } else {
        builder.append(Character.toUpperCase(parts[idx].charAt(0)));
        if (parts[idx].length() > 1) {
          builder.append(parts[idx].substring(1).toLowerCase());
        }
      }
    }
    return builder.toString();
  }

  /**
   * 首字母小写。
   *
   * @param str 原始字符串
   * @return 首字母小写的字符串，null 输入返回 null
   */
  public static String firstLower(final String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    return Character.toLowerCase(str.charAt(0)) + str.substring(1);
  }

  /**
   * 首字母大写。
   *
   * @param str 原始字符串
   * @return 首字母大写的字符串，null 输入返回 null
   */
  public static String firstUpper(final String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    return Character.toUpperCase(str.charAt(0)) + str.substring(1);
  }

  /**
   * 移除指定前缀。
   *
   * @param str    原始字符串
   * @param prefix 需移除的前缀
   * @return 移除前缀后的字符串，null 或不含前缀时返回原值
   */
  public static String removePrefix(final String str, final String prefix) {
    if (str == null || prefix == null) {
      return str;
    }
    if (str.startsWith(prefix)) {
      return str.substring(prefix.length());
    }
    return str;
  }
}
