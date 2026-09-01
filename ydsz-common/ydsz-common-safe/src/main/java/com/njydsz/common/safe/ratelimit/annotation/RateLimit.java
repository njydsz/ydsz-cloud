package com.njydsz.common.safe.ratelimit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.njydsz.common.safe.ratelimit.enums.RateLimitAlgorithm;
import com.njydsz.common.safe.ratelimit.enums.RateLimitDimension;
import com.njydsz.common.safe.ratelimit.enums.RateLimitMode;

/**
 * 限流注解
 *
 * <p>在方法上声明限流规则，触发 AOP 切面执行限流决策。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 全局限流：每秒 100 个
 * @RateLimit(resource = "order.create", threshold = 100)
 *
 * // 用户级限流：每个用户每秒 5 次
 * @RateLimit(resource = "user.login",
 *            threshold = 5,
 *            dimension = RateLimitDimension.USER,
 *            keyParam = 0)
 *
 * // 热点参数限流：按商品 ID 限流
 * @RateLimit(resource = "seckill",
 *            threshold = 10,
 *            dimension = RateLimitDimension.HOT_PARAM,
 *            keyParam = 0)
 *
 * // 集群限流
 * @RateLimit(resource = "payment.create",
 *            threshold = 1000,
 *            mode = RateLimitMode.CLUSTER,
 *            algorithm = RateLimitAlgorithm.SLIDING_WINDOW)
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

  /**
   * 资源名（必填，限流 key 基础）。
   *
   * @return 资源标识
   */
  String resource();

  /**
   * 限流阈值（每秒请求数 / 并发数 / 令牌数）。
   *
   * @return 限流阈值，默认 {@code 100.0}
   */
  double threshold() default 100.0;

  /**
   * 限流窗口大小（默认 1 秒）。
   *
   * @return 窗口大小（毫秒），默认 {@code 1000L}
   */
  long windowMillis() default 1000L;

  /**
   * 桶容量（突发容量，仅令牌桶/漏桶有效）。
   *
   * @return 突发容量，默认 {@code 200L}
   */
  long burstCapacity() default 200L;

  /**
   * 限流算法。
   *
   * @return 限流算法类型，默认 {@code TOKEN_BUCKET}
   */
  RateLimitAlgorithm algorithm() default RateLimitAlgorithm.TOKEN_BUCKET;

  /**
   * 限流维度。
   *
   * @return 限流维度，默认 {@code API}
   */
  RateLimitDimension dimension() default RateLimitDimension.API;

  /**
   * 限流模式。
   *
   * @return 限流模式，默认 {@code LOCAL}
   */
  RateLimitMode mode() default RateLimitMode.LOCAL;

  /**
   * 限流 key 的参数索引（-1 表示不按参数取 key）。
   *
   * @return 参数索引，默认 {@code -1}
   */
  int keyParam() default -1;

  /**
   * 备用 key 参数索引（拼接到 key 中）。
   *
   * @return 备用参数索引，默认 {@code -1}
   */
  int keyParam2() default -1;

  /**
   * 降级方法（bean name # method）。
   *
   * @return 降级方法引用，默认空串
   */
  String fallback() default "";

  /**
   * 限流错误码（默认使用 RateLimitExceptionCode.RATE_LIMIT）。
   *
   * @return 限流错误码，默认空串
   */
  String errorCode() default "";

  /**
   * 错误消息 i18n key。
   *
   * @return 错误消息键，默认 {@code "ratelimit.blocked"}
   */
  String message() default "ratelimit.blocked";

  /**
   * 排队等待超时（毫秒，0=不等待）。
   *
   * @return 排队超时（毫秒），默认 {@code 0L}
   */
  long queueTimeoutMillis() default 0L;

  /**
   * 预热期（毫秒，0=不预热）。
   *
   * @return 预热期（毫秒），默认 {@code 0L}
   */
  long warmupMillis() default 0L;
}
