package com.njydsz.gateway.config;

import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关限流配置属性。
 *
 * <p>支持 IP 和用户两个维度的令牌桶限流：
 *
 * <ul>
 *   <li>IP 级限流：防止单 IP 暴力请求
 *   <li>用户级限流：按用户 ID 限流
 * </ul>
 *
 * <p>配置示例：
 *
 * <pre>
 * ydsz:
 *   gateway:
 *     ratelimit:
 *       enabled: true
 *       per-user:
 *         enabled: true
 *         default-qps: 50
 *         burst-capacity: 100
 *       per-ip:
 *         enabled: true
 *         default-qps: 30
 *         burst-capacity: 60
 *         whitelist:
 *           - "127.0.0.1"
 *       response-headers:
 *         enabled: true
 *         retry-after: 5
 * </pre>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Data
@ConfigurationProperties(prefix = "ydsz.gateway.ratelimit")
public class RateLimitProperties {

  /** 是否启用限流 */
  private boolean enabled = true;

  /** 用户级限流配置 */
  private PerUserConfig perUser = new PerUserConfig();

  /** IP 级限流配置 */
  private PerIpConfig perIp = new PerIpConfig();

  /** 响应头配置 */
  private ResponseHeadersConfig responseHeaders = new ResponseHeadersConfig();

  /** 用户级限流配置：按用户 ID（X-User-Id 头）维度限流。 */
  @Data
  public static class PerUserConfig {
    /** 是否启用用户级限流（默认 true）。 */
    private boolean enabled = true;

    /** 令牌桶填充速率（每秒请求数，默认 50）。 */
    private int defaultQps = 50;

    /** 令牌桶突发容量（短时最大请求数，默认 100）。 */
    private int burstCapacity = 100;
  }

  /** IP 级限流配置：按客户端真实 IP 维度限流。 */
  @Data
  public static class PerIpConfig {
    /** 是否启用 IP 级限流（默认 true）。 */
    private boolean enabled = true;

    /** 令牌桶填充速率（每秒请求数，默认 30）。 */
    private int defaultQps = 30;

    /** 令牌桶突发容量（短时最大请求数，默认 60）。 */
    private int burstCapacity = 60;

    /** IP 白名单（命中后不限流，支持精确 IP 和 CIDR）。 */
    private List<String> whitelist;
  }

  /** 限流响应头配置：HTTP 429 响应中 {@code X-RateLimit-*} 头。 */
  @Data
  public static class ResponseHeadersConfig {
    /** 是否注入限流响应头（默认 true）。 */
    private boolean enabled = true;

    /** 限流触发时 {@code Retry-After} 头值（秒，默认 5）。 */
    private int retryAfter = 5;
  }
}
