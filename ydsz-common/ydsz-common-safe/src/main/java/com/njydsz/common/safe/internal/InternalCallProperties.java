package com.njydsz.common.safe.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 内部接口调用校验配置（公共版，P0-2 下沉）。
 *
 * <p>前缀：{@code ydsz.internal-call}
 *
 * <h3>配置项</h3>
 *
 * <ul>
 *   <li>{@code enabled}：是否启用内部调用校验，默认 {@code false}（渐进式启用，
 *       待各业务模块统一注入请求头/签名后逐模块开启）</li>
 *   <li>{@code header-name}：内部调用标记请求头名称，默认 {@code X-Internal-Call}</li>
 *   <li>{@code header-value}：内部调用标记请求头期望值，默认 {@code true}</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.11.06
 */
@ConfigurationProperties(prefix = "ydsz.internal-call")
public class InternalCallProperties {

  /** 是否启用内部调用校验（默认关闭，兼容存量 Feign 客户端） */
  private boolean isEnabled = false;

  /** 内部调用标记请求头名称 */
  private String headerName = "X-Internal-Call";

  /** 内部调用标记请求头期望值 */
  private String headerValue = "true";

  public boolean isEnabled() {
    return isEnabled;
  }

  public void setEnabled(boolean enabled) {
    isEnabled = enabled;
  }

  public String getHeaderName() {
    return headerName;
  }

  public void setHeaderName(String headerName) {
    this.headerName = headerName;
  }

  public String getHeaderValue() {
    return headerValue;
  }

  public void setHeaderValue(String headerValue) {
    this.headerValue = headerValue;
  }
}
