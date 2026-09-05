package com.njydsz.literule.server.core;

import com.njydsz.literule.domain.Rule;
import com.njydsz.literule.domain.vo.RuleContextVO;
import com.njydsz.literule.domain.vo.RuleResultVO;

/**
 * 单规则评估函数式接口（P2-3 并行评估回调）。
 *
 * <p>封装单条规则的评估逻辑，供 {@link ParallelRuleEvaluator} 并行调度使用。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@FunctionalInterface
public interface RuleEvaluator {

  /**
   * 评估单条规则
   *
   * @param rule    待评估规则
   * @param context 规则上下文（事实数据）  规则评估结果
   * @return 评估结果
   */
  RuleResultVO evaluate(Rule rule, RuleContextVO context);
}
