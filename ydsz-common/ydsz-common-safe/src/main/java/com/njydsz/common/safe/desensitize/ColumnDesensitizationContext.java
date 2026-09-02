package com.njydsz.common.safe.desensitize.ColumnDesensitizationContext;

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