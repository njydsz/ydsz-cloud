package com.njydsz.agent.infra.llm;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.parser.TolerantJsonUtils;

/**
 * LLM 结果解析工具类（P3-H），统一封装大语言模型输出的解析/容错逻辑。
 *
 * <p>LLM 输出存在三大典型噪声场景：
 *
 * <ul>
 *   <li><b>Markdown 包裹</b>：模型用 {@code ```json ... ```} 包裹 JSON 块
 *   <li><b>类型不一致</b>：数字字段偶发输出为字符串（{@code "score": "0.85"}）
 *   <li><b>字段缺失/扩展</b>：模型自创字段或遗漏必须字段
 * </ul>
 *
 * <p>本工具类所有方法均为静态纯函数，无副作用、无状态、线程安全。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * String content = llmResponse.getContent();
 * String json = LlmResultParser.stripJson(content);
 * double score = LlmResultParser.extractDouble(json, "score", 0.0);
 * String text = LlmResultParser.extractString(json, "text", "");
 * MyPojo pojo = LlmResultParser.parseSafely(content, MyPojo.class);
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public final class LlmResultParser {

  private LlmResultParser() {}

  /**
   * 剥离 LLM 输出外层 Markdown 代码块，返回纯净 JSON 内容。
   *
   * <p>等价于 {@link TolerantJsonUtils#stripMarkdownCodeBlock(String)}，作为统一入口提供业务模块命名一致性。
   *
   * @param llmOutput 模型原始输出（可能包裹在 Markdown 代码块中）
   * @return 剥离后的 JSON 字符串
   */
  public static String stripJson(Object llmOutput) {
    if (llmOutput == null) {
      return "";
    }
    return TolerantJsonUtils.stripMarkdownCodeBlock(llmOutput.toString());
  }

  /**
   * 安全地从 LLM 输出字符串中提取 double 字段，解析失败时返回默认值。
   *
   * <p>容错规则：字段缺失 → {@code defaultValue}；值为字符串数字（{@code "0.85"}）→ {@code 0.85}； 非数字 →
   * {@code defaultValue}。
   *
   * @param json LLM 输出 JSON 字符串（推荐先调用 {@link #stripJson} 剥离 Markdown）
   * @param field 字段名称
   * @param defaultValue 默认值
   * @return double 字段值
   */
  public static double extractDouble(String json, String field, double defaultValue) {
    return TolerantJsonUtils.extractDoubleField(json, field, defaultValue);
  }

  /**
   * 安全地从 LLM 输出字符串中提取 String 字段，字段缺失时返回默认值。
   *
   * @param json LLM 输出 JSON 字符串
   * @param field 字段名称
   * @param defaultValue 默认值
   * @return String 字段值
   */
  public static String extractString(String json, String field, String defaultValue) {
    return TolerantJsonUtils.extractStringField(json, field, defaultValue);
  }

  /**
   * 安全地将 LLM 输出反序列化为强类型 POJO，任何异常均返回 null（调用方需自备降级逻辑）。
   *
   * <p>内部处理链路：{@code stripMarkdownCodeBlock} → {@code YdszJson.fromJson}。
   *
   * @param llmOutput 模型原始输出
   * @param clazz 目标 POJO 类型
   * @param <T> 目标类型泛型
   * @return 反序列化后的 POJO；解析失败时返回 null
   */
  public static <T> T parseSafely(Object llmOutput, Class<T> clazz) {
    if (llmOutput == null) {
      return null;
    }
    String json = stripJson(llmOutput);
    if (json == null || json.isEmpty()) {
      return null;
    }
    try {
      return YdszJson.fromJson(json, clazz);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * 安全地将 LLM 输出反序列化为强类型 POJO，任何异常均返回指定的 fallback 实例（非 null 安全）。
   *
   * @param llmOutput 模型原始输出
   * @param clazz 目标 POJO 类型
   * @param fallback 解析失败时返回的降级实例（非 null）
   * @param <T> 目标类型泛型
   * @return 反序列化后的 POJO；解析失败时返回 {@code fallback}
   */
  public static <T> T parseOrElse(Object llmOutput, Class<T> clazz, T fallback) {
    T result = parseSafely(llmOutput, clazz);
    return result != null ? result : fallback;
  }
}
