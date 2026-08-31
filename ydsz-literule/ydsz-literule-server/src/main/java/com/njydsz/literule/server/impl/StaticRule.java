package com.njydsz.literule.server.impl;

import java.util.function.Function;

import com.njydsz.literule.domain.api.Rule;
import com.njydsz.literule.domain.api.RuleContext;
import com.njydsz.literule.domain.api.RuleResult;

/**
 * 静态规则：包装 Java lambda 作为规则
 *
 * <p>用于编程式注册规则，保持与原 AlertRule 编码习惯兼容。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public class StaticRule implements Rule {

  private final String code;
  private final String name;
  private final String category;
  private final int priority;
  private final String scope;
  private final Function<RuleContext, RuleResult> evaluator;

  /**
   * 构造静态规则
   *
   * @param code 规则编码
   * @param name 规则名称
   * @param category 类别
   * @param priority 优先级
   * @param evaluator 评估函数
   */
  public StaticRule(
      String code,
      String name,
      String category,
      int priority,
      Function<RuleContext, RuleResult> evaluator) {
    this(code, name, category, priority, null, evaluator);
  }

  /**
   * 构造静态规则（指定作用域）
   *
   * @param code 规则编码
   * @param name 规则名称
   * @param category 类别
   * @param priority 优先级
   * @param scope 作用域（null 表示全部场景）
   * @param evaluator 评估函数
   * @since 1.0.0
   */
  public StaticRule(
      String code,
      String name,
      String category,
      int priority,
      String scope,
      Function<RuleContext, RuleResult> evaluator) {
    this.code = code;
    this.name = name;
    this.category = category;
    this.priority = priority;
    this.scope = scope;
    this.evaluator = evaluator;
  }

  /**
   * 构造静态规则（默认优先级）
   *
   * @param code 规则编码
   * @param name 规则名称
   * @param category 类别
   * @param evaluator 评估函数
   */
  public StaticRule(
      String code, String name, String category, Function<RuleContext, RuleResult> evaluator) {
    this(code, name, category, DEFAULT_PRIORITY, evaluator);
  }

  @Override
  public String getCode() {
    return code;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getCategory() {
    return category;
  }

  @Override
  public int getPriority() {
    return priority;
  }

  @Override
  public String getScope() {
    return scope;
  }

  @Override
  public RuleResult evaluate(RuleContext context) {
    RuleResult result = evaluator.apply(context);
    if (result == null) {
      return RuleResult.notTriggered(code);
    }
    return result;
  }
}
