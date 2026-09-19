package com.njydsz.common.auth.token;

import java.util.Date;

import lombok.Getter;
import lombok.ToString;

/**
 * Token 自动刷新策略。
 *
 * <p>实现 Sliding Window Refresh 模式：当 Access Token 剩余有效期低于阈值时， 通过 Response Header {@code X-Token-Refresh: true}
 * 通知前端发起刷新，避免活跃用户在请求途中遭遇 401 导致体验裂缝。
 *
 * <p>配置项（由调用方从 ydsz.auth.token 配置中组装传入）：
 *
 * <ul>
 *   <li>enabled：总开关（默认 false，由 web 层显式开启）
 *   <li>thresholdSeconds：刷新阈值（秒）。当剩余有效期 &lt; 此值时触发，默认 300（5 分钟）
 * </ul>
 *
 * <p>典型用法（伪代码）：
 *
 * <pre>{@code
 * // 在 auth filter 的 doPostAuth 中调用
 * TokenRefreshResult result = strategy.checkExpiration(tokenExpiresAt);
 * if (result.isShouldRefresh()) {
 *   response.setHeader("X-Token-Refresh", "true");
 *   response.setHeader("X-Token-Refresh-After", String.valueOf(result.getRefreshAfterSeconds()));
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.18
 */
@Getter
@ToString
public class TokenRefreshStrategy {

  /** 是否启用滑动窗口刷新提示 */
  private final boolean enabled;

  /** 刷新阈值（秒）：剩余有效期低于此值时通知前端刷新 */
  private final int thresholdSeconds;

  /**
   * 构造默认策略（不启用，需显式配置）。
   */
  public TokenRefreshStrategy() {
    this(false, 300);
  }

  /**
   * 构造策略。
   *
   * @param enabled 是否启用
   * @param thresholdSeconds 刷新阈值（秒），必须 &gt; 0
   */
  public TokenRefreshStrategy(boolean enabled, int thresholdSeconds) {
    this.enabled = enabled;
    this.thresholdSeconds = Math.max(0, thresholdSeconds);
  }

  /**
   * 检查是否需要通知前端刷新 Token。
   *
   * @param tokenExpiresAt Token 过期时间（非 null）
   * @return 检查结果（含是否需刷新及建议的刷新倒计时）
   */
  public TokenRefreshResult checkExpiration(Date tokenExpiresAt) {
    if (!enabled || tokenExpiresAt == null) {
      return TokenRefreshResult.noRefresh();
    }
    long remainingSeconds = (tokenExpiresAt.getTime() - System.currentTimeMillis()) / 1000;
    if (remainingSeconds <= 0) {
      // 已过期，filter 会在验签阶段拦截，这里不重复通知
      return TokenRefreshResult.noRefresh();
    }
    if (remainingSeconds < thresholdSeconds) {
      return TokenRefreshResult.shouldRefresh(remainingSeconds);
    }
    return TokenRefreshResult.noRefresh();
  }

  /**
   * 检查结果。
   */
  @Getter
  @ToString
  public static class TokenRefreshResult {

    /** 是否需要通知前端刷新 */
    private final boolean shouldRefresh;

    /** 剩余有效期（秒），用于前端展示倒计时 */
    private final long remainingSeconds;

    /** 建议刷新倒计时秒数 */
    private final long refreshAfterSeconds;

    private TokenRefreshResult(boolean shouldRefresh, long remainingSeconds, long refreshAfterSeconds) {
      this.shouldRefresh = shouldRefresh;
      this.remainingSeconds = remainingSeconds;
      this.refreshAfterSeconds = refreshAfterSeconds;
    }

    /**
     * 无需刷新。
     *
     * @return 结果实例
     */
    public static TokenRefreshResult noRefresh() {
      return new TokenRefreshResult(false, 0, 0);
    }

    /**
     * 需要刷新。
     *
     * @param remainingSeconds 剩余秒数
     * @return 结果实例
     */
    public static TokenRefreshResult shouldRefresh(long remainingSeconds) {
      return new TokenRefreshResult(true, remainingSeconds, Math.max(0, remainingSeconds));
    }
  }
}
