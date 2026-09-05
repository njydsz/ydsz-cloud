package com.njydsz.generator.domain.tool;

import com.njydsz.common.util.string.StringUtils;

/**
 * Velocity 文本工具对象（模板中通过 {@code $text} 调用）。
 *
 * <p>提供字符串转换辅助方法，用于代码生成模板中的命名转换与格式化操作。
 * 底层实现委托给 {@link com.njydsz.common.util.string.StringUtils}，避免重复造轮子。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
public class VelocityTextTool {

  /**
   * 下划线转驼峰（首字母小写）。
   *
   * @param input 下划线命名字符串（如 user_name）
   * @return 驼峰命名字符串（如 userName）
   */
  public String camelCase(String input) {
    return StringUtils.toCamelCase(input);
  }

  /**
   * 下划线转驼峰（首字母大写）。
   *
   * @param input 下划线命名字符串（如 user_name）
   * @return PascalCase 字符串（如 UserName）
   */
  public String pascalCase(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }
    String camel = StringUtils.toCamelCase(input);
    return camel.isEmpty() ? camel : Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
  }

  /**
   * 首字母小写。
   *
   * @param input 输入字符串
   * @return 首字母小写字符串
   */
  public String uncapitalize(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }
    return Character.toLowerCase(input.charAt(0)) + input.substring(1);
  }

  /**
   * 首字母大写。
   *
   * @param input 输入字符串
   * @return 首字母大写字符串
   */
  public String capitalize(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }
    return Character.toUpperCase(input.charAt(0)) + input.substring(1);
  }

  /**
   * 驼峰转下划线。
   *
   * @param input 驼峰命名字符串（如 userName）
   * @return 下划线命名字符串（如 user_name）
   */
  public String snakeCase(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }
    StringBuilder sb = new StringBuilder();
    for (char c : input.toCharArray()) {
      if (Character.isUpperCase(c)) {
        sb.append('_').append(Character.toLowerCase(c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /**
   * 缩写：取每个单词首字母小写。
   *
   * @param input 下划线命名字符串
   * @return 缩写字符串
   */
  public String abbreviate(String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }
    StringBuilder sb = new StringBuilder();
    for (String part : input.split("_")) {
      if (!part.isEmpty()) {
        sb.append(Character.toLowerCase(part.charAt(0)));
      }
    }
    return sb.toString();
  }

  /**
   * 字符串重复。
   *
   * @param input  输入字符串
   * @param times 重复次数
   * @return 重复后的字符串
   */
  public String repeat(String input, int times) {
    if (input == null || times <= 0) {
      return "";
    }
    StringBuilder sb = new StringBuilder(input.length() * times);
    for (int i = 0; i < times; i++) {
      sb.append(input);
    }
    return sb.toString();
  }

  /**
   * 移除前缀。
   *
   * @param input  输入字符串
   * @param prefix 要移除的前缀
   * @return 移除前缀后的字符串
   */
  public String removePrefix(String input, String prefix) {
    return StringUtils.removeStart(input, prefix);
  }

  /**
   * 移除后缀。
   *
   * @param input  输入字符串
   * @param suffix 要移除的后缀
   * @return 移除后缀后的字符串
   */
  public String removeSuffix(String input, String suffix) {
    if (input == null || suffix == null) {
      return input;
    }
    return input.endsWith(suffix) ? input.substring(0, input.length() - suffix.length()) : input;
  }

  /**
   * Java 基本类型转包装类型。
   *
   * @param primitive 基本类型名
   * @return 包装类型名
   */
  public String wrapType(String primitive) {
    if (primitive == null) {
      return null;
    }
    switch (primitive) {
      case "int": return "Integer";
      case "long": return "Long";
      case "short": return "Short";
      case "byte": return "Byte";
      case "float": return "Float";
      case "double": return "Double";
      case "char": return "Character";
      case "boolean": return "Boolean";
      case "void": return "Void";
      default: return primitive;
    }
  }
}
