package com.njydsz.common.safe.desensitize;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 列脱敏执行器。
 *
 * <p>线程安全的单例执行器，负责根据 {@link ColumnDesensitizationContext.DesensitizationRuleConfig} 对字符串值执行脱敏处理。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li>缓存编译后的正则表达式，避免重复编译开销
 *   <li>支持内置规则（{@link ColumnDesensitizationRule}）与自定义规则（CUSTOM）
 *   <li>对 null/空值、null 规则等边界场景进行兜底处理
 * </ul>
 *
 * <p><b>典型用法：</b>
 *
 * <pre>{@code
 * ColumnDesensitizationContext ctx = ...;
 * ColumnDesensitizationContext.DesensitizationRuleConfig config = ctx.getRule("sys_user", "phone");
 * String masked = ColumnDesensitizationExecutor.getInstance().desensitize("13812345678", config);
 * // masked = "138****5678"
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ColumnDesensitizationRule
 * @see ColumnDesensitizationContext
 */
public final class ColumnDesensitizationExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(ColumnDesensitizationExecutor.class);

  /** 默认脱敏占位符（规则为 null 或正则不匹配时使用） */
  private static final String DEFAULT_MASK = "***";

  private static final ColumnDesensitizationExecutor INSTANCE = new ColumnDesensitizationExecutor();

  /** 正则表达式编译缓存：pattern 字符串 → 编译后的 Pattern（线程安全） */
  private final ConcurrentHashMap<String, Pattern> patternCache = new ConcurrentHashMap<>();

  private ColumnDesensitizationExecutor() {}

  /**
   * 获取执行器单例。
   *
   * @return 执行器实例
   */
  public static ColumnDesensitizationExecutor getInstance() {
    return INSTANCE;
  }

  /**
   * 对字符串值执行脱敏处理。
   *
   * <p>处理规则：
   *
   * <ul>
   *   <li>config 为 null 或 value 为 null/空 → 原样返回
   *   <li>规则为 null（仅标记需脱敏） → 返回 {@link #DEFAULT_MASK}
   *   <li>规则为 CUSTOM 且自定义正则为空 → 返回 {@link #DEFAULT_MASK}
   *   <li>其余情况按规则正则替换；正则编译/替换异常时返回 {@link #DEFAULT_MASK}
   * </ul>
   *
   * @param value 待脱敏的原始值
   * @param config 脱敏规则配置
   * @return 脱敏后的字符串；value 为 null 时返回 null
   */
  public String desensitize(
      String value, ColumnDesensitizationContext.DesensitizationRuleConfig config) {
    if (value == null || value.isEmpty() || config == null) {
      return value;
    }

    ColumnDesensitizationRule rule = config.getRule();
    String pattern;
    String replacement;

    if (config.isCustom()) {
      pattern = config.getCustomPattern();
      replacement = config.getCustomReplacement();
    } else if (rule != null) {
      pattern = rule.getPattern();
      replacement = rule.getReplacement();
    } else {
      // rule 为 null：列被标记为需要脱敏但未指定规则，使用默认占位
      return DEFAULT_MASK;
    }

    if (pattern == null || pattern.isEmpty()) {
      return DEFAULT_MASK;
    }
    if (replacement == null) {
      replacement = "";
    }

    try {
      Pattern compiled = patternCache.computeIfAbsent(pattern, Pattern::compile);
      return compiled.matcher(value).replaceAll(replacement);
    } catch (Exception e) {
      LOG.warn("脱敏正则处理失败：pattern={}, value={}, error={}", pattern, value, e.getMessage());
      return DEFAULT_MASK;
    }
  }
}
