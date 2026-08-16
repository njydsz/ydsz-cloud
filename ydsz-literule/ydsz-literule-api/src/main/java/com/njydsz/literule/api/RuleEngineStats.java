package com.njydsz.literule.api;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则引擎执行统计快照
 *
 * <p>记录每条规则的执行次数、触发次数、异常次数、平均耗时，用于规则效能监控。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleEngineStats implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 总评估次数 */
  private long totalEvaluations;

  /** 总触发次数 */
  private long totalTriggered;

  /** 总异常次数 */
  private long totalErrors;

  /** 总评估耗时（毫秒） */
  private long totalElapsedMs;

  /** 当前注册规则数（规则规模监控，用于评估 RETE 引入必要性） */
  private int registeredRules;

  /** 最近一次评估遍历的规则数 */
  private int lastEvaluatedRules;

  /** 按规则编码的统计明细 */
  private Map<String, RuleStat> perRuleStats;

  /**
   * 单条规则统计
   *
   * @author ydsz-team
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RuleStat implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 执行次数 */
    private long executions;

    /** 触发次数 */
    private long triggered;

    /** 异常次数 */
    private long errors;

    /** 总耗时（毫秒） */
    private long totalElapsedMs;
  }

  /**
   * 创建空统计快照
   *
   * @return 空快照
   */
  public static RuleEngineStats empty() {
    return RuleEngineStats.builder().perRuleStats(new ConcurrentHashMap<>()).build();
  }
}
