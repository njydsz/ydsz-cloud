package com.njydsz.system.domain.enums;

import java.util.regex.Pattern;

import com.njydsz.common.util.message.MessageUtils;

/**
 * 配置值类型枚举
 *
 * <p>用于系统配置（{@code ydsz_sys_config}）和系统变量（{@code ydsz_sys_variable}）的值类型校验。 与 DDL CHECK 约束对齐：{@code CHECK
 * (value_type IN ('STRING','NUMBER','BOOLEAN','JSON'))}。
 *
 * <p><b>类型说明：</b>
 *
 * <ul>
 *   <li>{@link #STRING} — 字符串类型，原样存储 / 原样输出
 *   <li>{@link #NUMBER} — 数值类型（{@code Integer / Long / BigDecimal}），写入时序列化为字符串，读取时反序列化
 *   <li>{@link #BOOLEAN} — 布尔类型，存储为 {@code "true" / "false"} 字符串
 *   <li>{@link #JSON} — JSON 对象 / 数组类型，写入时 {@code YdszJson.toJson}，读取时 {@code YdszJson.fromJson}
 * </ul>
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>配置 / 变量写入前的 {@link #validate(String)} 校验
 *   <li>配置值格式校验 {@link #validateFormat(String, String)}（P1-5 收敛：统一由本枚举承载值格式规则）
 *   <li>读取时按 {@code valueType} 字段动态解析 {@code configValue} / {@code variableValue}（{@link #parseValue}）
 *   <li>前端「公开配置」接口返回时附带 {@code valueType} 提示前端按类型解析
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum ConfigValueType {

  /** 字符串类型，原样存储 / 原样输出 */
  STRING,

  /** 数值类型（{@code Integer / Long / BigDecimal}） */
  NUMBER,

  /** 布尔类型，存储为 {@code "true" / "false"} 字符串 */
  BOOLEAN,

  /** JSON 对象 / 数组类型 */
  /** JSON */
  JSON;

  /** 字符串类型最大长度 */
  private static final int MAX_STRING_LENGTH = 4096;

  /** JSON 类型最大长度 */
  private static final int MAX_JSON_LENGTH = 65536;

  /** 数值类型最小值 */
  private static final double MIN_NUMBER = -1e15;

  /** 数值类型最大值 */
  private static final double MAX_NUMBER = 1e15;

  /** 布尔值合法取值模式（不区分大小写） */
  private static final Pattern BOOLEAN_PATTERN =
      Pattern.compile("^(true|false|TRUE|FALSE|True|False)$");

  /**
   * 校验值类型字符串是否合法（不区分大小写）
   *
   * <p>用于配置 / 变量写入前的合法性校验，避免脏数据落库导致后续解析失败。
   *
   * @param code 值类型字符串（{@code "STRING" / "Number" / "boolean" / "json"} 等均可，自动 {@code toUpperCase}）
   * @throws IllegalArgumentException 如果值为空或不在合法枚举范围内
   */
  public static void validate(String code) {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("值类型不能为空");
    }
    try {
      ConfigValueType.valueOf(code.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("无效的值类型: " + code + "，支持: STRING/NUMBER/BOOLEAN/JSON");
    }
  }

  /**
   * 校验配置值格式是否与声明类型匹配（值格式规则的唯一权威实现，P1-5 收敛）。
   *
   * <p>校验规则：
   *
   * <ul>
   *   <li>STRING — 长度 ≤ 4096
   *   <li>NUMBER — 可解析为数值且在 [-1e15, 1e15] 范围内
   *   <li>BOOLEAN — 必须为 true/false（不区分大小写）
   *   <li>JSON — 必须为合法 JSON 且长度 ≤ 65536
   * </ul>
   *
   * @param valueType 值类型字符串（null / 空则跳过校验）
   * @param value 配置值字符串（null 则跳过校验）
   * @return 错误描述，null 表示通过
   */
  public static String validateFormat(String valueType, String value) {
    if (valueType == null || valueType.isBlank() || value == null) {
      return null;
    }
    ConfigValueType type;
    try {
      type = ConfigValueType.valueOf(valueType.toUpperCase());
    } catch (IllegalArgumentException e) {
      return MessageUtils.getMessage("system.excel.unknownValueType",
          new Object[] {valueType}, "未知的值类型: " + valueType);
    }
    return type.validateValue(value);
  }

  /**
   * 校验单个值是否符合当前类型（实例方法）。
   *
   * @param value 待校验的值（非空）
   * @return 错误描述，null 表示通过
   */
  private String validateValue(String value) {
    return switch (this) {
      case STRING -> value.length() > MAX_STRING_LENGTH
          ? MessageUtils.getMessage("system.excel.stringTooLong",
              new Object[] {MAX_STRING_LENGTH}, "字符串长度超过限制 " + MAX_STRING_LENGTH)
          : null;
      case NUMBER -> validateNumber(value);
      case BOOLEAN -> BOOLEAN_PATTERN.matcher(value.trim()).matches()
          ? null
          : MessageUtils.getMessage("system.excel.booleanInvalid", "布尔值必须是 true/false");
      case JSON -> validateJson(value);
    };
  }

  /** 校验 NUMBER 类型可解析性与范围。 */
  private static String validateNumber(String value) {
    double v;
    try {
      v = Double.parseDouble(value.trim());
    } catch (NumberFormatException e) {
      return MessageUtils.getMessage("system.excel.numberFormat.invalid", "数值格式非法");
    }
    if (v < MIN_NUMBER || v > MAX_NUMBER) {
      return MessageUtils.getMessage("system.excel.numberOutOfRange",
          new Object[] {MIN_NUMBER, MAX_NUMBER}, "数值超出范围 [" + MIN_NUMBER + ", " + MAX_NUMBER + "]");
    }
    return null;
  }

  /**
   * 校验 JSON 类型合法性与长度。
   *
   * <p>采用轻量级括号匹配校验（不依赖具体 JSON 库，避免 domain 层与 common-json 耦合）。完整 JSON 语法校验由 server
   * 层在写入前通过 {@code YdszJson} 完成。
   */
  private static String validateJson(String value) {
    if (value.length() > MAX_JSON_LENGTH) {
      return "JSON 长度超过限制 " + MAX_JSON_LENGTH;
    }
    String trimmed = value.trim();
    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
      return isBalanced(trimmed, '{', '}') ? null : "JSON 花括号不匹配";
    }
    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
      return isBalanced(trimmed, '[', ']') ? null : "JSON 方括号不匹配";
    }
    return "JSON 类型值必须以 '{' 或 '[' 开头";
  }

  /**
   * 校验括号是否平衡（轻量级 JSON 结构校验）。
   *
   * @param str 待校验字符串
   * @param open 开括号
   * @param close 闭括号
   * @return 平衡返回 true
   */
  private static boolean isBalanced(String str, char open, char close) {
    int depth = 0;
    boolean inString = false;
    boolean escape = false;
    for (char c : str.toCharArray()) {
      if (escape) {
        escape = false;
        continue;
      }
      if (c == '\\' && inString) {
        escape = true;
        continue;
      }
      if (c == '"') {
        inString = !inString;
        continue;
      }
      if (inString) {
        continue;
      }
      if (c == open) {
        depth++;
      } else if (c == close) {
        depth--;
        if (depth < 0) {
          return false;
        }
      }
    }
    return depth == 0;
  }

  /**
   * 根据值类型将字符串值解析为对应 Java 类型。
   *
   * <p>转换规则：
   *
   * <ul>
   *   <li>{@code STRING} → {@link String} 原样返回
   *   <li>{@code NUMBER} → {@link Double}
   *   <li>{@code BOOLEAN} → {@link Boolean}
   *   <li>{@code JSON} → {@link String} 原样返回（调用方按需反序列化）
   *   <li>{@code null} 或空 → 抛出 {@link IllegalArgumentException}
   * </ul>
   *
   * @param type 值类型字符串
   * @param value 字符串形式的值
   * @return 转换后的 Java 对象
   * @throws IllegalArgumentException 类型不合法或解析失败时抛出
   */
  public static Object parseValue(String type, String value) {
    if (type == null || type.isBlank()) {
      return value;
    }
    ConfigValueType valueType = ConfigValueType.valueOf(type.toUpperCase());
    if (value == null) {
      return null;
    }
    return switch (valueType) {
      case STRING -> value;
      case NUMBER -> Double.valueOf(value);
      case BOOLEAN -> Boolean.valueOf(value);
      case JSON -> value;
    };
  }
}
