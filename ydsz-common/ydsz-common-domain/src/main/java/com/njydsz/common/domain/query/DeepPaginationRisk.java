package com.njydsz.common.domain.query;

/**
 * 深度分页风险评估结果。
 *
 * <p>由 {@link PageQuery#assessPaginationRisk()} 返回，标识当前分页查询是否存在深度分页风险。 业务方可据此决定是否允许查询、发出告警或强制拒绝。
 *
 * <p>对应阈值来自 {@link com.njydsz.common.domain.config.DomainProperties} （{@code
 * ydsz.domain.page.cursor-warning-threshold} / {@code cursor-reject-threshold}）， 默认值与本枚举的 {@link
 * #DEFAULT_WARN_THRESHOLD} / {@link #DEFAULT_REJECT_THRESHOLD} 保持一致， 便于在脱离 Spring
 * 上下文的场景（如单元测试、纯计算）下直接使用。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see PageQuery#assessPaginationRisk()
 * @see DeepPaginationException
 */
public enum DeepPaginationRisk {

  /** 安全：offset 在安全范围内，可正常使用 offset 分页。 */
  SAFE,

  /**
   * 警告：offset 超过警告阈值（默认 10000），建议改用游标分页。
   *
   * <p>调用方应记录 WARN 日志，提示开发者关注性能风险。
   */
  WARN,

  /**
   * 拒绝：offset 超过拒绝阈值（默认 50000），将抛出 {@link DeepPaginationException}。
   *
   * <p>强制调用方改用游标分页（SliceQuery / SliceResult 游标模式），防止慢查询拖垮数据库。
   */
  REJECT;

  /**
   * 默认警告阈值：offset 超过此值触发 WARN。
   *
   * <p>对齐阿里巴巴 Java 开发手册（嵩山版）深度分页治理建议：超过 10w 条记录的表，禁止 offset > 10000。
   */
  public static final long DEFAULT_WARN_THRESHOLD = 10000L;

  /** 默认拒绝阈值：offset 超过此值触发 REJECT（抛出 {@link DeepPaginationException}）。 */
  public static final long DEFAULT_REJECT_THRESHOLD = 50000L;

  /**
   * 评估深度分页风险（使用默认阈值）。
   *
   * @param offset 当前查询的 offset 值
   * @return 风险等级（SAFE / WARN / REJECT）
   */
  public static DeepPaginationRisk assess(long offset) {
    return assess(offset, DEFAULT_WARN_THRESHOLD, DEFAULT_REJECT_THRESHOLD);
  }

  /**
   * 评估深度分页风险（使用指定阈值）。
   *
   * <p>阈值契约：{@code rejectThreshold >= warnThreshold >= 0}。 当 {@code offset >= rejectThreshold} 返回
   * {@link #REJECT}， 当 {@code offset >= warnThreshold} 返回 {@link #WARN}，否则返回 {@link #SAFE}。
   *
   * <p>非法阈值（负数、或拒绝阈值小于警告阈值）属于配置错误，直接抛出 {@link IllegalArgumentException} 快速失败，避免静默产生错误的分页策略。
   *
   * @param offset 当前查询的 offset 值
   * @param warnThreshold 警告阈值（非负）
   * @param rejectThreshold 拒绝阈值（非负，且不小于 warnThreshold）
   * @return 风险等级
   * @throws IllegalArgumentException 当 warnThreshold / rejectThreshold 为负数， 或 rejectThreshold &lt;
   *     warnThreshold 时
   */
  public static DeepPaginationRisk assess(long offset, long warnThreshold, long rejectThreshold) {
    if (warnThreshold < 0L || rejectThreshold < 0L) {
      throw new IllegalArgumentException(
          "Thresholds must not be negative: warnThreshold="
              + warnThreshold
              + ", rejectThreshold="
              + rejectThreshold);
    }
    if (rejectThreshold < warnThreshold) {
      throw new IllegalArgumentException(
          "rejectThreshold must be >= warnThreshold: warnThreshold="
              + warnThreshold
              + ", rejectThreshold="
              + rejectThreshold);
    }
    if (offset >= rejectThreshold) {
      return REJECT;
    }
    if (offset >= warnThreshold) {
      return WARN;
    }
    return SAFE;
  }
}
