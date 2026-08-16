package com.njydsz.literule.api.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * 规则引擎监控大盘 - 概览指标 VO
 *
 * <p>用于大盘首屏指标卡片展示，包含规则数量、触发率、耗时分布、错误率等核心指标。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Builder
public class RuleDashboardOverviewVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 规则总数 */
  private long totalRules;

  /** 启用规则数 */
  private long enabledRules;

  /** 按状态分组的规则数：DRAFT/REVIEW/PUBLISHED/DISABLED/ARCHIVED → 数量 */
  private Map<String, Long> statusDistribution;

  /** 按类别分组的规则数：category → 数量 */
  private Map<String, Long> categoryDistribution;

  /** 今日评估次数 */
  private long todayEvaluations;

  /** 今日触发次数 */
  private long todayTriggered;

  /** 今日触发率（0~1） */
  private double todayTriggerRate;

  /** 今日错误次数 */
  private long todayErrors;

  /** 今日错误率（0~1） */
  private double todayErrorRate;

  /** 今日活跃规则数（有触发的规则） */
  private long todayActiveRules;

  /** P50 耗时（毫秒） */
  private double p50ElapsedMs;

  /** P95 耗时（毫秒） */
  private double p95ElapsedMs;

  /** P99 耗时（毫秒） */
  private double p99ElapsedMs;

  /** 平均耗时（毫秒） */
  private double avgElapsedMs;

  /** 统计时间窗口起始时间（含） */
  private String since;

  /** 统计时间窗口结束时间（不含） */
  private String until;
}
