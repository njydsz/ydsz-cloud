package com.njydsz.common.base.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 请求体配置属性。
 *
 * <p>配置前缀：{@code ydsz.base.request}
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@ConfigurationProperties(prefix = "ydsz.base.request")
public class YdszRequestProperties {

  /** 是否启用请求体大小限制。 */
  private boolean enabled = true;

  /**
   * 最大请求体大小（字节），默认 10MB。
   *
   * <p>超过此大小的请求体将被拒绝，直接返回 413 Request Entity Too Large。 业务方可通过 {@code
   * ydsz.base.request.max-body-size} 调整。
   */
  private long maxBodySize = 10 * 1024 * 1024;

  /**
   * 是否配置嵌入式容器的最大 POST 大小。
   *
   * <p>开启后会自动配置 Tomcat/Jetty 的 maxPostSize / maxSwallowSize。
   */
  private boolean configureContainer = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public long getMaxBodySize() {
    return maxBodySize;
  }

  public void setMaxBodySize(long maxBodySize) {
    this.maxBodySize = maxBodySize;
  }

  public boolean isConfigureContainer() {
    return configureContainer;
  }

  public void setConfigureContainer(boolean configureContainer) {
    this.configureContainer = configureContainer;
  }
}
