package com.njydsz.common.safe.ratelimit.enums;

/**
 * 限流算法枚举
 *
 * <p>YDSZ 限流模块支持的算法类型：
 *
 * <ul>
 *   <li>{@link #TOKEN_BUCKET} - 令牌桶（支持突发流量，适合 API 限流）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum RateLimitAlgorithm {

  /**
   * 令牌桶（推荐）
   *
   * <p>以恒定速率往桶里放令牌，请求需取令牌。允许突发流量（桶满时）。 本地限流首选算法，兼顾突发流量支持和实现简洁性。
   */
  TOKEN_BUCKET("token-bucket", "令牌桶"),

  /**
   * 并发数限制（基于信号量/线程数控制同时访问资源的请求量）
   *
   * <p>适用于保护下游服务不被过多并发请求压垮，配合 release() 使用。
   */
  CONCURRENCY("concurrency", "并发数");

  private final String code;
  private final String description;

  RateLimitAlgorithm(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public String getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }

  /**
   * 根据编码解析对应的限流算法。
   *
   * <p>编码匹配不区分大小写；编码为 {@code null} 或无法匹配时返回 {@link #TOKEN_BUCKET} 作为默认值，不抛出异常，保证非法配置下限流仍可用。
   *
   * @param code 算法编码（如 {@code "token-bucket"}），允许为 {@code null}
   * @return 匹配到的限流算法；无法匹配时返回 {@link #TOKEN_BUCKET}
   */
  public static RateLimitAlgorithm fromCode(String code) {
    if (code == null) {
      return TOKEN_BUCKET;
    }
    for (RateLimitAlgorithm alg : values()) {
      if (alg.code.equalsIgnoreCase(code)) {
        return alg;
      }
    }
    return TOKEN_BUCKET;
  }
}
