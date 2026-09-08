package com.njydsz.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关 Resilience4j 限流配置属性（{@code ydsz.gateway.rate-limiter.*}）。
 *
 * <p>控制 Spring Cloud Gateway 内置 {@code RequestRateLimiter} 过滤器的行为（即 {@code
 * application-resilience4j.yml} 中配置的 RedisRateLimiter 全局限流）。
 *
 * <p>与 {@link RateLimitFilter}（基于 Redis Lua 脚本的 IP + 用户二维度令牌桶）互补：
 *
 * <ul>
 *   <li>{@code RateLimitFilter}：细粒度二维限流（{@code ydsz.gateway.ratelimit.*}）
 *   <li>本配置：粗粒度全局统一限流（{@code ydsz.gateway.rate-limiter.*}），作为全局兜底
 * </ul>
 *
 * <p>配置示例：
 *
 * <pre>
 * ydsz:
 *   gateway:
 *     rate-limiter:
 *       enabled: true
 *       replenish-rate: 100
 *       burst-capacity: 200
 *       key-type: USER
 * </pre>
 *
 * @since 26.09.01
 * @author ydsz-team
 * @see RateLimitProperties
 * @see GatewayRateLimiterConfig
 */
@Data
@ConfigurationProperties(prefix = "ydsz.gateway.rate-limiter")
public class GatewayRateLimiterProperties {

  /** 是否启用 Resilience4j 全局兜底限流，默认 false。 */
  private boolean enabled = false;

  /** 令牌桶填充速率（每秒生成的令牌数），默认 100。 */
  private int replenishRate = 100;

  /** 令牌桶突发容量（桶内最大令牌数），默认 200。 */
  private int burstCapacity = 200;

  /** 限流 Key 解析策略，默认 USER（按用户标识）。可选：USER、IP、URL。 */
  private KeyType keyType = KeyType.USER;

  /** 限流 Key 维度枚举。 */
  public enum KeyType {
    /** 按 SecurityContext 中的用户标识限流 */
    USER,
    /** 按客户端 IP 地址限流 */
    IP,
    /** 按请求 URL 路径限流 */
    URL
  }
}
