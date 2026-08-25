package com.njydsz.workflow.domain.enums;

/**
 * 会签类型枚举
 *
 * <p>定义会签节点的完成策略，决定多个审批人之间的投票聚合规则。
 *
 * <p><b>类型说明：</b>
 *
 * <ul>
 *   <li>{@link #OR} — 或签：任一办理人通过即推进（一人通过即生效）
 *   <li>{@link #PARALLEL} — 并行会签：所有办理人全部通过才推进（全员同意）
 *   <li>{@link #WEIGHTED} — 票签（加权投票）：按权重投票，通过权重比例超过阈值才推进
 * </ul>
 *
 * <p><b>票签场景示例：</b>
 *
 * <p>董事会 5 人，权重分别为 30/20/20/15/15（总和 100），通过率阈值 50%，
 * 则累计通过权重 ≥ 50 票时推进。
 *
 * <p><b>状态流转：</b>
 *
 * <ul>
 *   <li>OR → 一人通过即满足推进条件
 *   <li>PARALLEL → approveFinished ≥ approveCount 时满足推进条件
 *   <li>WEIGHTED → 累计通过权重 / 总权重 ≥ votePassRate 时满足推进条件
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.workflow.server.service.impl.CountersignStrategy 会签策略接口
 * @see com.njydsz.workflow.server.service.impl.strategy.WeightedCountersignStrategy 票签策略实现
 */
public enum FlowPerformType {

  /** 或签：任一办理人通过即推进 */
  OR,
  /** 并行会签：所有办理人全部通过才推进 */
  PARALLEL,
  /**
   * 票签（加权投票）：按权重投票，累计通过权重 / 总权重 ≥ votePassRate 时推进。
   *
   * <p>节点 ext JSON 中配置 {@code userWeights}（{@code userId -> weight} 映射），
   * 未配置权重的办理人默认为 1。{@code votePassRate} 默认 0.5（过50%）。
   */
  WEIGHTED
}
