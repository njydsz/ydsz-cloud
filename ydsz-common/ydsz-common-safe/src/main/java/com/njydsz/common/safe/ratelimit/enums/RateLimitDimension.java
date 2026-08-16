package com.njydsz.common.safe.ratelimit.enums;

/**
 * 限流维度枚举
 *
 * <p>标识限流统计的维度，决定限流 key 的生成方式。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum RateLimitDimension {

  /** 接口粒度（按资源名） */
  API("api", "接口粒度"),

  /** 全局 */
  GLOBAL("global", "全局"),

  /** IP 维度 */
  IP("ip", "IP 维度"),

  /** 用户维度 */
  USER("user", "用户维度"),

  /** 租户维度 */
  TENANT("tenant", "租户维度"),

  /** 设备维度 */
  DEVICE("device", "设备维度"),

  /** 热点参数维度（按方法参数值） */
  HOT_PARAM("hot-param", "热点参数维度"),

  /** 热点用户维度 */
  HOT_USER("hot-user", "热点用户维度"),

  /** 热点商品维度 */
  HOT_GOODS("hot-goods", "热点商品维度"),

  /** 集群维度（分布式全局） */
  CLUSTER("cluster", "集群维度"),

  /** 自适应维度（基于系统负载） */
  ADAPTIVE("adaptive", "自适应维度");

  private final String code;
  private final String description;

  RateLimitDimension(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public String getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }
}
