package com.njydsz.common.sentry.alert;

/**
 * 告警级别枚举。
 *
 * <p>与 {@link com.njydsz.common.sentry.domain.AlertSeverity} 的区别：本枚举用于 {@link AlertRuleEngine} 产出结果的轻量级分类，
 * 仅区分 INFO / WARN / CRITICAL 三级（无 P0-P3 路由概念），便于 {@code shouldFire()} 做抑制判断。
 *
 * <p>映射关系：
 * <ul>
 *   <li>{@link com.njydsz.common.sentry.domain.AlertSeverity#P0} / {@link com.njydsz.common.sentry.domain.AlertSeverity#P1} → {@link #CRITICAL}</li>
 *   <li>{@link com.njydsz.common.sentry.domain.AlertSeverity#P2} → {@link #WARN}</li>
 *   <li>{@link com.njydsz.common.sentry.domain.AlertSeverity#P3} → {@link #INFO}</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.14
 */
public enum AlertLevel {

  /**
   * 信息级：趋势类告警，仅记录不通知。
   *
   * <p>典型场景：搜索 QPS 微幅波动、缓存命中率轻度下降等无需立即介入的业务信号。
   */
  INFO,

  /**
   * 警告级：指标异常但服务可用，建议排查。
   *
   * <p>典型场景：错误率上升但未超过硬阈值、响应时间趋势增长、资源水位达预警线等。
   */
  WARN,

  /**
   * 严重级：服务降级或核心指标严重偏离，需立即介入。
   *
   * <p>典型场景：LLM 调用失败率激增、Agent 执行超时大面积触发、搜索引擎不可用等。
   */
  CRITICAL
}
