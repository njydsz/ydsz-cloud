package com.njydsz.message.server.service.retry;

import com.njydsz.message.server.config.MessageProperties;

/**
 * 重试预设档位枚举（P3-2: 重试策略简化为预设档位）。
 *
 * <p>将复杂的指数退避参数简化为业务人员可理解的预设档位，降低配置门槛。 每个预设对应一组重试参数（重试次数 + 退避策略），并通过 {@link #toRetryPolicy()} 转换为标准 {@link
 * MessageProperties.RetryPolicy} 供重试框架使用。
 *
 * <p><b>预设档位：</b>
 *
 * <ul>
 *   <li>{@link #NONE} — 不重试，失败立即转死信/失败（适用于幂等性弱或不可重试的场景）
 *   <li>{@link #FAST} — 快速重试（最多 2 次，间隔 1s → 3s），适用于实时性要求高的场景
 *   <li>{@link #STANDARD} — 标准重试（最多 3 次，间隔 2s → 4s → 8s），默认推荐档位
 *   <li>{@link #RELAXED} — 宽松重试（最多 5 次，间隔 5s → 15s → 45s → 60s → 60s），适用于容忍时延的场景
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public enum RetryPreset {

  /**
   * 不重试。
   *
   * <p>发送失败后立即标记 FAILED / 转死信，不做任何重试。 适用于：幂等性弱的操作（如扣款通知）、明确不可恢复的错误（如参数无效）。
   */
  NONE("none", "不重试", 0, 0, 0.0, 0L),

  /**
   * 快速重试。
   *
   * <p>最多重试 2 次，退避间隔短（1s → 3s），总耗时约 4s。 适用于：即时通讯、实时通知等对延迟敏感的场景。
   */
  FAST("fast", "快速重试", 2, 1000L, 3.0, 5000L),

  /**
   * 标准重试（默认推荐）。
   *
   * <p>最多重试 3 次，退避间隔适中（2s → 4s → 8s），总耗时约 14s。 与原来的默认行为（maxRetryCount=3, baseBackoffMs=2000）保持一致。
   */
  STANDARD("standard", "标准重试", 3, 2000L, 2.0, 60000L),

  /**
   * 宽松重试。
   *
   * <p>最多重试 5 次，退避间隔长（5s → 15s → 45s → 60s → 60s），总耗时约 185s（约 3 分钟）。 适用于：容忍时延、希望尽可能送达的场景（如营销通知、日报推送）。
   */
  RELAXED("relaxed", "宽松重试", 5, 5000L, 3.0, 60000L);

  /** 预设标识（用于 API 参数和配置项） */
  private final String code;

  /** 预设显示名（中文） */
  private final String displayName;

  /** 最大重试次数 */
  private final int maxRetryCount;

  /** 基础退避（毫秒） */
  private final long baseBackoffMs;

  /** 退避倍率 */
  private final double backoffMultiplier;

  /** 退避上限（毫秒） */
  private final long maxBackoffMs;

  RetryPreset(
      String code,
      String displayName,
      int maxRetryCount,
      long baseBackoffMs,
      double backoffMultiplier,
      long maxBackoffMs) {
    this.code = code;
    this.displayName = displayName;
    this.maxRetryCount = maxRetryCount;
    this.baseBackoffMs = baseBackoffMs;
    this.backoffMultiplier = backoffMultiplier;
    this.maxBackoffMs = maxBackoffMs;
  }

  public String getCode() {
    return code;
  }

  public String getDisplayName() {
    return displayName;
  }

  public int getMaxRetryCount() {
    return maxRetryCount;
  }

  public long getBaseBackoffMs() {
    return baseBackoffMs;
  }

  public double getBackoffMultiplier() {
    return backoffMultiplier;
  }

  public long getMaxBackoffMs() {
    return maxBackoffMs;
  }

  /**
   * 将预设转换为标准 {@link MessageProperties.RetryPolicy}。
   *
   * @return 对应的 RetryPolicy 配置对象
   */
  public MessageProperties.RetryPolicy toRetryPolicy() {
    MessageProperties.RetryPolicy policy = new MessageProperties.RetryPolicy();
    policy.setMaxRetryCount(maxRetryCount);
    policy.setBaseBackoffMs(baseBackoffMs);
    policy.setBackoffMultiplier(backoffMultiplier);
    policy.setMaxBackoffMs(maxBackoffMs);
    return policy;
  }

  /**
   * 根据预设标识查找预设。
   *
   * @param code 预设标识（如 "fast", "standard"）
   * @return 匹配的预设，未找到时返回 {@link #STANDARD}
   */
  public static RetryPreset fromCode(String code) {
    if (code == null || code.isEmpty()) {
      return STANDARD;
    }
    for (RetryPreset preset : values()) {
      if (preset.code.equalsIgnoreCase(code)) {
        return preset;
      }
    }
    return STANDARD;
  }
}
