package com.njydsz.common.util.http;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 可信代理配置属性。
 *
 * <p>配置前缀：{@code ydsz.util.trusted-proxies}
 *
 * <p><b>配置示例（application.yml）：</b>
 *
 * <pre>{@code
 * ydsz:
 *   util:
 *     trusted-proxies:
 *       - 10.0.0.1    # 入口网关 IP
 *       - 10.0.0.2
 * }</pre>
 *
 * <p><b>K8s 风险提示：</b>未配置时仅内网/回环地址（RFC 1918 + 127.0.0.0/8 + ::1）被视为可信代理。
 * Kubernetes 集群内所有 Pod IP 均为内网地址，任意 Pod 伪造的 X-Forwarded-For 都会被采信。
 * 集群内部署时应将可信代理收敛为明确的入口网关/LB 出口 IP 集合。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@ConfigurationProperties(prefix = "ydsz.util")
public class TrustedProxyProperties {

  /** 可信代理 IP 集合（精确匹配，非 CIDR）；内网/回环地址始终可信，无需配置 */
  private Set<String> trustedProxies = new LinkedHashSet<>();

  /**
   * 获取可信代理 IP 集合。
   *
   * @return 可信代理 IP 集合（可为空集合）
   */
  public Set<String> getTrustedProxies() {
    return trustedProxies;
  }

  /**
   * 设置可信代理 IP 集合。
   *
   * @param trustedProxies 可信代理 IP 集合；null 视为空集合
   */
  public void setTrustedProxies(Set<String> trustedProxies) {
    this.trustedProxies = trustedProxies == null ? new LinkedHashSet<>() : trustedProxies;
  }
}
