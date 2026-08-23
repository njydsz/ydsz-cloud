package com.njydsz.common.safe.ratelimit.spring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.common.safe.ratelimit.core.RateLimitManager;
import com.njydsz.common.safe.ratelimit.model.RateLimitContext;
import com.njydsz.common.safe.ratelimit.model.RateLimitDecision;

/**
 * 消息发送限流服务。
 *
 * <p>限制单租户/单用户/单模板的发送频次。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitService {

  private final RateLimitManager rateLimitManager;




























  /**
   * 检查限流（不带上下文）。
   *
   * @param resource 资源标识
   * @return 限流决策
   */
  public RateLimitDecision check(String resource) {
    return check(RateLimitContext.builder().resource(resource).build());
  }

  /**
   * 检查限流（带上下文）。
   *
   * @param context 限流上下文
   * @return 限流决策
   */
  public RateLimitDecision check(RateLimitContext context) {
    return rateLimitManager.decide(context);
  }

  /**
   * 检查限流（带注解 + 参数）。
   *
   * @param annotation 限流注解
   * @param args 方法参数（用于 keyParam 提取）
   * @return 限流决策
   */
  public RateLimitDecision check(RateLimit annotation, Object... args) {
    StringBuilder keyBuilder = new StringBuilder(annotation.resource());
    if (annotation.keyParam() >= 0
        && annotation.keyParam() < args.length
        && args[annotation.keyParam()] != null) {
      keyBuilder.append(":").append(args[annotation.keyParam()]);
    }
    RateLimitContext context =
        RateLimitContext.builder().resource(keyBuilder.toString()).args(args).build();
    return check(context);
  }

  /**
   * 快捷方法：判断是否通过。
   *
   * @param resource 资源标识
   * @return {@code true} 表示未触发限流
   */
  public boolean isPass(String resource) {
    return check(resource).isPass();
  }
}
