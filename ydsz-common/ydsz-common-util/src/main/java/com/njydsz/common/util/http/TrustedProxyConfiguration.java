package com.njydsz.common.util.http;

import java.util.Collections;
import java.util.Set;

/**
 * 可信代理 IP 配置
 *
 * <p>管理反向代理（Nginx/SLB）公网出口 IP 集合，用于判断 X-Forwarded-For 等转发头是否可信（防止客户端伪造真实 IP）。
 *
 * <p>内网地址（RFC 1918 + 回环）始终可信，无需显式配置。
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * &#64;Configuration
 * public class ProxyConfig {
 *     &#64;Bean
 *     public TrustedProxyConfiguration trustedProxyConfiguration(
 *             &#64;Value("${ydsz.util.trusted-proxies:}") Set<String> proxies) {
 *         return new TrustedProxyConfiguration(proxies);
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class TrustedProxyConfiguration {

  /** 显式配置的可信代理 IP 集合（精确匹配，非 CIDR） */
  private final Set<String> trustedIps;

  /**
   * 构造器
   *
   * @param trustedIps 可信代理 IP 集合 或空集合）；null 自动转为空集合
   */
  public TrustedProxyConfiguration(Set<String> trustedIps) {
    this.trustedIps =
        (trustedIps == null || trustedIps.isEmpty())
            ? Collections.emptySet()
            : Set.copyOf(trustedIps);
  }

  /**
   * 获取可信代理 IP 集合。
   *
   * @return 不可变集合
   */
  public Set<String> getTrustedIps() {
    return trustedIps;
  }

  /**
   * 判断指定 IP 是否在可信代理配置中。
   *
   * @param ip 待判断的 IP 地址
   * @return true 表示可信
   */
  public boolean isTrusted(String ip) {
    return trustedIps.contains(ip);
  }
}
