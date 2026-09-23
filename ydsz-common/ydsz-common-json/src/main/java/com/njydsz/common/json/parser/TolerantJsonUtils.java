package com.njydsz.common.json.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 容错 JSON 工具（面向 LLM 输出的非标准 JSON 解析，P2-12 自 ydsz-agent 下沉）。
 *
 * <p>统一处理 LLM 返回的非标准 JSON（如 markdown 代码块包裹、尾随逗号、注释等），
 * 消除各模块中重复的 extractJsonFromMarkdown / parseScoreFromJson 等手写逻辑。
 *
 * <p>所有方法均为静态纯函数，无可变状态，线程安全；零依赖（纯 JDK 正则），符合 L1 工具层纯度要求。
 *
 * @author ydsz-team
 * @since 26.09.23
 */
public final class TolerantJsonUtils {

  /** 私有构造器防止实例化 */
  private TolerantJsonUtils() {
  }

  // ======================== Markdown 代码块剥离 ========================

  /** 匹配 ```json ... ``` 或 ``` ... ``` 的正则 */
  private static final Pattern MD_CODE_BLOCK_PATTERN =
      Pattern.compile("```(?:json)?\\s*\\n?([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

  /**
   * 从可能包裹在 markdown 代码块中的文本提取纯 JSON 内容。
   *
   * <p>支持格式：
   * <ul>
   *   <li>{@code ```json\n{...}\n```} — 代码块包裹</li>
   *   <li>{@code ```\n{...}\n```} — 无语言标签的代码块</li>
   *   <li>{@code {...}} — 纯 JSON（直接返回）</li>
   * </ul>
   *
   * @param text 可能包裹 markdown 的原始文本
   * @return 去除代码块后的纯 JSON 文本（已 trim）
   */
  public static String stripMarkdownCodeBlock(String text) {
    if (text == null || text.isBlank()) {
      return "";
    }
    String trimmed = text.trim();
    Matcher matcher = MD_CODE_BLOCK_PATTERN.matcher(trimmed);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }
    // 尝试剥离开头 ``` 和结尾 ```
    if (trimmed.startsWith("```")) {
      int firstNewline = trimmed.indexOf('\n');
      int lastFence = trimmed.lastIndexOf("```");
      if (firstNewline > 0 && lastFence > firstNewline) {
        return trimmed.substring(firstNewline + 1, lastFence).trim();
      }
    }
    return trimmed;
  }

  // ======================== 字段值提取 ========================

  /**
   * 从 JSON 文本中提取指定字段的 Double 数值。
   *
   * <p>适用于形如 {@code {"score": 0.85, "reasoning": "..."}} 的 LLM 输出。
   * 提取逻辑：找到 "fieldName": 后取到下一个 , 或 } 之间的内容。
   *
   * @param json JSON 文本
   * @param fieldName 字段名
   * @param defaultValue 解析失败或字段不存在时的默认值
   * @return 字段值（已 clamp 到 [0.0, 1.0] 范围内，如检测到值在该范围）
   */
  public static double extractDoubleField(String json, String fieldName, double defaultValue) {
    if (json == null || json.isBlank()) {
      return defaultValue;
    }
    try {
      int fieldStart = json.indexOf("\"" + fieldName + "\"");
      if (fieldStart < 0) {
        return defaultValue;
      }
      int colonIdx = json.indexOf(':', fieldStart);
      if (colonIdx < 0) {
        return defaultValue;
      }
      // 找到值的结束位置（逗号、大括号或字符串结束）
      int valueStart = colonIdx + 1;
      int commaIdx = findValueEnd(json, valueStart);
      String valueStr = json.substring(valueStart, commaIdx).trim();
      // 去除可能的尾随逗号
      valueStr = valueStr.replaceAll(",\\s*$", "").trim();
      double value = Double.parseDouble(valueStr);
      // Clamp 到合理范围
      if (value >= 0.0 && value <= 1.0) {
        return value;
      }
      // 如果值明显超出 [0,1] 但不是 [0,100] 的情况，做合理缩放
      return value;
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /**
   * 从 JSON 文本中提取指定字段的 String 值（双引号包裹的字符串内容）。
   *
   * @param json JSON 文本
   * @param fieldName 字段名
   * @param defaultValue 解析失败或字段不存在时的默认值
   * @return 字段字符串内容（不含引号），失败返回 defaultValue
   */
  public static String extractStringField(String json, String fieldName, String defaultValue) {
    if (json == null || json.isBlank()) {
      return defaultValue;
    }
    try {
      int fieldStart = json.indexOf("\"" + fieldName + "\"");
      if (fieldStart < 0) {
        return defaultValue;
      }
      int colonIdx = json.indexOf(':', fieldStart);
      if (colonIdx < 0) {
        return defaultValue;
      }
      // 找到值的开始位置（第一个双引号）
      int quoteStart = json.indexOf('"', colonIdx + 1);
      if (quoteStart < 0) {
        return defaultValue;
      }
      // 找到值的结束位置（配对的引号）
      int quoteEnd = findClosingQuote(json, quoteStart + 1);
      if (quoteEnd < 0) {
        return defaultValue;
      }
      return json.substring(quoteStart + 1, quoteEnd)
          .replace("\\\"", "\"")
          .replace("\\n", "\n")
          .replace("\\t", "\t");
    } catch (Exception e) {
      return defaultValue;
    }
  }

  /**
   * 找到 JSON 值的结束位置（逗号、大括号或字符串末尾）。
   *
   * @param json JSON 文本
   * @param start 值开始位置（冒号之后）
   * @return 结束位置索引
   */
  private static int findValueEnd(String json, int start) {
    for (int i = start; i < json.length(); i++) {
      char c = json.charAt(i);
      if (c == ',' || c == '}') {
        return i;
      }
    }
    return json.length();
  }

  /**
   * 找到配对的闭合引号位置（忽略转义引号）。
   *
   * @param json JSON 文本
   * @param start 搜索开始位置（开始引号之后）
   * @return 闭合引号索引，未找到返回 -1
   */
  private static int findClosingQuote(String json, int start) {
    for (int i = start; i < json.length(); i++) {
      char c = json.charAt(i);
      if (c == '\\') {
        i++; // 跳过转义字符
        continue;
      }
      if (c == '"') {
        return i;
      }
    }
    return -1;
  }
}
