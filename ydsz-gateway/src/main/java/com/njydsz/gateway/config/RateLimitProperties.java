package com.njydsz.gateway.config;

import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

/**
 * 网关限流配置属性。
 *
 * <p>支持 IP 和用户两个维度的令牌桶限流：
 *
 * <ul>
 *   <li>IP 级限流：防止单 IP 暴力请求
 *   <li>用户级限流：按用户 ID 限流
 *   <li>租户级限流：多租户 SaaS 场景，按租户 ID 限流
 *   <li>应用级限流：按接入应用（App-ID）限流
 *   <li>接口级限流：按 API 路径限流（防热点接口被打爆）
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
@RefreshScope
@ConfigurationProperties(prefix = "ydsz.gateway.ratelimit")
public class RateLimitProperties {

  /** 是否启用限流 */
  private boolean enabled = true;

  /** 用户级限流配置 */
  private PerUserConfig perUser = new PerUserConfig();

  /** IP 级限流配置 */
  private PerIpConfig perIp = new PerIpConfig();

  /** 租户级限流配置（多租户 SaaS） */
  private PerTenantConfig perTenant = new PerTenantConfig();

  /** 应用级限流配置（按 App-ID） */
  private PerAppConfig perApp = new PerAppConfig();

  /** 接口级限流配置（按 API 路径） */
  private PerApiConfig perApi = new PerApiConfig();

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

  /** 租户级限流配置（多租户 SaaS 场景） */
  @Data
  public static class PerTenantConfig {
    private boolean enabled = false;

    /** 默认每秒请求数 */
    private int defaultQps = 100;

    /** 突发容量 */
    private int burstCapacity = 200;
  }

  /** 应用级限流配置（按 App-ID） */
  @Data
  public static class PerAppConfig {
    private boolean enabled = false;

    /** 默认每秒请求数 */
    private int defaultQps = 200;

    /** 突发容量 */
    private int burstCapacity = 400;
  }

  /** 接口级限流配置（按 API 路径） */
  @Data
  public static class PerApiConfig {
    private boolean enabled = false;

    /** 默认每秒请求数 */
    private int defaultQps = 500;

    /** 突发容量 */
    private int burstCapacity = 1000;
  }

  /** 响应头配置 */
  @Data
  public static class ResponseHeadersConfig {
    private boolean enabled = true;

    /** Retry-After 头值（秒） */
    private int retryAfter = 5;
  }
}
