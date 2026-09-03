package com.njydsz.common.safe.desensitize;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 列脱敏上下文。
 *
 * <p>按「表 → 列 → 规则」三级结构组织脱敏规则，支持多角色规则合并。 上下文实例可变，非线程安全；多角色合并场景应在单线程内完成构建后共享只读视图。
 *
 * <h3>P2-1: 脱敏体系使用指引</h3>
 *
 * <p>common-safe 提供两套字段级脱敏（JSON 序列化层）和一套列级脱敏（数据层）：
 *
 * <ul>
 *   <li>{@code @Sensitive}：字段级脱敏，<b>推荐</b>，用于 JSON 响应输出
 *   <li>{@code @SensitiveData}：字段级脱敏，仅用于需要角色白名单的场景
 *   <li><b>本上下文 {@code ColumnDesensitizationContext}</b>：<b>列级脱敏</b>， 用于 SQL 查询结果集脱敏（数据层），通过 {@link
 *       ColumnDesensitizationExecutor} 执行。 与字段级注解互不干扰，可同时使用
 * </ul>
 *
 * <p><b>典型用法：</b>
 *
 * <pre>{@code
 * ColumnDesensitizationContext ctx = new ColumnDesensitizationContext();
 * ctx.addRule("sys_user", "phone", ColumnDesensitizationRule.PHONE);
 * ctx.addRule("sys_user", "email", ColumnDesensitizationRule.CUSTOM, "(\\w).*(@.*)", "$1***$2");
 *
 * if (ctx.hasRule("sys_user", "phone")) {
 *     ColumnDesensitizationContext.DesensitizationRuleConfig cfg = ctx.getRule("sys_user", "phone");
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ColumnDesensitizationRule
 * @see ColumnDesensitizationExecutor
 */
public class ColumnDesensitizationContext {

  /** 空上下文单例（共享只读，不应被修改） */
  private static final ColumnDesensitizationContext EMPTY = new ColumnDesensitizationContext();

  /** 表 → (列 → 规则配置) 的二级映射 */
  private final Map<String, Map<String, DesensitizationRuleConfig>> rules = new LinkedHashMap<>(16);

  /**
   * 脱敏规则配置（不可变值对象）。
   *
   * <p>由 {@link ColumnDesensitizationExecutor} 读取，决定如何对列值进行脱敏。
   */
  public static final class DesensitizationRuleConfig {

    private final ColumnDesensitizationRule rule;
    private final String customPattern;
    private final String customReplacement;

    public DesensitizationRuleConfig(ColumnDesensitizationRule rule) {
      this(rule, null, null);
    }

    public DesensitizationRuleConfig(
        ColumnDesensitizationRule rule, String customPattern, String customReplacement) {
      this.rule = rule;
      this.customPattern = customPattern;
      this.customReplacement = customReplacement;
    }

    /** 获取脱敏规则枚举（null 表示仅标记脱敏但无具体规则，使用默认掩码） */
    public ColumnDesensitizationRule getRule() {
      return rule;
    }

    /** 是否为自定义规则 */
    public boolean isCustom() {
      return rule == ColumnDesensitizationRule.CUSTOM;
    }

    /** 获取自定义正则表达式（仅 CUSTOM 规则时有效） */
    public String getCustomPattern() {
      return customPattern;
    }

    /** 获取自定义替换模板（仅 CUSTOM 规则时有效） */
    public String getCustomReplacement() {
      return customReplacement;
    }
  }

  // ==================== 静态工厂 ====================

  /**
   * 获取空上下文共享单例（只读，不可修改）。
   *
   * @return 空上下文单例
   */
  public static ColumnDesensitizationContext empty() {
    return EMPTY;
  }

  // ==================== 规则管理 ====================

  /**
   * 添加列脱敏规则。
   *
   * <p>同一表同一列重复添加时后者覆盖前者。
   *
   * @param tableName 表名
   * @param columnName 列名
   * @param rule 脱敏规则枚举
   */
  public void addRule(String tableName, String columnName, ColumnDesensitizationRule rule) {
    rules.computeIfAbsent(tableName, k -> new LinkedHashMap<>(8))
        .put(columnName, new DesensitizationRuleConfig(rule));
  }

  /**
   * 添加自定义列脱敏规则。
   *
   * @param tableName 表名
   * @param columnName 列名
   * @param rule 脱敏规则枚举（通常为 {@link ColumnDesensitizationRule#CUSTOM}）
   * @param customPattern 自定义正则表达式
   * @param customReplacement 自定义替换模板
   */
  public void addRule(
      String tableName,
      String columnName,
      ColumnDesensitizationRule rule,
      String customPattern,
      String customReplacement) {
    rules.computeIfAbsent(tableName, k -> new LinkedHashMap<>(8))
        .put(columnName, new DesensitizationRuleConfig(rule, customPattern, customReplacement));
  }

  /**
   * 检查指定列是否配置了脱敏规则。
   *
   * @param tableName 表名
   * @param columnName 列名
   * @return 已配置规则返回 true
   */
  public boolean hasRule(String tableName, String columnName) {
    Map<String, DesensitizationRuleConfig> tableRules = rules.get(tableName);
    return tableRules != null && tableRules.containsKey(columnName);
  }

  /**
   * 获取指定列的脱敏规则配置。
   *
   * @param tableName 表名
   * @param columnName 列名
   * @return 规则配置，未配置返回 null
   */
  public DesensitizationRuleConfig getRule(String tableName, String columnName) {
    Map<String, DesensitizationRuleConfig> tableRules = rules.get(tableName);
    if (tableRules == null) {
      return null;
    }
    return tableRules.get(columnName);
  }

  /**
   * 获取全部不可变规则视图。
   *
   * @return 表 → (列 → 规则配置) 的不可变映射
   */
  public Map<String, Map<String, DesensitizationRuleConfig>> getAllRules() {
    Map<String, Map<String, DesensitizationRuleConfig>> result = new LinkedHashMap<>(rules.size());
    for (Map.Entry<String, Map<String, DesensitizationRuleConfig>> entry : rules.entrySet()) {
      result.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
    }
    return Collections.unmodifiableMap(result);
  }

  /**
   * 合并另一个上下文的规则到当前上下文。
   *
   * <p>冲突时以后传入的上下文为准（覆盖策略）。 合并操作非线程安全，调用方需在单线程内完成构建。
   *
   * @param other 另一个脱敏上下文
   * @return 当前上下文自身（链式调用）
   */
  public ColumnDesensitizationContext merge(ColumnDesensitizationContext other) {
    if (other == null || other.isEmpty()) {
      return this;
    }
    for (Map.Entry<String, Map<String, DesensitizationRuleConfig>> tableEntry :
        other.rules.entrySet()) {
      for (Map.Entry<String, DesensitizationRuleConfig> colEntry :
          tableEntry.getValue().entrySet()) {
        rules.computeIfAbsent(tableEntry.getKey(), k -> new LinkedHashMap<>(8))
            .put(colEntry.getKey(), colEntry.getValue());
      }
    }
    return this;
  }

  /**
   * 判断当前上下文是否未配置任何脱敏规则。
   *
   * @return 无任何规则时返回 true
   */
  public boolean isEmpty() {
    return rules.isEmpty();
  }
}
