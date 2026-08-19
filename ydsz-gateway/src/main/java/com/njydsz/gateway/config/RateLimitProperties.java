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
 * @since 1.0.0
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

  /** 用户级限流配置 */
  @Data
  public static class PerUserConfig {
    private boolean enabled = true;

    /** 默认每秒请求数 */
    private int defaultQps = 50;

    /** 突发容量（令牌桶） */
    private int burstCapacity = 100;
  }

  /** IP 级限流配置 */
  @Data
  public static class PerIpConfig {
    private boolean enabled = true;

    /** 默认每秒请求数 */
    private int defaultQps = 30;

    /** 突发容量 */
    private int burstCapacity = 60;

    /** IP 白名单（不限流） */
    private List<String> whitelist;
  }

  /** 响应头配置 */
  @Data
  public static class ResponseHeadersConfig {
    private boolean enabled = true;

    /** Retry-After 头值（秒） */
    private int retryAfter = 5;
  }
}
