package com.njydsz.literule.domain.vo;

/**
 * 统计记录器
 *
 * <p>将规则执行统计从引擎内部解耦，使编排层（{@code RuleChain}） 也能将执行结果统一记录到引擎统计中，消除编排层与引擎层统计割裂问题。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@FunctionalInterface
public interface StatsRecorder {

  /**
   * 记录一次规则评估
   *
   * @param ruleCode 规则编码
   * @param triggered 是否触发
   * @param error 是否异常
   * @param elapsedMs 耗时（毫秒）
   */
  void record(String ruleCode, boolean triggered, boolean error, long elapsedMs);
}
