package com.njydsz.common.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Webhook 配置属性。
 *
 * <p>配置前缀：{@code ydsz.webhook}
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@ConfigurationProperties(prefix = "ydsz.webhook")
public class WebhookProperties {

  /** 是否启用 Webhook 功能。 */
  private boolean enabled = true;

  /** 连接超时时间（毫秒）。 */
  private int connectTimeoutMs = 5000;

  /** 读取超时时间（毫秒）。 */
  private int readTimeoutMs = 10000;

  /** 最大连接数（连接池）。 */
  private int maxConnections = 50;

  /** 每个路由的最大连接数。 */
  private int maxConnectionsPerRoute = 20;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getConnectTimeoutMs() {
    return connectTimeoutMs;
  }

  public void setConnectTimeoutMs(int connectTimeoutMs) {
    this.connectTimeoutMs = connectTimeoutMs;
  }

  public int getReadTimeoutMs() {
    return readTimeoutMs;
  }

  public void setReadTimeoutMs(int readTimeoutMs) {
    this.readTimeoutMs = readTimeoutMs;
  }

  public int getMaxConnections() {
    return maxConnections;
  }

  public void setMaxConnections(int maxConnections) {
    this.maxConnections = maxConnections;
  }

  public int getMaxConnectionsPerRoute() {
    return maxConnectionsPerRoute;
  }

  public void setMaxConnectionsPerRoute(int maxConnectionsPerRoute) {
    this.maxConnectionsPerRoute = maxConnectionsPerRoute;
  }
}
