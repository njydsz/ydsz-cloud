package com.njydsz.literule.api;

import java.util.List;


/**
 * 规则引擎接口
 *
 * <p>引擎负责管理规则注册、按优先级编排执行、收集评估结果、记录执行统计。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface RuleEngine {

  /**
   * 注册一条规则
   *
   * @param rule 规则
   */
  void register(Rule rule);

  /**
   * 注销一条规则
   *
   * @param ruleCode 规则编码
   */
  void unregister(String ruleCode);

  /**
   * 评估全部已注册规则，返回触发的结果列表（按严重度倒序）
   *
   * @param context 规则上下文
   * @return 触发的规则结果列表；无触发返回空列表
   */
  List<RuleResult> evaluate(RuleContext context);

  /**
   * 评估并返回最高严重度的结果（用于顶部 banner 摘要）
   *
   * @param context 规则上下文
   * @return 最高严重度结果；无触发返回 null
   */
  RuleResult topResult(RuleContext context);

  /**
   * Dry-run 仿真：评估全部规则，返回全部结果（含未触发），不发布事件、不记录统计
   *
   * @param context 规则上下文
   * @return 全部规则结果列表（含未触发）
   */
  List<RuleResult> dryRun(RuleContext context);

  /**
   * Dry-run 仿真（支持短路返回优化，P2-5）
   *
   * <p>当 {@code limit} 与 {@code minSeverity} 均非空时， 在按优先级遍历过程中，已命中且严重度不低于 {@code minSeverity}
   * 的结果数量达到 {@code limit} 时立即停止评估（短路）。
   *
   * <p>默认实现委托 {@link #dryRun(RuleContext)}（短路参数不生效）， 支持短路优化的实现（如 {@code DefaultRuleEngine}）应覆盖本方法。
   *
   * @param context 规则上下文
   * @param limit 返回结果数量上限（可为 null，表示不限制）
   * @param minSeverity 最低严重度阈值（可为 null，表示不限制）
   * @return 全部规则结果列表（含未触发）
   * @since 1.0.0
   */
  default List<RuleResult> dryRun(
      RuleContext context, Integer limit, RuleSeverity minSeverity) {
    return dryRun(context);
  }

  /**
   * 获取全部已注册规则（只读）
   *
   * @return 不可修改的规则列表
   */
  List<Rule> getRules();

  /**
   * 获取规则执行统计快照
   *
   * @return 统计快照
   */
  RuleEngineStats getStats();
}
