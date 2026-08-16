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
 * @since 1.0.0
 * @see ColumnDesensitizationRule
 * @see ColumnDesensitizationExecutor
 */
public class ColumnDesensitizationContext {

  /** 空上下文单例（共享只读，不应被修改） */
  private static final ColumnDesensitizationContext EMPTY = new ColumnDesensitizationContext();

  /** 表 → (列 → 规则配置) 的二级映射 */
  private final Map<String, Map<String, DesensitizationRuleConfig>> rules = new LinkedHashMap<>();

  /**
   * 获取空上下文单例。
   *
   * @return 空上下文实例
   */
  public static ColumnDesensitizationContext empty() {
    return EMPTY;
  }

  /**
   * 添加内置脱敏规则。
   *
   * @param table 表名
   * @param column 列名
   * @param rule 脱敏规则，null 表示该列标记为需要脱敏但使用默认策略
   */
  public void addRule(String table, String column, ColumnDesensitizationRule rule) {
    if (table == null || column == null) {
      return;
    }
    rules
        .computeIfAbsent(table, k -> new LinkedHashMap<>())
        .put(column, new DesensitizationRuleConfig(rule));
  }

  /**
   * 添加自定义脱敏规则。
   *
   * @param table 表名
   * @param column 列名
   * @param rule 脱敏规则（通常为 {@link ColumnDesensitizationRule#CUSTOM}）
   * @param customPattern 自定义正则表达式
   * @param customReplacement 自定义替换模板
   */
  public void addRule(
      String table,
      String column,
      ColumnDesensitizationRule rule,
      String customPattern,
      String customReplacement) {
    if (table == null || column == null) {
      return;
    }
    rules
        .computeIfAbsent(table, k -> new LinkedHashMap<>())
        .put(column, new DesensitizationRuleConfig(rule, customPattern, customReplacement));
  }

  /**
   * 添加规则配置（用于多角色合并场景）。
   *
   * @param table 表名
   * @param column 列名
   * @param config 规则配置
   */
  public void addRule(String table, String column, DesensitizationRuleConfig config) {
    if (table == null || column == null || config == null) {
      return;
    }
    rules.computeIfAbsent(table, k -> new LinkedHashMap<>()).put(column, config);
  }

  /**
   * 查询指定表列的脱敏规则配置。
   *
   * @param table 表名
   * @param column 列名
   * @return 规则配置，不存在时返回 null
   */
  public DesensitizationRuleConfig getRule(String table, String column) {
    Map<String, DesensitizationRuleConfig> tableRules = rules.get(table);
    return tableRules == null ? null : tableRules.get(column);
  }

  /**
   * 判断指定表列是否配置了脱敏规则。
   *
   * @param table 表名
   * @param column 列名
   * @return 已配置返回 true，否则 false
   */
  public boolean hasRule(String table, String column) {
    Map<String, DesensitizationRuleConfig> tableRules = rules.get(table);
    return tableRules != null && tableRules.containsKey(column);
  }

  /**
   * 获取所有已配置脱敏规则的表名。
   *
   * @return 不可修改的表名集合
   */
  public Set<String> getAllTables() {
    return Collections.unmodifiableSet(new LinkedHashSet<>(rules.keySet()));
  }

  /**
   * 获取指定表下所有已配置脱敏规则的列名。
   *
   * @param table 表名
   * @return 不可修改的列名集合，表不存在时返回空集合
   */
  public Set<String> getColumns(String table) {
    Map<String, DesensitizationRuleConfig> tableRules = rules.get(table);
    if (tableRules == null) {
      return Collections.emptySet();
    }
    return Collections.unmodifiableSet(new LinkedHashSet<>(tableRules.keySet()));
  }

  /**
   * 判断上下文是否为空（无任何规则配置）。
   *
   * @return 无规则时返回 true
   */
  public boolean isEmpty() {
    return rules.isEmpty();
  }

  /**
   * 脱敏规则配置。
   *
   * <p>封装内置规则枚举与自定义正则/替换模板，供 {@link ColumnDesensitizationExecutor#desensitize(String,
   * DesensitizationRuleConfig)} 使用。
   */
  public static class DesensitizationRuleConfig {

    private final ColumnDesensitizationRule rule;
    private final String customPattern;
    private final String customReplacement;

    /**
     * 构造内置规则配置。
     *
     * @param rule 内置脱敏规则，null 表示占位（使用默认策略）
     */
    public DesensitizationRuleConfig(ColumnDesensitizationRule rule) {
      this.rule = rule;
      this.customPattern = null;
      this.customReplacement = null;
    }

    /**
     * 构造自定义规则配置。
     *
     * @param rule 脱敏规则（通常为 {@link ColumnDesensitizationRule#CUSTOM}）
     * @param customPattern 自定义正则表达式
     * @param customReplacement 自定义替换模板
     */
    public DesensitizationRuleConfig(
        ColumnDesensitizationRule rule, String customPattern, String customReplacement) {
      this.rule = rule;
      this.customPattern = customPattern;
      this.customReplacement = customReplacement;
    }

    /**
     * 获取内置脱敏规则。
     *
     * @return 内置规则，可能为 null
     */
    public ColumnDesensitizationRule getRule() {
      return rule;
    }

    /**
     * 判断是否为自定义规则。
     *
     * @return 规则为 {@link ColumnDesensitizationRule#CUSTOM} 时返回 true
     */
    public boolean isCustom() {
      return rule == ColumnDesensitizationRule.CUSTOM;
    }

    /**
     * 获取自定义正则表达式。
     *
     * @return 自定义正则，非自定义规则时返回 null
     */
    public String getCustomPattern() {
      return customPattern;
    }

    /**
     * 获取自定义替换模板。
     *
     * @return 自定义替换模板，非自定义规则时返回 null
     */
    public String getCustomReplacement() {
      return customReplacement;
    }
  }
}
