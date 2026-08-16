package com.njydsz.gateway.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import lombok.Data;

/**
 * P3-7: 网关请求幂等性配置属性
 *
 * <p>支持基于 {@code Idempotency-Key} 请求头的幂等性保证， 防止业务重复提交（如重复下单、重复支付）。
 *
 * <p>配置示例（Nacos 推送或 application.yml）：
 *
 * <pre>
 * ydsz:
 *   gateway:
 *     idempotency:
 *       enabled: true
 *       # 幂等 key TTL（秒），默认 24 小时
 *       key-ttl-seconds: 86400
 *       # 缓存响应体最大长度（字节），默认 16KB
 *       max-response-size: 16384
 *       # 需要幂等性保护的 HTTP 方法
 *       methods:
 *         - POST
 *         - PUT
 *         - PATCH
 *       # 幂等性保护的路径前缀
 *       path-prefixes:
 *         - /api/order
 *         - /api/payment
 *         - /api/refund
 *       # 响应头
 *       response-headers:
 *         enabled: true
 * </pre>
 *
 * @since 3.7.0
 * @author ydsz-team
 */
@Data
@RefreshScope
@ConfigurationProperties(prefix = "ydsz.gateway.idempotency")
public class IdempotencyProperties {

  /** 是否启用幂等性检查 */
  private boolean enabled = true;

  /** 幂等 key TTL（秒），默认 24 小时 */
  private long keyTtlSeconds = 86_400;

  /** 缓存响应体最大长度（字节），默认 16KB */
  private int maxResponseSize = 16_384;

  /** 需要幂等性保护的 HTTP 方法 */
  private List<String> methods = List.of("POST", "PUT", "PATCH");

  /** 幂等性保护的路径前缀（空列表表示全部路径） */
  private List<String> pathPrefixes = List.of();

  /** 响应头配置 */
  private ResponseHeadersConfig responseHeaders = new ResponseHeadersConfig();

  /** 响应头配置 */
  @Data
  public static class ResponseHeadersConfig {
    /** 是否添加幂等性相关响应头 */
    private boolean enabled = true;
  }
}
